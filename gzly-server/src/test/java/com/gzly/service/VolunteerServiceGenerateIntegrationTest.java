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
import com.gzly.entity.ScoreLineGz;
import com.gzly.entity.University;
import com.gzly.mapper.BizUserMapper;
import com.gzly.mapper.MajorRequirementGzMapper;
import com.gzly.mapper.PlanHistoryMapper;
import com.gzly.service.VolunteerService.GenerateRequest;
import com.gzly.service.VolunteerService.PlanResult;
import com.gzly.service.VolunteerService.VolunteerItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * VolunteerService.generate() 主流程**真实集成测试**：
 * 用 Mockito 桩出 ScoreLineService / AlgorithmService / 各类 Mapper / Redis 等外部依赖，
 * 注入 5 个真实算法引擎实例（CandidateFilter / FeatureBuild / FallbackRulePrediction /
 * VolunteerSort / VolunteerDiagnosis），调用 generate() 完整链路并对最终 PlanResult 做断言。
 *
 * <p>覆盖 6 个用户白皮书要求的端到端不变量：</p>
 * <ol>
 *   <li>{@link #generate_dislikedMajors_shouldNotAppearInFinalItems}</li>
 *   <li>{@link #generate_rejectPrivateSchool_shouldNotContainPrivateSchool}</li>
 *   <li>{@link #generate_rejectChineseForeignCoop_shouldNotContainCoop}</li>
 *   <li>{@link #generate_shouldUsePolicyMaxCount}</li>
 *   <li>{@link #generate_shouldReturnDiagnosis}</li>
 *   <li>{@link #generate_shouldUseNewChanceScoreFormula}</li>
 * </ol>
 */
class VolunteerServiceGenerateIntegrationTest {

    private ScoreLineService scoreLineService;
    private AlgorithmService algorithmService;
    private BizUserMapper bizUserMapper;
    private PlanHistoryMapper planHistoryMapper;
    private MajorRequirementGzMapper majorRequirementGzMapper;
    private OfficialLinkService officialLinkService;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOps;
    private ProvinceRankService provinceRankService;
    private VolunteerService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        scoreLineService = mock(ScoreLineService.class);
        algorithmService = mock(AlgorithmService.class);
        bizUserMapper = mock(BizUserMapper.class);
        planHistoryMapper = mock(PlanHistoryMapper.class);
        majorRequirementGzMapper = mock(MajorRequirementGzMapper.class);
        officialLinkService = mock(OfficialLinkService.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        provinceRankService = mock(ProvinceRankService.class);

        // Redis 桩：缓存恒 miss、锁恒拿到
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(anyString())).thenReturn(null);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);

        // PlanHistory.insert 桩：写 id 让后续读取不空
        lenient().when(planHistoryMapper.insert(any(PlanHistory.class))).thenAnswer(inv -> {
            PlanHistory h = inv.getArgument(0);
            h.setId(System.currentTimeMillis());
            return 1;
        });
        lenient().when(majorRequirementGzMapper.selectLatestByMajor(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);
        lenient().when(officialLinkService.loadBySchoolIds(any())).thenReturn(Map.of());

        // AlgorithmService 桩：返回非 null 占位对象，不走 NPE
        lenient().when(algorithmService.calcProbability(anyInt(), anyString(), any(), anyString()))
                .thenAnswer(inv -> {
                    AlgorithmService.AdmissionProbability p = new AlgorithmService.AdmissionProbability();
                    p.setProbability(60D);
                    p.setLevel("中");
                    p.setHistoryRanks(new ArrayList<>());
                    p.setHistoryYears(new ArrayList<>());
                    return p;
                });
        lenient().when(algorithmService.assessRisk(anyString(), any(), anyString()))
                .thenAnswer(inv -> {
                    AlgorithmService.RiskAssessment r = new AlgorithmService.RiskAssessment();
                    r.setRiskLevel("中等");
                    r.setRiskColor("yellow");
                    r.setHistoryRanks(new ArrayList<>());
                    r.setHistoryYears(new ArrayList<>());
                    return r;
                });
        lenient().when(algorithmService.predictScore(anyString(), any(), anyString()))
                .thenAnswer(inv -> {
                    AlgorithmService.ScorePrediction sp = new AlgorithmService.ScorePrediction();
                    sp.setPredictedRank(20000);
                    sp.setConfidence(60D);
                    sp.setTrend("稳定");
                    sp.setHistoryRanks(new ArrayList<>());
                    sp.setHistoryYears(new ArrayList<>());
                    return sp;
                });
        // getRecentLines: 默认空，FallbackRulePredictionEngine 仍能跑（用 referenceRank fallback）
        lenient().when(algorithmService.getRecentLines(anyString(), any(), anyString(), anyInt()))
                .thenReturn(List.of());

        // ScoreLineService 桩：默认空，单测内部按需重写
        lenient().when(scoreLineService.findMajorCandidates(anyString(), anyInt(), anyInt(), anyList()))
                .thenReturn(List.of());
        lenient().when(scoreLineService.findCandidates(anyString(), anyInt(), anyInt(), anyList()))
                .thenReturn(List.of());
        lenient().when(scoreLineService.getUniversityById(anyString())).thenReturn(null);

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

        // 默认锁等待 / 缓存 TTL，缩小到测试上下文
        ReflectionTestUtils.setField(service, "generateCacheSeconds", 30L);
        ReflectionTestUtils.setField(service, "generateLockSeconds", 30L);
        ReflectionTestUtils.setField(service, "generateWaitMillis", 100L);
        ReflectionTestUtils.setField(safetyCodeService, "jwtSecret", "integration-test-secret-key-change-me");
    }

    @Test
    void generate_dislikedMajors_shouldNotAppearInFinalItems() {
        // 4 条候选：含两条"土木工程"，应被 dislikedMajors 过滤掉
        List<MajorScoreGz> candidates = new ArrayList<>(Arrays.asList(
                major("1001", "贵州大学", "土木工程", 25000, 2024),
                major("1001", "贵州大学", "计算机科学与技术", 22000, 2024),
                major("1002", "贵州师范大学", "土木工程", 27000, 2024),
                major("1002", "贵州师范大学", "数学与应用数学", 26000, 2024)
        ));
        stubCandidatesAcrossGradients(candidates);
        stubUniversities("1001", "贵州大学", "贵阳", "公办");
        stubUniversities("1002", "贵州师范大学", "贵阳", "公办");

        GenerateRequest req = baseValidRequest();
        req.setDislikedMajors(List.of("土木"));

        PlanResult result = service.generate(req, null, "127.0.0.1");

        assertThat(result.getItems()).isNotEmpty();
        for (VolunteerItem item : result.getItems()) {
            assertThat(item.getMajorName())
                    .as("item.majorName 不应含 dislikedMajor")
                    .doesNotContain("土木");
        }
    }

    @Test
    void generate_rejectPrivateSchool_shouldNotContainPrivateSchool() {
        List<MajorScoreGz> candidates = new ArrayList<>(Arrays.asList(
                major("2001", "贵州民办学院", "工商管理", 60000, 2024),
                major("2001", "贵州民办学院", "市场营销", 62000, 2024),
                major("1001", "贵州大学", "计算机科学与技术", 22000, 2024),
                major("1003", "贵州工程学院", "电子信息工程", 50000, 2024)
        ));
        stubCandidatesAcrossGradients(candidates);
        stubUniversities("1001", "贵州大学", "贵阳", "公办");
        stubUniversities("1003", "贵州工程学院", "贵阳", "公办");
        stubUniversities("2001", "贵州民办学院", "贵阳", "民办");

        GenerateRequest req = baseValidRequest();
        req.setAcceptPrivateSchool(false);

        PlanResult result = service.generate(req, null, "127.0.0.1");

        assertThat(result.getItems()).isNotEmpty();
        for (VolunteerItem item : result.getItems()) {
            assertThat(item.getUniversityName()).doesNotContain("民办");
            // 民办院校的 schoolNature/SchoolNature 字段应当不出现
            assertThat(item.getSchoolNature()).doesNotContain("民办");
        }
    }

    @Test
    void generate_rejectChineseForeignCoop_shouldNotContainCoop() {
        List<MajorScoreGz> candidates = new ArrayList<>(Arrays.asList(
                major("1004", "贵州大学（中外合作）", "计算机科学与技术（中外合作）", 28000, 2024),
                major("1005", "贵州大学（联合培养）", "电子信息（联合培养）", 30000, 2024),
                major("1001", "贵州大学", "计算机科学与技术", 22000, 2024),
                major("1002", "贵州师范大学", "数学与应用数学", 26000, 2024)
        ));
        stubCandidatesAcrossGradients(candidates);
        stubUniversities("1001", "贵州大学", "贵阳", "公办");
        stubUniversities("1002", "贵州师范大学", "贵阳", "公办");
        University coop1 = univ("1004", "贵州大学（中外合作）", "贵阳", "中外合作");
        coop1.setTags(List.of("中外合作办学"));
        lenient().when(scoreLineService.getUniversityById("1004")).thenReturn(coop1);
        University coop2 = univ("1005", "贵州大学（联合培养）", "贵阳", "公办");
        coop2.setTags(List.of("中外合作办学"));
        lenient().when(scoreLineService.getUniversityById("1005")).thenReturn(coop2);

        GenerateRequest req = baseValidRequest();
        req.setAcceptChineseForeignCoop(false);

        PlanResult result = service.generate(req, null, "127.0.0.1");

        assertThat(result.getItems()).isNotEmpty();
        for (VolunteerItem item : result.getItems()) {
            assertThat(item.getUniversityName()).doesNotContain("中外合作");
            assertThat(item.getMajorName()).doesNotContain("中外合作");
            assertThat(item.getMajorName()).doesNotContain("联合培养");
        }
    }

    @Test
    void generate_shouldUsePolicyMaxCount() {
        // 桩出至少 80 条候选数据让生成尽量充分，再用 policyMaxVolunteerCount=60 限制
        List<MajorScoreGz> candidates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String schoolId = "30" + (1000 + i);
            String schoolName = "测试大学" + i;
            candidates.add(major(schoolId, schoolName, "测试专业" + i, 18000 + i * 200, 2024));
            stubUniversities(schoolId, schoolName, "贵阳", "公办");
        }
        stubCandidatesAcrossGradients(candidates);

        GenerateRequest req = baseValidRequest();
        req.setPolicyMaxVolunteerCount(60);

        PlanResult result = service.generate(req, null, "127.0.0.1");

        assertThat(result.getTargetCount()).isEqualTo(60);
        // 实际 items 数量受候选数据约束，但绝不能超过 policy 上限
        assertThat(result.getItems().size()).isLessThanOrEqualTo(60);
    }

    @Test
    void generate_shouldReturnDiagnosis() {
        List<MajorScoreGz> candidates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String sid = "40" + (100 + i);
            candidates.add(major(sid, "样例大学" + i, "样例专业" + i, 16000 + i * 400, 2024));
            stubUniversities(sid, "样例大学" + i, "贵阳", "公办");
        }
        stubCandidatesAcrossGradients(candidates);

        GenerateRequest req = baseValidRequest();

        PlanResult result = service.generate(req, null, "127.0.0.1");

        assertThat(result.getDiagnosis()).isNotNull();
        assertThat(result.getDiagnosis()).isNotEmpty();
        // VolunteerDiagnosisEngine 必出的关键字段
        assertThat(result.getDiagnosis()).containsKey("totalCount");
        assertThat(result.getDiagnosis()).containsKey("policyMaxCount");
        assertThat(result.getDiagnosis()).containsKey("gradientCount");
        assertThat(result.getDiagnosis()).containsKey("warnings");
        assertThat(result.getDiagnosis()).containsKey("summary");
    }

    @Test
    void generate_shouldUseNewChanceScoreFormula() {
        // 构造 5 年稳定历史 + 计划稳定的候选，确保 FeatureBuildEngine 能产出非零特征值，
        // FallbackRulePredictionEngine 的 sigmoid+4 项 penalty 公式应当为该候选写入完整字段
        List<MajorScoreGz> candidates = new ArrayList<>(Arrays.asList(
                major("9001", "稳定大学", "计算机科学与技术", 30000, 2024)
        ));
        stubCandidatesAcrossGradients(candidates);
        stubUniversities("9001", "稳定大学", "贵阳", "公办");

        // 5 年历史，位次稳定 → rankVolatility 低 / planChangeRate 0 / dataConfidence 高
        List<ScoreLineGz> history = new ArrayList<>();
        for (int year = 2020; year <= 2024; year++) {
            history.add(scoreLine("9001", year, 30000 + (year - 2020) * 50, 80));
        }
        lenient().when(algorithmService.getRecentLines(eqStr("9001"), any(), eqStr("物理类"), anyInt()))
                .thenReturn(history);

        GenerateRequest req = baseValidRequest();
        req.setProvinceRank(30000); // 候选位次 = 用户位次，sigmoid 应当落在中位

        PlanResult result = service.generate(req, null, "127.0.0.1");

        VolunteerItem item = findBySchool(result.getItems(), "9001");
        assertThat(item).as("候选应进入最终方案").isNotNull();

        // FallbackRulePredictionEngine 真实写入的 6 个核心字段，缺一即视为新公式未生效
        assertThat(item.getChanceScore())
                .as("chanceScore 应在 [0,100]，且不能是 0（说明 FallbackRulePredictionEngine 真实生效）")
                .isBetween(1, 100);
        assertThat(item.getChanceLevel())
                .as("chanceLevel 应由 FallbackRulePredictionEngine 派生")
                .isNotBlank();
        assertThat(item.getRiskLevel())
                .as("riskLevel 应由 FallbackRulePredictionEngine 派生")
                .isNotBlank()
                .isIn("很低", "较低", "中等", "较高");
        assertThat(item.getConfidenceLevel())
                .as("confidenceLevel 应由 FallbackRulePredictionEngine 派生")
                .isNotBlank();
        assertThat(item.getDataConfidence())
                .as("dataConfidence 应由 FeatureBuildEngine 写入 [0,100]")
                .isBetween(0.0D, 100.0D);
        assertThat(item.getPredictedMinRank())
                .as("predictedMinRank 应由 FallbackRulePredictionEngine 写入")
                .isGreaterThan(0);
    }

    // ───────────── helpers ─────────────

    private GenerateRequest baseValidRequest() {
        GenerateRequest req = new GenerateRequest();
        req.setTotalScore(560);
        req.setProvinceRank(32000);
        req.setProvinceCode("GZ");
        req.setFirstSubject("物理");
        req.setResubjects(List.of("化学", "生物"));
        req.setSelectedSubjects(List.of("物理", "化学", "生物"));
        req.setBatchCode("NORMAL_UNDERGRADUATE");
        req.setCandidateType("普通类");
        req.setAgreedDisclaimer(true);
        req.setDisclaimerVersion(ComplianceConstants.DISCLAIMER_VERSION);
        return req;
    }

    /** 把同一批 candidates 桩到 4 个梯度区间查询接口（findMajorCandidates 不分梯度，参数仅 rankLow/rankHigh）。
     *  用 thenAnswer 每次返回新 ArrayList 副本，避免内部 sort/dedup 破坏后续调用的数据。 */
    private void stubCandidatesAcrossGradients(List<MajorScoreGz> candidates) {
        lenient().when(scoreLineService.findMajorCandidates(anyString(), anyInt(), anyInt(), any()))
                .thenAnswer(inv -> new ArrayList<>(candidates));
    }

    private void stubUniversities(String schoolId, String name, String city, String nature) {
        University u = univ(schoolId, name, city, nature);
        lenient().when(scoreLineService.getUniversityById(schoolId)).thenReturn(u);
    }

    private University univ(String schoolId, String name, String city, String nature) {
        University u = new University();
        u.setSchoolId(schoolId);
        u.setName(name);
        u.setProvince("贵州");
        u.setCity(city);
        u.setLevel("本科");
        u.setNatureName(nature);
        u.setTags(new ArrayList<>());
        u.setF985(0);
        u.setF211(0);
        u.setDualClass(0);
        return u;
    }

    private MajorScoreGz major(String schoolId, String universityName, String majorName,
                               int minRank, int year) {
        MajorScoreGz m = new MajorScoreGz();
        m.setSchoolId(schoolId);
        m.setUniversityName(universityName);
        m.setMajorName(majorName);
        m.setMajorId(UUID.randomUUID().toString());
        m.setYear(year);
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

    private ScoreLineGz scoreLine(String schoolId, int year, int minRank, int planCount) {
        ScoreLineGz s = new ScoreLineGz();
        s.setSchoolId(schoolId);
        s.setUniversityName("学校" + schoolId);
        s.setYear(year);
        s.setSubjectType("物理类");
        s.setBatch("普通本科批");
        s.setMinScore(550);
        s.setMaxScore(580);
        s.setMinRank(minRank);
        s.setPlanCount(planCount);
        return s;
    }

    private VolunteerItem findBySchool(List<VolunteerItem> items, String schoolId) {
        return items.stream()
                .filter(i -> schoolId.equals(i.getSchoolId()))
                .findFirst()
                .orElse(null);
    }

    private static String eqStr(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
