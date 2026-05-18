package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 安徽顺序志愿引擎（AH_TIQIAN_BENKE_SEQUENTIAL 本科提前批司法/其他、AH_TIQIAN_ZHUANKE_SEQUENTIAL 高职专科提前批司法/其他、
 * AH_UNIVERSITY_SPECIAL 高校专项、AH_ART_XIAOKAO_BENKE 艺术校考本科批）。
 *
 * <p>口径：「根据志愿、从高分到低分、按比例投档」，调档比例 120% 以内。
 * listing 由 {@link com.gzly.service.AnhuiBatchListingService} 在控制器侧注入。</p>
 */
@Service
public class AnhuiSequentialCollegeEngine implements RecommendEngine {

    public static final String NAME = "AnhuiSequentialCollegeEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
