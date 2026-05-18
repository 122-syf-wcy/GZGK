package com.gzly.service.recommend;

import org.springframework.stereotype.Service;

/**
 * 四川院校专业组 45 平行志愿引擎（主流程 SC_BENKE_B / SC_ZHUANKE_B 等普通类平行院校专业组）。
 *
 * <p>当前生成逻辑落在 {@link com.gzly.service.ProfessionalGroupVolunteerService#generate}，
 * 本类只作为 {@link RecommendEngine} 注册位 + engineName 标识，便于
 * {@link com.gzly.service.recommend.ProvinceBatchEngineRouter} 把四川主流程批次精确路由到本 engine。</p>
 *
 * <p>批次代码：SC_BENKE_B / SC_ZHUANKE_B。规则来源：四川省 2026 实施规定。</p>
 */
@Service
public class SichuanProfessionalGroup45Engine implements RecommendEngine {

    public static final String NAME = "SichuanProfessionalGroup45Engine";

    @Override
    public String engineName() {
        return NAME;
    }
}
