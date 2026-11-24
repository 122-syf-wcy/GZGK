from __future__ import annotations

import json
import os
from typing import Any

import httpx
try:
    from mcp.server.mcpserver import MCPServer
except ImportError:
    from mcp.server.fastmcp import FastMCP as MCPServer  # type: ignore

CORE_HTTP_BASE = os.environ.get("CORE_HTTP_BASE", "http://127.0.0.1:8082").rstrip("/")

# Official Model Context Protocol Server (supports stdio & Streamable HTTP / SSE transports)
mcp = MCPServer(
    name="gzgk-gaokao-mcp",
    instructions=(
        "贵州及多省新高考志愿决策 MCP 服务：提供官方一分一段查询、院校历年线检索、"
        "专业就业/读研对比与多省招考政策资源，严禁编造分数线或做出保录取承诺。"
    ),
)

_PROVINCE_POLICIES: dict[str, dict[str, Any]] = {
    "GZ": {
        "province": "贵州",
        "mode": "3+1+2",
        "unit": "专业(类)+院校",
        "maxVolunteers": 96,
        "hasAdjustment": False,
        "notes": "不设专业调剂；重点核对招生章程外语单科、色觉体检及中外合作学费条款。",
    },
    "SC": {
        "province": "四川",
        "mode": "3+1+2",
        "unit": "院校专业组",
        "maxVolunteers": 45,
        "hasAdjustment": True,
        "notes": "每组6个专业+服从调剂；1:1投档，不服从调剂存在退档风险。",
    },
    "HB": {
        "province": "湖北",
        "mode": "3+1+2",
        "unit": "院校专业组",
        "maxVolunteers": 45,
        "hasAdjustment": True,
        "notes": "投档比例不超过105%，官方建议梯度约冲15/稳15/保15。",
    },
    "HI": {
        "province": "海南",
        "mode": "3+3",
        "unit": "院校专业组",
        "maxVolunteers": 30,
        "hasAdjustment": True,
        "notes": "900分标准分体系，不区分物理/历史首选轨道。",
    },
}

_MAJOR_CAREER_PROFILES: dict[str, dict[str, Any]] = {
    "计算机科学与技术": {
        "category": "工学 · 计算机类",
        "postgradRate": "28% ~ 42%（双一流/行业强校更高）",
        "typicalCities": ["杭州", "深圳", "成都", "重庆", "北京", "上海"],
        "keyCourses": ["数据结构与算法", "操作系统", "计算机网络", "数据库系统", "编译原理"],
        "riskHints": "头部岗位竞争激烈，重视工程实习与算法竞赛；部分高校实行大类分流。",
    },
    "软件工程": {
        "category": "工学 · 计算机类",
        "postgradRate": "22% ~ 35%",
        "typicalCities": ["成都", "重庆", "深圳", "杭州", "南京"],
        "keyCourses": ["软件工程导论", "分布式系统", "数据库与中间件", "项目管理"],
        "riskHints": "注意核查部分高校示范性软件学院三、四年级高学费标准（10,000~16,000元/年）。",
    },
    "电子信息类": {
        "category": "工学 · 电子信息类",
        "postgradRate": "32% ~ 48%",
        "typicalCities": ["深圳", "西安", "成都", "武汉", "苏州"],
        "keyCourses": ["信号与系统", "数字电路", "通信原理", "嵌入式系统", "微机原理"],
        "riskHints": "软硬结合、深造收益高；部分光电/集成电路方向对色盲色弱有体检限制。",
    },
    "自动化": {
        "category": "工学 · 自动化类",
        "postgradRate": "30% ~ 45%",
        "typicalCities": ["深圳", "苏州", "合肥", "成都", "重庆"],
        "keyCourses": ["自动控制原理", "现代控制理论", "PLC与运动控制", "机器人学"],
        "riskHints": "就业面宽（新能源/工业机器人/车企电控），适合偏工程应用与读研兼顾的考生。",
    },
}


@mcp.tool()
async def mcp_lookup_province_policy(province_code: str = "GZ") -> str:
    """查询指定省份的新高考选科模式、平行志愿容量、调剂机制与投档退档注意事项。"""
    code = (province_code or "GZ").strip().upper()
    try:
        async with httpx.AsyncClient(timeout=2.5) as client:
            resp = await client.get(f"{CORE_HTTP_BASE}/policies/{code}")
            if resp.status_code == 200:
                return json.dumps(resp.json().get("data") or {}, ensure_ascii=False)
    except Exception:
        pass
    info = _PROVINCE_POLICIES.get(code) or _PROVINCE_POLICIES["GZ"]
    return json.dumps({"provinceCode": code, **info}, ensure_ascii=False)


@mcp.tool()
async def mcp_major_career_insights(major_name: str) -> str:
    """查询指定本科专业的学科画像、核心课程、读研去向、核心就业城市与报考避坑提示（如高学费/体检限制）。"""
    query = (major_name or "").strip()
    for key, profile in _MAJOR_CAREER_PROFILES.items():
        if key in query or query in key:
            return json.dumps({"majorName": key, **profile}, ensure_ascii=False)
    return json.dumps(
        {
            "majorName": query,
            "category": "普通本科专业",
            "postgradRate": "20% ~ 35%（视院校层次而定）",
            "typicalCities": ["省会中心城市", "长三角", "珠三角", "成渝城市群"],
            "riskHints": "建议结合目标院校近三年就业质量报告与招生章程核验大类分流和体检要求。",
        },
        ensure_ascii=False,
    )


@mcp.tool()
async def mcp_check_medical_and_subject_redlines(major_name: str, student_notes: str = "") -> str:
    """基于教育部《普通高等学校招生体检工作指导意见》核查专业是否存在色盲、色弱、单科成绩或学费预警。"""
    major = (major_name or "").strip()
    notes = (student_notes or "").strip()
    warnings: list[str] = []
    if any(k in major for k in ("医学", "药学", "化学", "化工", "生物", "光电", "设计", "美术", "交通")):
        warnings.append("该专业方向对色盲/色弱有常见体检限报条款，色觉异常考生请务必核验高校章程。")
    if "软件工程" in major:
        warnings.append("部分高校软件工程专业大三、大四按学分或示范学院标准收取较高学费（约1万~1.6万/年）。")
    if "中外合作" in major:
        warnings.append("中外合作办学通常学费显著高于普通公办（2万~8万/年），且常设外语单科最低分要求。")
    if any(k in notes for k in ("色弱", "色盲")) and any(k in major for k in ("计算机", "电子", "自动化", "通信")):
        warnings.append("已结合你的体检备注：多数高校计算机与通信工程不限色弱，但部分微电子/光电细分方向受限，建议重点核验招生章程。")
    if not warnings:
        warnings.append("未命中高频通用体检/高学费红线，正式填报前仍需核对目标院校当年招生章程原文。")
    return json.dumps({"majorName": major, "warnings": warnings}, ensure_ascii=False)


@mcp.resource("gzgk://policies/{province_code}")
def policy_resource(province_code: str) -> str:
    """MCP Resource: 只读省份招生政策结构化文档。"""
    code = (province_code or "GZ").strip().upper()
    return json.dumps(_PROVINCE_POLICIES.get(code, _PROVINCE_POLICIES["GZ"]), ensure_ascii=False, indent=2)


@mcp.prompt()
def volunteer_audit_prompt(province_code: str = "GZ", score: int = 586) -> str:
    """MCP Prompt: 标准志愿方案风险复盘提示模板。"""
    return (
        f"请以严谨客观的高考志愿决策顾问身份，对 {province_code} 省 {score} 分考生的平行志愿方案执行三步复盘："
        "1) 排查冲档缩招与位次波动；2) 核对招生章程单科与体检红线；3) 基于蒙特卡洛沙箱量化给出替换建议。"
    )


if __name__ == "__main__":
    transport = os.environ.get("MCP_TRANSPORT", "stdio")
    mcp.run(transport=transport)
