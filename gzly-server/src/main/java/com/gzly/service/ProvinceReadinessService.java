package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzly.common.exception.BizException;
import com.gzly.entity.DataScoreRank;
import com.gzly.entity.MajorScoreGz;
import com.gzly.entity.ScoreRankGz;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 省份数据就绪度门禁（docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md 第六部分）。
 *
 * <p>四态由**真实行数**实时判定，不依赖人工维护的枚举：</p>
 * <ul>
 *   <li>LOCKED：该省该科类官方一分一段行数为 0，连位次口径都没有；</li>
 *   <li>QUERY_ONLY：有一分一段，但候选数据不足以凑满该省本科批志愿数；</li>
 *   <li>ESTIMATE：候选充足，可基于历史数据生成（全程标注"历史估算"）；</li>
 *   <li>FULL：当年官方数据齐备且回测达标——阶段 0 尚无判定来源，恒不返回。</li>
 * </ul>
 *
 * <p>结果按省缓存 60 秒；判定失败（如表不存在）按 LOCKED 处理并记录原因，保证接口不抛 500。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvinceReadinessService {

    public static final String LEVEL_LOCKED = "LOCKED";
    public static final String LEVEL_QUERY_ONLY = "QUERY_ONLY";
    public static final String LEVEL_ESTIMATE = "ESTIMATE";
    public static final String LEVEL_FULL = "FULL";

    private static final long CACHE_TTL_MS = 60_000L;

    private final ProvincePolicyService provincePolicyService;
    private final ScoreRankGzMapper scoreRankGzMapper;
    private final MajorScoreGzMapper majorScoreGzMapper;
    private final DataScoreRankMapper dataScoreRankMapper;
    private final DataAdmissionGroupLineMapper groupLineMapper;

    private final ConcurrentHashMap<String, CachedReadiness> cache = new ConcurrentHashMap<>();

    public ProvinceReadiness getReadiness(String provinceCode) {
        String code = provincePolicyService.normalizeProvinceCode(provinceCode);
        CachedReadiness cached = cache.get(code);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < CACHE_TTL_MS) {
            return cached.readiness;
        }
        ProvinceReadiness readiness = evaluate(code);
        cache.put(code, new CachedReadiness(readiness, now));
        return readiness;
    }

    /**
     * 生成前门禁：请求科类未达到 ESTIMATE 时抛出带具体缺口说明的业务异常。
     * 结构化原因同时可通过 batch-support 接口获取。
     */
    public void requireGenerationReady(String provinceCode, String subjectType) {
        ProvinceReadiness readiness = getReadiness(provinceCode);
        SubjectReadiness subject = readiness.getSubjects().stream()
                .filter(s -> Objects.equals(s.getSubjectType(), subjectType))
                .findFirst()
                .orElse(null);
        String level = subject != null ? subject.getLevel() : readiness.getLevel();
        if (LEVEL_ESTIMATE.equals(level) || LEVEL_FULL.equals(level)) {
            return;
        }
        String reason = subject != null ? subject.getReason() : firstReason(readiness);
        throw new BizException(String.format("%s专区暂未开放智能生成：%s", readiness.getProvinceName(),
                reason == null || reason.isBlank() ? "数据尚未达到解锁门槛，仅开放院校与分数线查询。" : reason));
    }

    // ══════════════════ 判定 ══════════════════

    private ProvinceReadiness evaluate(String code) {
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(code);
        ProvinceReadiness readiness = new ProvinceReadiness();
        readiness.setProvinceCode(code);
        readiness.setProvinceName(policy.getProvinceName());
        readiness.setTargetCount(policy.getTargetCount());
        readiness.setEvaluatedAt(LocalDateTime.now());

        List<SubjectReadiness> subjects = new ArrayList<>();
        for (String subjectType : policy.getSubjectTypes()) {
            try {
                subjects.add(ProvincePolicyService.GZ.equals(code)
                        ? evaluateGuizhouSubject(policy, subjectType)
                        : evaluateGroupSubject(policy, subjectType));
            } catch (Exception e) {
                log.warn("省份就绪度判定失败: province={}, subject={}, err={}", code, subjectType, e.getMessage());
                SubjectReadiness failed = new SubjectReadiness();
                failed.setSubjectType(subjectType);
                failed.setLevel(LEVEL_LOCKED);
                failed.setReason("数据就绪度检查暂不可用，默认锁定生成入口。");
                failed.getMissingData().add("readiness_check_failed");
                subjects.add(failed);
            }
        }
        readiness.setSubjects(subjects);
        readiness.setLevel(aggregateLevel(subjects));
        readiness.setDataYears(loadDataYears(code));
        readiness.setLatestDataYear(readiness.getDataYears().isEmpty() ? null : readiness.getDataYears().get(0));
        return readiness;
    }

    /** 贵州：一分一段（data_score_rank_gz）+ 专业级录取线（data_major_score_gz）。 */
    private SubjectReadiness evaluateGuizhouSubject(ProvincePolicyService.ProvincePolicy policy, String subjectType) {
        SubjectReadiness result = new SubjectReadiness();
        result.setSubjectType(subjectType);
        String legacy = "物理类".equals(subjectType) ? "理科" : "文科";

        Long scoreRankRows = scoreRankGzMapper.selectCount(new LambdaQueryWrapper<ScoreRankGz>()
                .and(w -> w.eq(ScoreRankGz::getSubjectType, subjectType).or().eq(ScoreRankGz::getSubjectType, legacy)));
        result.setScoreRankRows(scoreRankRows == null ? 0 : scoreRankRows);
        if (result.getScoreRankRows() <= 0) {
            result.setLevel(LEVEL_LOCKED);
            result.setReason(String.format("%s官方一分一段表尚未导入，无法建立位次口径。", subjectType));
            result.getMissingData().add("official_score_rank");
            return result;
        }

        // 与候选检索的科类纪元隔离同口径：只统计新高考首年（贵州 2024）及之后的行，
        // 避免"就绪度按全年份说充足、实际候选池只有纪元内数据"的判定漂移。
        int eraYear = Math.max(2021, policy.getNewGaokaoFirstYear());
        Long majorRows = majorScoreGzMapper.selectCount(new LambdaQueryWrapper<MajorScoreGz>()
                .and(w -> w.eq(MajorScoreGz::getSubjectType, subjectType).or().eq(MajorScoreGz::getSubjectType, legacy))
                .ge(MajorScoreGz::getYear, eraYear)
                .isNotNull(MajorScoreGz::getMinRank)
                .gt(MajorScoreGz::getMinRank, 0));
        result.setCandidatePoolCount(majorRows == null ? 0 : majorRows);
        result.setRequiredCount(policy.getTargetCount());
        if (result.getCandidatePoolCount() < policy.getTargetCount()) {
            result.setLevel(LEVEL_QUERY_ONLY);
            result.setReason(String.format("%s专业级录取线仅%d条，不足以支撑%d个志愿生成。",
                    subjectType, result.getCandidatePoolCount(), policy.getTargetCount()));
            result.getMissingData().add("major_score_lines");
            return result;
        }

        result.setLevel(LEVEL_ESTIMATE);
        result.setReason(String.format("%s按已核验历史数据开放估算生成；2026年官方招生计划与投档线尚未导入。", subjectType));
        result.getMissingData().add("official_2026_plan");
        result.getMissingData().add("official_2026_score_line");
        return result;
    }

    /** 院校专业组省份：多省一分一段（data_score_rank）+ 专业组调档线（data_admission_group_line）。 */
    private SubjectReadiness evaluateGroupSubject(ProvincePolicyService.ProvincePolicy policy, String subjectType) {
        SubjectReadiness result = new SubjectReadiness();
        result.setSubjectType(subjectType);
        String code = policy.getProvinceCode();

        Long scoreRankRows = dataScoreRankMapper.selectCount(new LambdaQueryWrapper<DataScoreRank>()
                .eq(DataScoreRank::getProvinceCode, code)
                .eq(DataScoreRank::getSubjectType, subjectType));
        result.setScoreRankRows(scoreRankRows == null ? 0 : scoreRankRows);
        if (result.getScoreRankRows() <= 0) {
            result.setLevel(LEVEL_LOCKED);
            result.setReason(String.format("%s%s官方一分一段表尚未导入，无法建立位次口径，生成入口锁定。",
                    policy.getProvinceName(), subjectType));
            result.getMissingData().add("official_score_rank");
            return result;
        }

        Integer latestYear = groupLineMapper.selectLatestYear(code, subjectType);
        if (latestYear == null) {
            result.setLevel(LEVEL_QUERY_ONLY);
            result.setReason(String.format("%s%s院校专业组调档线尚未导入，仅开放院校与分数线查询。",
                    policy.getProvinceName(), subjectType));
            result.getMissingData().add("group_admission_lines");
            return result;
        }
        result.setLatestCandidateYear(latestYear);

        Long groups = groupLineMapper.countDistinctGroups(code, latestYear, subjectType,
                batchKeyword(policy.getTargetBatch()));
        result.setCandidatePoolCount(groups == null ? 0 : groups);
        result.setRequiredCount(policy.getTargetCount());
        if (result.getCandidatePoolCount() < policy.getTargetCount()) {
            result.setLevel(LEVEL_QUERY_ONLY);
            result.setReason(String.format("%s%s可核验院校专业组当前%d组，未达到%d组解锁门槛，仅开放院校与分数线查询。",
                    policy.getProvinceName(), subjectType, result.getCandidatePoolCount(), policy.getTargetCount()));
            result.getMissingData().add("group_admission_lines_insufficient");
            return result;
        }

        result.setLevel(LEVEL_ESTIMATE);
        result.setReason(String.format("%s%s按%d年已核验专业组数据开放估算生成；当年官方计划与投档线尚未导入。",
                policy.getProvinceName(), subjectType, latestYear));
        result.getMissingData().add("official_current_year_plan");
        return result;
    }

    private List<Integer> loadDataYears(String code) {
        try {
            if (ProvincePolicyService.GZ.equals(code)) {
                QueryWrapper<MajorScoreGz> wrapper = new QueryWrapper<>();
                wrapper.select("DISTINCT year").orderByDesc("year");
                return majorScoreGzMapper.selectObjs(wrapper).stream()
                        .filter(Objects::nonNull)
                        .map(v -> Integer.valueOf(v.toString()))
                        .toList();
            }
            return groupLineMapper.selectDistinctYears(code);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String aggregateLevel(List<SubjectReadiness> subjects) {
        int best = 0;
        for (SubjectReadiness subject : subjects) {
            best = Math.max(best, levelOrder(subject.getLevel()));
        }
        return switch (best) {
            case 3 -> LEVEL_FULL;
            case 2 -> LEVEL_ESTIMATE;
            case 1 -> LEVEL_QUERY_ONLY;
            default -> LEVEL_LOCKED;
        };
    }

    private int levelOrder(String level) {
        return switch (level == null ? "" : level) {
            case LEVEL_FULL -> 3;
            case LEVEL_ESTIMATE -> 2;
            case LEVEL_QUERY_ONLY -> 1;
            default -> 0;
        };
    }

    private String firstReason(ProvinceReadiness readiness) {
        return readiness.getSubjects().stream()
                .map(SubjectReadiness::getReason)
                .filter(r -> r != null && !r.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** 与 ProfessionalGroupVolunteerService.batchKeyword 同口径：批次名含"本科"时用宽匹配关键字。 */
    private String batchKeyword(String targetBatch) {
        String value = targetBatch == null ? "" : targetBatch.trim();
        return value.contains("本科") ? "本科" : value;
    }

    private record CachedReadiness(ProvinceReadiness readiness, long loadedAt) {
    }

    @Data
    public static class ProvinceReadiness {
        private String provinceCode;
        private String provinceName;
        /** 省级聚合状态 = 各科类中的最高可用档 */
        private String level = LEVEL_LOCKED;
        private int targetCount;
        private List<SubjectReadiness> subjects = new ArrayList<>();
        private List<Integer> dataYears = new ArrayList<>();
        private Integer latestDataYear;
        private LocalDateTime evaluatedAt;
    }

    @Data
    public static class SubjectReadiness {
        private String subjectType;
        private String level = LEVEL_LOCKED;
        private long scoreRankRows;
        /** 候选池规模：贵州为专业级录取线行数，专业组省份为最近年份可核验组数 */
        private long candidatePoolCount;
        private Integer latestCandidateYear;
        private int requiredCount;
        private String reason;
        private List<String> missingData = new ArrayList<>();
    }
}
