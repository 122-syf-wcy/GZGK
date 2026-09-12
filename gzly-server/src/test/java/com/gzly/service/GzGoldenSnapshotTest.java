package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.CandidateFilterEngine;
import com.gzly.algorithm.FallbackRulePredictionEngine;
import com.gzly.algorithm.FeatureBuildEngine;
import com.gzly.algorithm.VolunteerDiagnosisEngine;
import com.gzly.algorithm.VolunteerSortEngine;
import com.gzly.common.ComplianceConstants;
import com.gzly.entity.MajorScoreGz;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.University;
import com.gzly.mapper.BizUserMapper;
import com.gzly.mapper.MajorRequirementGzMapper;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.service.VolunteerService.GenerateRequest;
import com.gzly.service.VolunteerService.GradientRangeSummary;
import com.gzly.service.VolunteerService.PlanMetrics;
import com.gzly.service.VolunteerService.PlanResult;
import com.gzly.service.VolunteerService.VolunteerItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * GZ 主链路 golden 快照测试（期 2 绞杀式重构的前置护栏）。
 *
 * <p>固定一组输入与确定性桩数据，跑 {@link VolunteerService#generate} 完整链路，对输出的
 * <b>稳定子集</b>做快照断言：条数、各梯度数量、梯度分组顺序、入选院校/专业编码序列、
 * 历史调档位次、梯度区间边界（rankLow/rankHigh）。不触碰时间戳、随机 id、耗时等不稳定字段。</p>
 *
 * <p>构桩方式沿用 {@link VolunteerServiceGenerateIntegrationTest}：Mockito 桩出外部依赖
 * （ScoreLineService / AlgorithmService / Mapper / Redis），注入真实算法引擎。
 * 与集成测试的唯一差异：{@code findMajorCandidates} 按位次区间过滤候选，
 * 复刻真实 DB 的分档查询语义，使每个候选只落入一个梯度、避免跨档重复。</p>
 *
 * <p>覆盖边界：本测试覆盖「候选检索 → 硬规则过滤 → 梯度分档 → 排序 → 指标统计 → 区间摘要」
 * 的确定性骨架。算法派生数值（chanceScore 等）仅断言取值范围，不断言精确值——
 * 其精确值依赖 FeatureBuildEngine / FallbackRulePredictionEngine 的浮点口径，
 * 属于后续专门单测的职责。</p>
 */
class GzGoldenSnapshotTest {

    private static final String PROVINCE = "GZ";
    private static final int STUDENT_RANK = 32000;
    /** 志愿总数设为 8，使梯度定为 冲1/稳3/保2/垫2（GradientAllocationEngine 均衡型通用分支）。 */
    private static final int POLICY_TARGET = 8;

    private ScoreLineService scoreLineService;
    private AlgorithmService algorithmService;
    private PlanHistoryMapper planHistoryMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOps;
    private ProvinceRankService provinceRankService;
    private VolunteerService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        scoreLineService = mock(ScoreLineService.class);
        algorithmService = mock(AlgorithmService.class);
        BizUserMapper bizUserMapper = mock(BizUserMapper.class);
        planHistoryMapper = mock(PlanHistoryMapper.class);
        MajorRequirementGzMapper majorRequirementGzMapper = mock(MajorRequirementGzMapper.class);
        OfficialLinkService officialLinkService = mock(OfficialLinkService.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        provinceRankService = mock(ProvinceRankService.class);

        // Redis 桩：缓存恒 miss、锁恒拿到
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(anyString())).thenReturn(null);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);

        // plan_history 写入回填 id
        lenient().when(planHistoryMapper.insert(any(PlanHistory.class))).thenAnswer(inv -> {
            PlanHistory h = inv.getArgument(0);
            h.setId(System.currentTimeMillis());
            return 1;
        });
        lenient().when(majorRequirementGzMapper.selectLatestByMajor(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);
        lenient().when(officialLinkService.loadBySchoolIds(any())).thenReturn(Map.of());

        // 历史线一律为空：机会指数走「以 historyMinRank 为 referenceRank」的确定性退化路径
        lenient().when(algorithmService.getRecentLinesSince(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());
        lenient().when(algorithmService.calcProbabilityFromLines(anyInt(), anyString(), any(), anyString(), anyList()))
                .thenAnswer(inv -> {
                    AlgorithmService.AdmissionProbability p = new AlgorithmService.AdmissionProbability();
                    p.setProbability(60D);
                    p.setLevel("中");
                    p.setHistoryRanks(new ArrayList<>());
                    p.setHistoryYears(new ArrayList<>());
                    return p;
                });
        lenient().when(algorithmService.assessRiskFromLines(anyString(), any(), anyString(), anyList()))
                .thenAnswer(inv -> {
                    AlgorithmService.RiskAssessment r = new AlgorithmService.RiskAssessment();
                    r.setRiskLevel("中等");
                    r.setRiskColor("yellow");
                    r.setHistoryRanks(new ArrayList<>());
                    r.setHistoryYears(new ArrayList<>());
                    return r;
                });
        lenient().when(algorithmService.predictScoreFromLines(anyString(), any(), anyString(), anyList()))
                .thenAnswer(inv -> {
                    AlgorithmService.ScorePrediction sp = new AlgorithmService.ScorePrediction();
                    sp.setPredictedRank(0);
                    sp.setConfidence(60D);
                    sp.setTrend("稳定");
                    sp.setHistoryRanks(new ArrayList<>());
                    sp.setHistoryYears(new ArrayList<>());
                    return sp;
                });

        // 专业级候选：按被查询的位次区间过滤（复刻真实 DB 分档语义），每次返回新副本避免被内部排序破坏
        lenient().when(scoreLineService.findMajorCandidates(anyString(), anyInt(), anyInt(), anyList(), anyInt()))
                .thenAnswer(inv -> {
                    int rankLow = inv.getArgument(1);
                    int rankHigh = inv.getArgument(2);
                    return candidatePool().stream()
                            .filter(m -> m.getMinRank() >= rankLow && m.getMinRank() <= rankHigh)
                            .collect(Collectors.toList());
                });
        // 院校级回退不参与本快照（专业级候选已足够填满各档）
        lenient().when(scoreLineService.findCandidates(anyString(), anyInt(), anyInt(), anyList(), anyInt()))
                .thenReturn(List.of());

        for (String schoolId : List.of("77001", "77002", "77003", "77004", "77005", "77006", "77007", "77008")) {
            lenient().when(scoreLineService.getUniversityById(schoolId)).thenReturn(publicUniversity(schoolId));
        }

        ProvincePolicyService provincePolicyService = new ProvincePolicyService();
        SafetyCodeService safetyCodeService = new SafetyCodeService();

        service = new VolunteerService(
                scoreLineService, algorithmService, bizUserMapper, planHistoryMapper, majorRequirementGzMapper,
                officialLinkService, new ObjectMapper(), stringRedisTemplate,
                new VolunteerMetricsRecorder(), safetyCodeService, provincePolicyService, provinceRankService,
                new CandidateFilterEngine(),
                new FeatureBuildEngine(),
                new FallbackRulePredictionEngine(),
                new VolunteerSortEngine(),
                new VolunteerDiagnosisEngine());

        ReflectionTestUtils.setField(service, "generateCacheSeconds", 30L);
        ReflectionTestUtils.setField(service, "generateLockSeconds", 30L);
        ReflectionTestUtils.setField(service, "generateWaitMillis", 100L);
        ReflectionTestUtils.setField(safetyCodeService, "jwtSecret", "golden-snapshot-test-secret-key-change-me");
    }

    @Test
    void generate_goldenSnapshot_stableSelectionOrderAndCounts() {
        GenerateRequest req = baseValidRequest();

        PlanResult result = service.generate(req, null, "127.0.0.1");

        // 1. 总数与梯度分布（冲1/稳3/保2/垫2，共 8）
        assertThat(result.getTargetCount()).isEqualTo(POLICY_TARGET);
        assertThat(result.getItems()).hasSize(POLICY_TARGET);

        // 2. 整体梯度顺序固定 冲→稳→保→垫（梯度内部顺序由 VolunteerSortEngine 决定，此处不断言）
        assertThat(result.getItems()).extracting(VolunteerItem::getGradient)
                .containsExactly("冲", "稳", "稳", "稳", "保", "保", "垫", "垫");

        // 3. 稳定子集快照：院校/专业编码 + 梯度 + 历史调档位次（排序后逐条比对）
        List<String> snapshot = result.getItems().stream()
                .map(i -> i.getSchoolId() + "|" + i.getMajorName() + "|" + i.getGradient() + "|" + i.getHistoryMinRank())
                .sorted()
                .toList();
        assertThat(snapshot).containsExactly(
                "77001|计算机科学与技术|冲|23000",
                "77002|软件工程|稳|31000",
                "77003|电子信息工程|稳|33000",
                "77004|数学与应用数学|稳|35000",
                "77005|机械工程|保|39000",
                "77006|会计学|保|42000",
                "77007|英语|垫|57700",
                "77008|汉语言文学|垫|57900");

        // 4. 序号从 1 连续编号
        assertThat(result.getItems()).extracting(VolunteerItem::getIndex)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);

        // 5. 全部为专业级候选（无一来自院校级回退）
        assertThat(result.getItems()).allMatch(i -> "专业级".equals(i.getDataSourceType()));

        // 6. 算法派生数值仅锁定取值域（精确值不在本快照职责内）
        assertThat(result.getItems()).allMatch(i -> i.getChanceScore() >= 0 && i.getChanceScore() <= 100);

        // 7. 指标统计与梯度分布一致
        PlanMetrics metrics = result.getMetrics();
        assertThat(metrics.getTotalCount()).isEqualTo(8);
        assertThat(metrics.getChongCount()).isEqualTo(1);
        assertThat(metrics.getWenCount()).isEqualTo(3);
        assertThat(metrics.getBaoCount()).isEqualTo(2);
        assertThat(metrics.getDianCount()).isEqualTo(2);
        assertThat(metrics.getTargetCount()).isEqualTo(POLICY_TARGET);
    }

    @Test
    void generate_goldenSnapshot_stableGradientRangeBoundaries() {
        PlanResult result = service.generate(baseValidRequest(), null, "127.0.0.1");

        // 位次 32000、均衡型下，绝对偏移区间与比例区间共同约束的最终边界（纯位次算术，确定值）
        GradientRangeSummary summary = result.getGradientRangeSummary();
        assertThat(summary.getRanges().get("冲").getRankLow()).isEqualTo(22000);
        assertThat(summary.getRanges().get("冲").getRankHigh()).isEqualTo(29000);
        assertThat(summary.getRanges().get("稳").getRankLow()).isEqualTo(30400);
        assertThat(summary.getRanges().get("稳").getRankHigh()).isEqualTo(36000);
        assertThat(summary.getRanges().get("保").getRankLow()).isEqualTo(38400);
        assertThat(summary.getRanges().get("保").getRankHigh()).isEqualTo(44000);
        assertThat(summary.getRanges().get("垫").getRankLow()).isEqualTo(57600);
        assertThat(summary.getRanges().get("垫").getRankHigh()).isEqualTo(58000);

        // 各档目标数量随总数 8 走 GradientAllocationEngine 均衡型通用分支
        assertThat(summary.getRanges().get("冲").getTargetCount()).isEqualTo(1);
        assertThat(summary.getRanges().get("稳").getTargetCount()).isEqualTo(3);
        assertThat(summary.getRanges().get("保").getTargetCount()).isEqualTo(2);
        assertThat(summary.getRanges().get("垫").getTargetCount()).isEqualTo(2);
    }

    // ───────────── helpers ─────────────

    /** 固定输入：GZ / 物理类 560 分 / 位次 32000 / 均衡型 / 限额 8。 */
    private GenerateRequest baseValidRequest() {
        GenerateRequest req = new GenerateRequest();
        req.setTotalScore(560);
        req.setProvinceRank(STUDENT_RANK);
        req.setProvinceCode(PROVINCE);
        req.setFirstSubject("物理");
        req.setResubjects(List.of("化学", "生物"));
        req.setSelectedSubjects(List.of("物理", "化学", "生物"));
        req.setBatchCode("NORMAL_UNDERGRADUATE");
        req.setCandidateType("普通类");
        req.setPolicyMaxVolunteerCount(POLICY_TARGET);
        req.setAgreedDisclaimer(true);
        req.setDisclaimerVersion(ComplianceConstants.DISCLAIMER_VERSION);
        return req;
    }

    /**
     * 确定性候选池：每校一个专业，位次分别落在 冲/稳/保/垫 的最终区间内
     * （冲 [22000,29000]、稳 [30400,36000]、保 [38400,44000]、垫 [57600,58000]）。
     * 每次调用返回全新对象，避免 pickGradient 内部排序/去重污染后续梯度查询。
     */
    private List<MajorScoreGz> candidatePool() {
        List<MajorScoreGz> pool = new ArrayList<>();
        pool.add(major("77001", "甲大学", "计算机科学与技术", 23000));
        pool.add(major("77002", "乙大学", "软件工程", 31000));
        pool.add(major("77003", "丙大学", "电子信息工程", 33000));
        pool.add(major("77004", "丁大学", "数学与应用数学", 35000));
        pool.add(major("77005", "戊大学", "机械工程", 39000));
        pool.add(major("77006", "己大学", "会计学", 42000));
        pool.add(major("77007", "庚大学", "英语", 57700));
        pool.add(major("77008", "辛大学", "汉语言文学", 57900));
        return pool;
    }

    private MajorScoreGz major(String schoolId, String universityName, String majorName, int minRank) {
        MajorScoreGz m = new MajorScoreGz();
        m.setSchoolId(schoolId);
        m.setUniversityName(universityName);
        m.setMajorName(majorName);
        m.setMajorId(UUID.randomUUID().toString());
        m.setYear(2024);
        m.setSubjectType("物理类");
        m.setBatch("普通本科批");
        m.setMinScore(550);
        m.setMaxScore(580);
        m.setAvgScore(560);
        m.setMinRank(minRank);
        m.setPlanCount(50);
        m.setResubjectRequirement("不限");
        return m;
    }

    private University publicUniversity(String schoolId) {
        University u = new University();
        u.setSchoolId(schoolId);
        u.setName("样例大学" + schoolId);
        u.setProvince("贵州");
        u.setCity("贵阳");
        u.setLevel("本科");
        u.setNatureName("公办");
        u.setTags(new ArrayList<>());
        u.setF985(0);
        u.setF211(0);
        u.setDualClass(0);
        return u;
    }
}
