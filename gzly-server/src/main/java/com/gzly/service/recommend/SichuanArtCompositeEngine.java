package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 四川艺术类综合分平行志愿引擎（SC_ART_BENKE / SC_ART_ZHUANKE）+ 校考顺序段 SC_ART_TIQIAN 也走本 engine 的 listing 路径。
 *
 * <p>综合分公式（11 类统考路由）由 {@link com.gzly.service.SichuanCompositeScoreCalculator} 提供：</p>
 * <ul>
 *   <li>类别 1（文化 50% + 统考 50%）：美术与设计 / 戏剧编导 / 戏剧表演 / 戏剧导演 / 服装表演 / 播音与主持</li>
 *   <li>类别 2（文化 30% + 统考 70%）：音乐表演 / 音乐教育 / 舞蹈 / 书法 / 航空服务艺术</li>
 * </ul>
 */
@Service
public class SichuanArtCompositeEngine implements RecommendEngine {

    public static final String NAME = "SichuanArtCompositeEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
