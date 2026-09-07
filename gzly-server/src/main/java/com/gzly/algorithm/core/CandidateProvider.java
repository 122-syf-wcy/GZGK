package com.gzly.algorithm.core;

import com.gzly.service.VolunteerService;

import java.util.List;
import java.util.Map;

/**
 * 候选提供者（统一管线可插拔点之一，docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md 4.3）。
 * 数据行形状的差异（专业级 vs 院校专业组）收敛在实现内，输出统一为 VolunteerItem。
 */
public interface CandidateProvider {

    /** 本实现服务的志愿单位类型，用于按 BatchPolicy 路由。 */
    VolunteerUnitType supports();

    /** 按位次区间取候选。实现必须尊重 query.limit，禁止全量捞取。 */
    List<VolunteerService.VolunteerItem> fetch(CandidateQuery query);

    /**
     * 批量取历史位次序列（key = 候选唯一键，value = 按年份降序的历史点）。
     * 阶段 4 性能改造的契约位：一次 IN 查询取完，替代当前逐条查库的 N+1。
     * 默认实现返回空，调用方回退旧的逐条路径。
     */
    default Map<String, List<RankPoint>> loadHistories(List<VolunteerService.VolunteerItem> items) {
        return Map.of();
    }

    /** 单个历史录取观测点。 */
    record RankPoint(int year, Integer minRank, Integer minScore, Integer planCount) {
    }
}
