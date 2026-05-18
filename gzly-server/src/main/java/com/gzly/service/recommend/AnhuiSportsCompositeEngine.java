package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 安徽体育类综合分平行志愿引擎（AH_SPORTS_BENKE 体育本科批 / AH_SPORTS_ZHUANKE 体育高职专科批）。
 *
 * <p>公式（与四川 30/70 不同，安徽是带文化控线的线性映射）：</p>
 *
 * <p><code>综合 = 1.2 × 专业总分 + 0.8 × [60 + 40 × (文化分 - 本科文化控线) ÷ (750 - 控线)]</code></p>
 *
 * <ul>
 *   <li>本科文化控线按普通类本科控线 65% 划定：2025 物理 300、历史 310</li>
 *   <li>实时计算落在 {@link com.gzly.service.AnhuiCompositeScoreCalculator#calculateSports}</li>
 * </ul>
 */
@Service
public class AnhuiSportsCompositeEngine implements RecommendEngine {

    public static final String NAME = "AnhuiSportsCompositeEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
