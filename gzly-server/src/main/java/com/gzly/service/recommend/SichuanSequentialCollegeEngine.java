package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 四川院校顺序志愿引擎（SC_TIQIAN_A 提前批 A 段 / SC_GAOXIAO_SPECIAL_PRE_B 高校专项顺序段 / SC_ZHUANKE_EARLY 高职专科提前批 等）。
 *
 * <p>顺序志愿口径：按"志愿优先、从高分到低分、按比例投档"录取，与平行志愿口径不同；
 * 本 engine 仅作为路由标识，listing 与候选注入由
 * {@link com.gzly.service.SichuanBatchListingService} 在控制器侧完成。</p>
 */
@Service
public class SichuanSequentialCollegeEngine implements RecommendEngine {

    public static final String NAME = "SichuanSequentialCollegeEngine";

    @Override
    public String engineName() {
        return NAME;
    }
}
