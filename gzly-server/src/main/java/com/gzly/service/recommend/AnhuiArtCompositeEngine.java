package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 安徽艺术类综合分平行志愿引擎（AH_ART_TONGKAO_BENKE 艺术统考本科批 / AH_ART_TONGKAO_ZHUANKE 艺术统考高职专科批）。
 *
 * <p>公式来源：皖招委〔2024〕11 号《关于做好 2025 年普通高校艺术类专业招生考试工作的通知》。</p>
 *
 * <ul>
 *   <li>综合分 1（5050）：音乐 / 舞蹈 / 表（导）演 / 美术与设计 / 书法 → 文化 × 50% + 统考 × 2.5 × 50%</li>
 *   <li>综合分 2（7030）：播音与主持 → 文化 × 70% + 统考 × 2.5 × 30%（与 SC 5050 不同）</li>
 * </ul>
 *
 * <p>实时计算落在 {@link com.gzly.service.AnhuiCompositeScoreCalculator}。</p>
 */
@Service
public class AnhuiArtCompositeEngine implements RecommendEngine {

    public static final String NAME = "AnhuiArtCompositeEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
