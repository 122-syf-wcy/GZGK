package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 安徽专项计划资格 + listing 引擎（AH_NATIONAL_SPECIAL 国家专项 / AH_LOCAL_SPECIAL 地方专项 /
 * AH_UNIVERSITY_SPECIAL 高校专项 / AH_TIQIAN_BENKE_PARALLEL 本科提前批 6 子类合并 20 平行 /
 * AH_TIQIAN_ZHUANKE_PARALLEL 高职提前批定向培养军士/免费医学/农技推广 20 平行）。
 *
 * <p>本 engine 仅作路由标识；listing 由 {@link com.gzly.service.AnhuiBatchListingService} 按
 * 批次关键词在 {@code data_admission_group_line} 注入候选 + 资格审核要点。</p>
 */
@Service
public class AnhuiSpecialPlanEligibilityEngine implements RecommendEngine {

    public static final String NAME = "AnhuiSpecialPlanEligibilityEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
