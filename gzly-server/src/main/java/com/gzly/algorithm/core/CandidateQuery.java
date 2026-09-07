package com.gzly.algorithm.core;

import com.gzly.service.VolunteerService;
import lombok.Data;

/**
 * 候选检索条件（统一管线可插拔点之一的输入契约）。
 *
 * <p>阶段 1 过渡形态：保留原始 GenerateRequest 引用，硬规则过滤 / 偏好约束仍由
 * 各 Provider 内部沿用旧链路逻辑；阶段 2 并线时逐字段拆解并移除该引用。</p>
 */
@Data
public class CandidateQuery {

    private String provinceCode;
    /** 科类轨道：物理类 / 历史类 / 综合（3+3） */
    private String subjectType;
    /** 梯度：冲 / 稳 / 保 / 垫 */
    private String gradient;
    private int rankLow;
    private int rankHigh;
    /** 返回条数上限，必须由调用方给出，禁止无界查询 */
    private int limit;
    /** 阶段 1 过渡字段：原始生成请求 */
    private VolunteerService.GenerateRequest request;
}
