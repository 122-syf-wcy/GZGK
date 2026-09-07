package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzly.entity.DataScoreRank;
import com.gzly.entity.ScoreRankGz;
import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨年等效位次换算（docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md 第九部分）。
 *
 * <p>公式：E(r, y→t) = round( r ÷ N(y) × N(t) )，其中 N(y) 为该省该科类 y 年参与排名的
 * 考生总数，直接取官方一分一段表的最大累计人数。这是河南省教育考试院在 2025 首年新高考时
 * 官方推荐的"同位分"比例换算口径，也是业界通行做法。</p>
 *
 * <p>设计约束：任一年份的考生总数缺失（一分一段未导入）时**原样返回不换算**，绝不造数；
 * 调用方可用 {@link #canNormalize} 区分"已换算"与"原始值"。人数结果缓存 1 小时。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankNormalizationService {

    private static final long CACHE_TTL_MS = 3_600_000L;
    private static final long MISSING = -1L;

    private final ScoreRankGzMapper scoreRankGzMapper;
    private final DataScoreRankMapper dataScoreRankMapper;

    private final ConcurrentHashMap<String, CachedCount> populationCache = new ConcurrentHashMap<>();

    /**
     * 把 fromYear 口径的位次换算到 toYear 口径。
     * 数据不足时返回原值（保守：宁可不换算，不可用假人数换算）。
     */
    public int normalize(int rank, int fromYear, int toYear, String provinceCode, String subjectType) {
        if (rank <= 0 || fromYear == toYear) {
            return rank;
        }
        Long from = population(provinceCode, fromYear, subjectType);
        Long to = population(provinceCode, toYear, subjectType);
        if (from == null || to == null || from <= 0 || to <= 0) {
            return rank;
        }
        long normalized = Math.round(rank * (double) to / from);
        return (int) Math.max(1, normalized);
    }

    /** 两个年份的考生总数是否都可得（即 normalize 是否会真正换算）。 */
    public boolean canNormalize(int fromYear, int toYear, String provinceCode, String subjectType) {
        if (fromYear == toYear) {
            return true;
        }
        Long from = population(provinceCode, fromYear, subjectType);
        Long to = population(provinceCode, toYear, subjectType);
        return from != null && from > 0 && to != null && to > 0;
    }

    /**
     * 该省该科类某年参与排名的考生总数 = 一分一段表最大累计人数；未导入返回 null。
     * 贵州读 data_score_rank_gz（含新旧科类映射），其余省份读 data_score_rank。
     */
    public Long population(String provinceCode, int year, String subjectType) {
        String code = provinceCode == null || provinceCode.isBlank() ? ProvincePolicyService.GZ : provinceCode.trim();
        String key = code + "|" + year + "|" + subjectType;
        CachedCount cached = populationCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < CACHE_TTL_MS) {
            return cached.count == MISSING ? null : cached.count;
        }
        Long count = loadPopulation(code, year, subjectType);
        populationCache.put(key, new CachedCount(count == null ? MISSING : count, now));
        return count;
    }

    private Long loadPopulation(String provinceCode, int year, String subjectType) {
        try {
            if (ProvincePolicyService.GZ.equals(provinceCode)) {
                String legacy = com.gzly.algorithm.core.ThreeOneTwoSubjectMatcher.legacyEquivalent(subjectType);
                QueryWrapper<ScoreRankGz> wrapper = new QueryWrapper<>();
                wrapper.select("MAX(cumulative_count) AS population")
                        .eq("year", year)
                        .and(w -> w.eq("subject_type", subjectType).or().eq("subject_type", legacy));
                return firstLong(scoreRankGzMapper.selectObjs(wrapper));
            }
            QueryWrapper<DataScoreRank> wrapper = new QueryWrapper<>();
            wrapper.select("MAX(cumulative_count) AS population")
                    .eq("province_code", provinceCode)
                    .eq("year", year)
                    .eq("subject_type", subjectType);
            return firstLong(dataScoreRankMapper.selectObjs(wrapper));
        } catch (Exception e) {
            log.debug("考生总数查询失败: province={}, year={}, subject={}, err={}",
                    provinceCode, year, subjectType, e.getMessage());
            return null;
        }
    }

    private Long firstLong(List<Object> values) {
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return null;
        }
        try {
            return Long.valueOf(values.get(0).toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record CachedCount(long count, long loadedAt) {
    }
}
