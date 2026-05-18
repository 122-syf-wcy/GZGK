package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 四川体育类综合分平行志愿引擎（SC_SPORTS_BENKE / SC_SPORTS_ZHUANKE）。
 *
 * <p>体育综合分公式：综合 = 文化 × 30% + 体育统考 × (750/100) × 70%。
 * 详见 {@link com.gzly.service.SichuanCompositeScoreCalculator#calculateSports}。</p>
 */
@Service
public class SichuanSportsCompositeEngine implements RecommendEngine {

    public static final String NAME = "SichuanSportsCompositeEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
