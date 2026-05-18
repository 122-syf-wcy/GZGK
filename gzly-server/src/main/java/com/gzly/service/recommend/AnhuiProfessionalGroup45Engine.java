package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 安徽院校专业组 45 平行志愿引擎（AH_BENKE 普通本科批 / AH_ZHUANKE 普通高职专科批主流程）。
 *
 * <p>生成逻辑沿用 {@link com.gzly.service.ProfessionalGroupVolunteerService#generate}，
 * 本类是路由标识，用于在 plan 上回填 engineName 让前端按安徽口径渲染。</p>
 *
 * <p>规则来源：皖招委 2025-05-12《安徽省 2025 年普通高校招生工作实施办法》第 24-38 条。</p>
 */
@Service
public class AnhuiProfessionalGroup45Engine implements RecommendEngine {

    public static final String NAME = "AnhuiProfessionalGroup45Engine";

    @Override
    public String engineName() {
        return NAME;
    }
}
