package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 四川 8 类专项/特殊批次的资格 + listing 引擎（SC_TIQIAN_BEFORE_A_NATIONAL / SC_BENKE_A_NATIONAL /
 * SC_BENKE_A_LOCAL / SC_BENKE_GAOXIAO_SPECIAL / SC_BENKE_REGION_BALANCE / SC_BENKE_MINORITY_PRE /
 * SC_BENKE_SPORTS_TEAM 等）。
 *
 * <p>当前以「展示批次规则 + 资格审核要点 + 候选 listing」为主，
 * 配合 {@link com.gzly.service.SichuanBatchListingService} 在控制器侧注入 items 与 dataQualityWarning。</p>
 */
@Service
public class SichuanSpecialPlanEligibilityEngine implements RecommendEngine {

    public static final String NAME = "SichuanSpecialPlanEligibilityEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
