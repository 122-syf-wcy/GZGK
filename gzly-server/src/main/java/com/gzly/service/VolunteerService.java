package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.CandidateFilterEngine;
import com.gzly.algorithm.FallbackRulePredictionEngine;
import com.gzly.algorithm.FeatureBuildEngine;
import com.gzly.algorithm.PortfolioMonteCarloSimulator;
import com.gzly.algorithm.ProbabilityCalibration;
import com.gzly.algorithm.RecruitTypeClassifier;
import com.gzly.algorithm.VolunteerDiagnosisEngine;
import com.gzly.algorithm.VolunteerSortEngine;
import com.gzly.common.ComplianceConstants;
import com.gzly.common.exception.BizException;
import com.gzly.entity.BizUser;
import com.gzly.entity.MajorScoreGz;
import com.gzly.entity.MajorRequirementGz;
import com.gzly.entity.PlanHistory;
import com.gzly.entity.ScoreLineGz;
import com.gzly.entity.UniOfficialLink;
import com.gzly.entity.University;
import com.gzly.mapper.BizUserMapper;
import com.gzly.mapper.MajorRequirementGzMapper;
import com.gzly.mapper.PlanHistoryMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 96 志愿生成引擎 — 贵州专属
 *
 * 梯度算法（基于用户手填官方全省位次 R）：
 * - 默认按方案风格采用预设冲/稳/保/垫相对位次区间
 * - 用户可在生成前自定义梯度区间，后端统一校验并在结果中留痕
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VolunteerService {

    private final ScoreLineService scoreLineService;
    private final AlgorithmService algorithmService;
    private final BizUserMapper bizUserMapper;
    private final PlanHistoryMapper planHistoryMapper;
    private final MajorRequirementGzMapper majorRequirementGzMapper;
    private final OfficialLinkService officialLinkService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final VolunteerMetricsRecorder metricsRecorder;
    private final SafetyCodeService safetyCodeService;
    private final ProvincePolicyService provincePolicyService;
    private final ProvinceRankService provinceRankService;
    /** ── 主链路接入的算法引擎（按计划接入 generate / pickGradient / enrichWithAlgorithms /
     *     applyPlanOrdering / generate 末尾产出 diagnosis）。 */
    private final CandidateFilterEngine candidateFilterEngine;
    private final FeatureBuildEngine featureBuildEngine;
    private final FallbackRulePredictionEngine fallbackRulePredictionEngine;
    private final VolunteerSortEngine volunteerSortEngine;
    private final VolunteerDiagnosisEngine volunteerDiagnosisEngine;
    private final AdmissionYearService admissionYearService;

    @Value("${gzly.stability.generate-cache-seconds:120}")
    private long generateCacheSeconds;
    @Value("${gzly.stability.generate-lock-seconds:30}")
    private long generateLockSeconds;
    @Value("${gzly.stability.generate-wait-millis:4000}")
    private long generateWaitMillis;

    // 梯度配置：按报告建议的“冲/稳/保/兜底”比例折算为贵州本科批 96 志愿。
    private static final int TOTAL_COUNT = 96;
    private static final double PORTFOLIO_SAFETY_THRESHOLD = 98.0;
    private static final int MAX_NEGATIVE_OFFSET = -50000;
    private static final int MAX_POSITIVE_OFFSET = 100000;
    private static final List<String> GRADIENT_ORDER = List.of("冲", "稳", "保", "垫");
    private static final String NEIGHBOR_GRADIENT_BACKFILL = "NEIGHBOR_GRADIENT_BACKFILL";
    public static final String ADVISOR_SOURCE_PROJECT_NAME = "Eric-Yibo-Shen/zhangxuefeng-skillset";
    public static final String ADVISOR_SOURCE_PROJECT_URL = "https://github.com/Eric-Yibo-Shen/zhangxuefeng-skillset";
    public static final String ADVISOR_SOURCE_NOTE =
            "本模块参考 GitHub 开源项目 Eric-Yibo-Shen/zhangxuefeng-skillset 的高考志愿决策框架，"
                    + "并吸收 alchaincyf/zhangxuefeng-skill 中公开整理的策略抽象（数据优先、就业倒推、中位数原则、城市/学校/专业取舍、家庭成本分流），"
                    + "由系统按结构化志愿数据生成；不扮演张雪峰本人，不代表张雪峰本人或任何机构官方意见，也不构成录取承诺。";

    @Data
    public static class VolunteerItem {
        private int index;
        private String provinceCode;
        private String volunteerUnitType;
        private String volunteerUnitLabel;
        private String universityName;
        private String schoolId;
        private String groupCode;
        private String groupName;
        private List<String> groupMajors;
        private Boolean obeyAdjustment;
        private String majorName;
        private String province;
        private String city;
        private List<String> tags;
        private String gradient;          // 冲/稳/保/垫
        private int historyMinScore;
        private int historyMinRank;
        private int referenceYear;
        private String resubjectRequirement;
        private String subjectRequirementSource; // official_requirement / score_line / inferred / missing
        private Integer latestPlanCount;  // 最近一年可用招生计划数
        private String planTrend;         // 扩招/缩招/基本稳定/单年计划/计划数暂缺
        private String planRiskNote;      // 招生计划变化解释
        private double planExpansionIndex;       // 当年计划相对近年基准的扩招指数，100=稳定
        private String planExpansionLabel;       // 扩招指数标签
        private String planExpansionNote;        // 扩招指数解释
        private double schoolEnrollmentIndex;    // 招生供给指数，0-100
        private String schoolEnrollmentLabel;    // 招生供给标签
        private String schoolEnrollmentNote;     // 招生供给解释
        private int rankGap;              // 历史最低位次 - 考生位次，正数更宽松
        private double rankGapRatio;      // rankGap / 考生位次，百分比
        private int recommendationScore;  // 综合推荐分，仅用于排序解释
        private int precisionScore;       // 推荐精度分：位次、计划、供给、数据置信度综合
        private String precisionLabel;    // 精度标签
        private String precisionNote;     // 精度解释
        // ── 算法增强字段 ──
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private double admissionProb;     // 内部历史机会基线，不对新版公共响应展示
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String probLevel;         // 内部机会基线等级，不对新版公共响应展示
        private int chanceScore;          // 用户可见机会指数 0-100
        private String chanceLevel;       // 冲刺参考/适中/稳妥参考/兜底参考
        private String confidenceLevel;   // 高/中/低/数据不足
        private double dataConfidence;    // 数据参考度 0-100
        private int predictedMinRank;     // 预测参考位次
        private int rankDiff;             // predictedMinRank - candidateRank
        // ── FallbackRulePredictionEngine 输入特征（10 维位次特征的关键 3 项），WRITE_ONLY 不外露 ──
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private double rankVolatility3y;  // 近三年位次波动系数
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private double planChangeRate;    // 招生计划同比变化率（负数=缩招）
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private double hotTrendScore;     // 专业热度趋势分（暂无数据源时为 0）
        // ── CandidateFilterEngine 命中拒绝原因，主列表通常为空，仅审计/调试用 ──
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private List<String> filterReasons;
        private String riskLevel;         // 低风险/中风险/高风险
        private String riskColor;         // green/yellow/red
        private int predictedRank;        // 预测下一年位次
        private String trend;             // 上升/稳定/下降
        // ── 意向匹配字段 ──
        private int matchScore;            // 匹配度 0-100
        private String matchTag;           // 专业匹配/地区匹配/双匹配/无
        private String schoolNature;       // 公办 / 民办 / 中外合作
        private String dataSourceType;     // 专业级 / 院校级
        private String confidenceLabel;    // 高可信 / 中可信 / 需复核
        private String recommendReason;    // 推荐原因
        private String riskReason;         // 风险提醒
        private String alternativeOption;  // 替代建议
        private String suitableFor;        // 适合哪类考生
        // ── 数据可信度证据链字段（V6.79 新增） ──
        /** 学校官网 */
        private String schoolOfficialUrl;
        /** 招生网入口 */
        private String admissionSiteUrl;
        /** 招生章程 */
        private String admissionBrochureUrl;
        /** 专业目录 */
        private String majorCatalogUrl;
        /** 收费标准入口 */
        private String tuitionInfoUrl;
        /** 选科要求来源链接（来自 data_major_requirement_gz.source_url） */
        private String requirementSourceUrl;
        /** 选科要求来源年份 */
        private Integer requirementSourceYear;
        /** 选科要求来源名称（如：贵州省招生考试院） */
        private String requirementSourceName;
        /** 近三次可用录取记录：专业级优先，缺失时回退院校级 */
        private List<HistoryRecord> historyRecords;
        /** 是否落在本次配置的梯度位次区间内 */
        private boolean withinConfiguredRange;
        private boolean outsideConfiguredRange;
        private String fillReason;
        /** 本条志愿与本次梯度区间的关系说明 */
        private String rangeNote;
        /** 是否需要人工复核 */
        private boolean needsManualReview;
        /** 触发人工复核的原因标签：missing_subject_requirement / inferred_subject_requirement /
         *  non_major_level / sino_foreign / private_school / military_police / medical_special /
         *  art_sports / cooperative / outdated_year */
        private List<String> reviewFlags;
        /** 参考匹配等级：较高 / 中等 / 偏低 / 需复核 */
        private String referenceFitLevel;
        /** 数据置信度分：0-100，只用于排序和解释，不代表任何结果承诺 */
        private int dataConfidenceScore;
        /** 本条推荐的结构化算法解释，供前端详情页和 AI 解读使用 */
        private String algorithmExplanation;
        /** 是否使用 2021-2023 文理科历史数据作为兼容参考 */
        private boolean legacySubjectFallback;
        /** 是否疑似专项、军警、艺术体育等特殊招生类型 */
        private boolean specialTypeFlag;
        /** 被排除或需复核的特殊类型原因 */
        private String excludedReason;
        /**
         * 招生类型枚举（recruit_type）：NORMAL / SPECIAL_PROGRAM / PRE_BATCH / MILITARY_POLICE /
         * ART_SPORTS / GENDER_RESTRICTED / FREE_NORMAL / DIRECTED / SUPPLEMENT。
         *
         * <p>由 {@link com.gzly.algorithm.RecruitTypeClassifier} 归一，主列表只保留 NORMAL；
         * 其它取值会在 PlanMetrics.recruitTypeBreakdown 中独立计数，便于报告口径下的回归与审计。</p>
         */
        private String recruitType;
    }

    /**
     * 强制人工复核清单条目。
     */
    @Data
    public static class ManualReviewItem {
        private int index;
        private String universityName;
        private String majorName;
        private String gradient;
        /** 中文复核原因 */
        private List<String> reasons;
        /** 推荐查阅的官方链接 */
        private List<String> evidenceLinks;
        /** 数据可信度标签 */
        private String confidenceLabel;
        /** 数据来源类型 */
        private String dataSourceType;
        /** 选科要求来源 */
        private String subjectRequirementSource;
    }

    /**
     * 单次方案生成的关键监控指标。
     */
    @Data
    public static class PlanMetrics {
        private int totalCount;
        private int chongCount;
        private int wenCount;
        private int baoCount;
        private int dianCount;
        private int missingRequirementCount;
        private int nonMajorLevelCount;
        private int manualReviewCount;
        private int legacyFallbackCount;
        private int specialExcludedCount;
        private int lowConfidenceCount;
        private int officialRequirementCount;
        private int expandedPlanCount;
        private int shrunkPlanCount;
        private int missingPlanIndexCount;
        private int highSupplyCount;
        private int lowSupplyCount;
        private int avgPrecisionScore;
        private double portfolioSafetyProbability;
        private String portfolioSafetyLevel;
        private String portfolioSafetyNote;
        private int safeTailCount;
        private int targetCount;
        private String provinceCode;
        private String volunteerUnitType;
        private long generationCostMs;
        private long generatedAtMs;
        // === 算法报告 P1 评估指标（V7.38 新增） ===
        /** 主列表中被归入非普通招生类型的条数。正常流程下应恒为 0，>0 表示规则前置被绕过。 */
        private int ruleViolationCount;
        /** ruleViolationCount / totalCount，对应报告中的 Rule Violation Rate。 */
        private double ruleViolationRate;
        /** 前 20 志愿中机会指数 < 35 的占比，对应报告中的 Over-Risk Exposure（中性默认 < 10%）。 */
        private double overRiskExposure;
        /** 前 20 志愿中机会指数 ≥ 75 的占比，作为 First-20 Hit Rate 的代理指标（无真实录取回流前的近似）。 */
        private double firstTwentyHitRate;
        /** 主列表中按 recruit_type 归一后的条数分布，键为 RecruitTypeClassifier 常量。 */
        private java.util.Map<String, Integer> recruitTypeBreakdown;
        /** 当前策略模式（保守型 / 均衡型 / 冲刺型），决定 overRisk/firstTwentyHit 的告警基线。 */
        private String strategyMode;
        /** overRiskExposure 的策略自适应基线（含 5% 容忍）；超过该值视为异常。 */
        private double overRiskExposureBaseline;
        /** firstTwentyHitRate 的策略自适应基线（含 5% 容忍）；低于该值视为异常。 */
        private double firstTwentyHitRateBaseline;
        /** overRiskExposure 是否突破当前策略基线，true 表示触发监控告警。 */
        private boolean overRiskExposureBreached;
        /** firstTwentyHitRate 是否低于当前策略基线，true 表示触发监控告警。 */
        private boolean firstTwentyHitRateBreached;
    }

    @Data
    public static class AdvisorAdvice {
        private String title;
        private String positioning;
        private String priorityAdvice;
        private String gradientAdvice;
        private String cityAdvice;
        private String majorAdvice;
        private String planChangeAdvice;
        private List<String> riskChecklist;
        private List<String> actionItems;
        private String sourceNote;
        private String sourceProjectName;
        private String sourceProjectUrl;
    }

    @Data
    public static class GradientRangeInput {
        /** 相对用户位次的起点偏移，负数代表更靠前名次 */
        private Integer rankOffsetMin;
        /** 相对用户位次的终点偏移，正数代表更靠后名次 */
        private Integer rankOffsetMax;
    }

    @Data
    public static class GradientRangeDetail {
        private String gradient;
        private int rankOffsetMin;
        private int rankOffsetMax;
        private int rankLow;
        private int rankHigh;
        private int targetCount;
        private int actualCount;
        private String label;
        /** 自适应比例下限，例如 0.65 表示当前位次的 65% */
        private Double rankRatioMin;
        /** 自适应比例上限，例如 0.95 表示当前位次的 95% */
        private Double rankRatioMax;
        /** 区间来源说明 */
        private String rangeSourceNote;
    }

    @Data
    public static class GradientRangeSummary {
        private String source;
        private String strategyMode;
        private String explanation;
        private Map<String, GradientRangeDetail> ranges;
    }

    @Data
    public static class HistoryRecord {
        private String provinceCode;
        private String groupCode;
        private Integer year;
        private Integer minScore;
        private Integer minRank;
        private Integer avgScore;
        private Integer maxScore;
        private Integer planCount;
        private String batch;
        private String subjectType;
        private String dataSourceType;
        private String confidenceLabel;
        private String rankSourceType;
        private String rankSourceNote;
        private Integer rankLow;
        private Integer rankHigh;
        private String rankSourceUrl;
        private String rankSourcePageUrl;
    }

    @Data
    public static class GenerateRequest {
        /** 省份代码：GZ=贵州（默认），SC=四川 */
        private String provinceCode;
        /** 招生年份；公共填报入口不填时使用当前激活招生年份 */
        private Integer year;
        /** 考生类别；不填默认普通类 */
        private String candidateType;
        /** 批次代码；不填默认普通类本科批 */
        private String batchCode;
        private int totalScore;
        private int provinceRank;
        private String firstSubject;      // 物理 | 历史
        private List<String> resubjects;  // 再选科目
        private String cardKey;
        private String safetyCode;
        private List<String> preferredMajors;  // 意向专业关键词，如 ["计算机","电子信息"]
        private List<String> preferredRegions; // 意向地区，如 ["北京","上海","四川"]
        private String strategyMode;           // 保守型 / 均衡型 / 冲刺型
        private String decisionPriority;       // 学校优先 / 专业优先
        private String careerGoal;             // 就业优先 / 升学优先 / 城市机会优先
        private String tuitionBudget;          // 低预算 / 均衡预算 / 不限制
        private Boolean acceptPrivate;         // 是否接受民办
        private Boolean acceptSinoForeign;     // 是否接受中外合作
        /** 新版公共接口别名字段 */
        private Integer score;
        private Integer rank;
        private String subjectType;
        private List<String> selectedSubjects;
        private String riskPreference;
        private List<String> dislikedMajors;
        private List<String> preferredCities;
        private List<String> preferredProvinces;
        private Integer maxTuition;
        private Boolean acceptPrivateSchool;
        private Boolean acceptChineseForeignCoop;
        private List<String> medicalLimitations;
        private Map<String, Integer> singleSubjectScores;
        /** 外语语种（英语/日语/俄语...），用于 CandidateFilterEngine 处理"只招英语"等限制 */
        private String foreignLanguage;
        /** 性别（男/女），用于 CandidateFilterEngine 处理"只招男生/只招女生"限制 */
        private String gender;
        /** 资格类标签（专项/民族班/预科/定向/免费医学/优师计划），用于硬规则放行 */
        private List<String> qualificationTags;
        private Integer artProfessionalScore;
        private Integer sportsProfessionalScore;
        private Double comprehensiveScore;
        private Double majorPriority;
        private Double schoolPriority;
        private Double cityPriority;
        private Double employmentPriority;
        /** 从 policy_rule_config 注入，避免推荐主链路写死 96 / 普通本科批 */
        private Integer policyMaxVolunteerCount;
        private String policyBatchName;
        private String policyVolunteerUnitType;
        private String policyVolunteerUnitLabel;
        private Boolean agreedDisclaimer;      // 是否已确认生成前风险告知
        private String disclaimerVersion;      // 风险告知版本
        /** 可选：冲/稳/保/垫相对位次区间，key 支持 chong/wen/bao/dian 或中文梯度 */
        private Map<String, GradientRangeInput> gradientRanges;
    }

    @Data
    public static class RankEstimateSummary {
        private String provinceCode;
        private String provinceName;
        private String subjectType;
        private int totalScore;
        private Integer submittedRank;
        private Integer effectiveRank;
        private Integer estimatedRank;
        private Integer rankLow;
        private Integer rankHigh;
        private Integer referenceYear;
        private boolean rankEstimated;
        private Boolean matched;
        private boolean officialDataReady;
        private String sourceName;
        private String sourceUrl;
        private String sourcePageUrl;
        private String parseMethod;
        private String note;
        private String reminder;
    }

    @Data
    public static class PlanResult {
        private long id;
        private String provinceCode;
        private String provinceName;
        private String volunteerUnitType;
        private String volunteerUnitLabel;
        private String targetBatch;
        private int targetCount;
        private int totalScore;
        private int provinceRank;
        private String firstSubject;
        private List<String> resubjects;
        private List<String> preferredMajors;
        private List<String> preferredRegions;
        private String strategyMode;
        private String decisionPriority;
        private String careerGoal;
        private String tuitionBudget;
        private Boolean acceptPrivate;
        private Boolean acceptSinoForeign;
        private String safetyCode;
        private String accessKey;
        private List<VolunteerItem> items;
        private String createdAt;
        private String dataQualityWarning;
        /** 强制人工复核清单 */
        private List<ManualReviewItem> manualReviewItems;
        /** 关键指标（前端可展示，运维可统计） */
        private PlanMetrics metrics;
        /** 全局机会指数口径与免责声明（前端用于覆盖默认文案） */
        private String referenceProbabilityNotice;
        /** 本次采用的梯度区间、解释与各梯度数量 */
        private GradientRangeSummary gradientRangeSummary;
        /** 分数到位次的官方一分一段估算与采用情况 */
        private RankEstimateSummary rankEstimate;
        /** 基于位次法、可行集、计划变化和偏好的报考顾问建议 */
        private AdvisorAdvice advisorAdvice;
        /** 新规范接口返回的年度政策摘要 */
        private Map<String, Object> policy;
        /** 新规范接口返回的模型信息 */
        private Map<String, Object> modelInfo;
        /** 新规范接口返回的警告列表 */
        private List<String> warnings;
        private int activeAdmissionYear;
        private int latestOfficialDataYear;
        private int targetYear;
        private int futureImportYear;
        private List<Integer> trainingYears;
        private List<Integer> dataSourceYears;
        private String recommendationPhase;
        private boolean estimateMode;
        private boolean officialDataReady;
        private BatchSupportService.DataReadiness dataReadiness;
        private String supportLevel;
        private String recommendMode;
        private String engineName;
        private String supportReason;
        /**
         * VolunteerDiagnosisEngine 13 维诊断输出：totalCount / policyMaxCount / gradientCount /
         * overallRisk / summary / diagnosis / warnings / longestHighRiskRun / eliteCount。
         * 旧 biz_plan_history.plan_json 没有这个字段时 Jackson 默认会留空，反序列化不报错。
         */
        private Map<String, Object> diagnosis;
    }

    private record ResolvedGradientRanges(Map<String, GradientRangeDetail> details,
                                          GradientRangeSummary summary) {
        GradientRangeDetail get(String gradient) {
            return details.get(gradient);
        }
    }

    private record GradientCounts(int chong, int wen, int bao, int dian) {
        int count(String gradient) {
            return switch (gradient) {
                case "冲" -> chong;
                case "稳" -> wen;
                case "保" -> bao;
                case "垫" -> dian;
                default -> 0;
            };
        }
    }

    private record PortfolioSafety(double probability, String level, String note, int safeTailCount) {
    }

    @Data
    private static class GenerationStats {
        private int specialExcludedCount;
        private int neighborGradientBackfillCount;

        void incrementSpecialExcluded() {
            specialExcludedCount++;
        }

        void addNeighborGradientBackfill(int count) {
            neighborGradientBackfillCount += Math.max(0, count);
        }
    }

    private record RatioRange(double min, double max) {
    }

    private record RankWindow(int low, int high) {
    }

    private record PlanSignal(Integer latestPlanCount, String trend, String note, double scoreAdjustment,
                              double expansionIndex, String expansionLabel, String expansionNote,
                              double supplyIndex, String supplyLabel, String supplyNote) {
    }

    private record RankResolution(Integer submittedRank, int effectiveRank, RankEstimateSummary summary) {
    }

    @Data
    private static class PreferenceProfile {
        private List<String> preferredMajors = List.of();
        private List<String> preferredRegions = List.of();
        private String strategyMode = "均衡型";
        private String decisionPriority = "专业优先";
        private String careerGoal = "就业优先";
        private String tuitionBudget = "均衡预算";
        private boolean acceptPrivate = true;
        private boolean acceptSinoForeign = false;
        /** 排序权重（VolunteerSortEngine 输入），未传时由 fromStrategyAndProfile 用默认值兜底 */
        private Double majorPriority;
        private Double schoolPriority;
        private Double cityPriority;
        private Double employmentPriority;
    }

    @Data
    private static class RequirementResolution {
        private String requirement = "";
        private String source = "missing";
        /** 选科要求来源年份（仅 official_requirement 命中时填写） */
        private Integer year;
        /** 选科要求来源链接（仅 official_requirement 命中时填写） */
        private String sourceUrl;
        /** 选科要求来源名称 */
        private String sourceName;
    }

    /**
     * 默认的机会指数/免责声明文案。
     * 前端在结果页/AI 解读页统一展示，避免任何承诺性措辞。
     */
    private static final String REFERENCE_PROBABILITY_NOTICE = ComplianceConstants.REFERENCE_PROBABILITY_NOTICE;

    /**
     * 生成 96 志愿方案
     */
    @Transactional(rollbackFor = Exception.class)
    public PlanResult generate(GenerateRequest req, Long userId, String clientIp) {
        validateGenerateRequest(req);
        String provinceCode = normalizeProvinceCode(req.getProvinceCode());
        String subjectType = mapSubjectType(req.getFirstSubject());
        RankResolution rankResolution = resolveRankResolution(provinceCode, req, subjectType);
        int R = rankResolution.effectiveRank();
        req.setProvinceRank(R);
        boolean requestedSafetyCode = req.getSafetyCode() != null && !req.getSafetyCode().isBlank();
        String submittedSafetyCode = requestedSafetyCode ? safetyCodeService.normalizeSafetyCode(req.getSafetyCode()) : "";
        String fingerprint = buildGenerateFingerprint(req, userId, clientIp);
        if (!requestedSafetyCode) {
            fingerprint = fingerprint + ":" + UUID.randomUUID();
        }
        String resultKey = "gzly:v7:generate:result:" + fingerprint;
        String lockKey = "gzly:v7:generate:lock:" + fingerprint;

        if (requestedSafetyCode) {
            String cachedPayload = stringRedisTemplate.opsForValue().get(resultKey);
            if (cachedPayload != null && !cachedPayload.isBlank()) {
                return readCachedPlanResult(cachedPayload, submittedSafetyCode);
            }
        }

        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(generateLockSeconds));
        if (Boolean.FALSE.equals(acquired)) {
            PlanResult waiting = requestedSafetyCode ? waitForGenerateResult(resultKey, submittedSafetyCode) : null;
            if (waiting != null) {
                return waiting;
            }
            throw new BizException("相同请求正在处理中，请稍后再试");
        }

        long startedAt = System.currentTimeMillis();
        metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_TOTAL);
        boolean success = false;
        try {
        if (userId != null && userId > 0) {
            bizUserMapper.update(null, new LambdaUpdateWrapper<BizUser>()
                    .eq(BizUser::getId, userId)
                    .setSql("total_used = total_used + 1")
                    .set(BizUser::getLastActiveTime, LocalDateTime.now()));
        }

        PreferenceProfile profile = toPreferenceProfile(req);
        int policyTargetCount = policyTargetCount(req);
        String policyUnitType = policyVolunteerUnitType(req);
        String policyUnitLabel = policyVolunteerUnitLabel(req);
        String policyBatchName = policyBatchName(req);
        GradientCounts targetCounts = targetCounts(profile.getStrategyMode(), policyTargetCount);
        ResolvedGradientRanges resolvedRanges = resolveGradientRanges(req, R, profile, policyTargetCount);
        GenerationStats generationStats = new GenerationStats();

        // 2. 构建硬规则过滤条件（CandidateFilterEngine 16 维硬规则的承载体）
        CandidateFilterEngine.FilterCriteria filterCriteria = buildFilterCriteria(req, provinceCode, subjectType);

        // 3. 按梯度查询候选（所有区间均以用户手填官方位次为基准）
        List<VolunteerItem> chongItems = pickGradient("冲", subjectType,
                resolvedRanges.get("冲").getRankLow(), resolvedRanges.get("冲").getRankHigh(),
                req.getResubjects(), targetCounts.chong(), profile, generationStats, filterCriteria);
        List<VolunteerItem> wenItems = pickGradient("稳", subjectType,
                resolvedRanges.get("稳").getRankLow(), resolvedRanges.get("稳").getRankHigh(),
                req.getResubjects(), targetCounts.wen(), profile, generationStats, filterCriteria);
        List<VolunteerItem> baoItems = pickGradient("保", subjectType,
                resolvedRanges.get("保").getRankLow(), resolvedRanges.get("保").getRankHigh(),
                req.getResubjects(), targetCounts.bao(), profile, generationStats, filterCriteria);
        List<VolunteerItem> dianItems = pickGradient("垫", subjectType,
                resolvedRanges.get("垫").getRankLow(), resolvedRanges.get("垫").getRankHigh(),
                req.getResubjects(), targetCounts.dian(), profile, generationStats, filterCriteria);
        List<List<VolunteerItem>> gradientItemGroups = List.of(chongItems, wenItems, baoItems, dianItems);
        backfillGradientFromNeighbors("冲", chongItems, targetCounts.chong(), List.of("稳"),
                resolvedRanges, subjectType, req.getResubjects(), profile, generationStats, filterCriteria,
                gradientItemGroups);
        backfillGradientFromNeighbors("稳", wenItems, targetCounts.wen(), List.of("冲", "保"),
                resolvedRanges, subjectType, req.getResubjects(), profile, generationStats, filterCriteria,
                gradientItemGroups);
        backfillGradientFromNeighbors("保", baoItems, targetCounts.bao(), List.of("稳", "垫"),
                resolvedRanges, subjectType, req.getResubjects(), profile, generationStats, filterCriteria,
                gradientItemGroups);
        backfillGradientFromNeighbors("垫", dianItems, targetCounts.dian(), List.of("保"),
                resolvedRanges, subjectType, req.getResubjects(), profile, generationStats, filterCriteria,
                gradientItemGroups);
        applyRangeContext(chongItems, resolvedRanges.get("冲"));
        applyRangeContext(wenItems, resolvedRanges.get("稳"));
        applyRangeContext(baoItems, resolvedRanges.get("保"));
        applyRangeContext(dianItems, resolvedRanges.get("垫"));
        applyRangeCounts(resolvedRanges, chongItems, wenItems, baoItems, dianItems);
        appendRangeSummaryQualityNote(resolvedRanges.summary(), generationStats);

        // 3. 合并并编号
        List<VolunteerItem> allItems = new ArrayList<>();
        allItems.addAll(chongItems);
        allItems.addAll(wenItems);
        allItems.addAll(baoItems);
        allItems.addAll(dianItems);

        // 按历年最低位次从小到大排序（位次越小=录取难度越高=排在前面）
        allItems.sort(Comparator.comparingInt(VolunteerItem::getHistoryMinRank));

        for (int i = 0; i < allItems.size(); i++) {
            allItems.get(i).setIndex(i + 1);
        }

        // ── 算法增强: 为每个志愿计算机会指数、风险、预测 ──
        enrichWithAlgorithms(allItems, R, subjectType);

        // ── 意向匹配打分 ──
        List<String> prefMajors = profile.getPreferredMajors();
        List<String> prefRegions = profile.getPreferredRegions();
        if (!prefMajors.isEmpty() || !prefRegions.isEmpty()) {
            enrichWithPreference(allItems, prefMajors, prefRegions);
        }
        enrichWithHistoryRecords(allItems, subjectType);
        enrichWithPlanAndRankSignals(allItems, R);

        // ── 证据链补全：批量加载招生章程/专业目录/收费链接，并打上复核标签 ──
        enrichWithEvidence(allItems);
        applyReliabilityExplanations(allItems, R);
        enrichWithDecisionSupport(allItems, profile);
        applyPlanOrdering(allItems, profile);
        normalizePublicChanceFields(allItems, R);
        PortfolioSafety portfolioSafety = assessPortfolioSafety(allItems);

        // ── 强制人工复核清单 + 监控指标 ──
        List<ManualReviewItem> manualReviewItems = buildManualReviewList(allItems);
        String dataQualityWarning = buildDataQualityWarning(req, subjectType, allItems, rankResolution.summary());
        dataQualityWarning = appendNeighborBackfillWarning(dataQualityWarning, generationStats);
        dataQualityWarning = appendPortfolioSafetyWarning(dataQualityWarning, portfolioSafety);
        PlanMetrics metrics = buildPlanMetrics(allItems, manualReviewItems,
                generationStats.getSpecialExcludedCount(), portfolioSafety,
                policyTargetCount, provinceCode, policyUnitType, profile.getStrategyMode());

        // 4. 保存记录
        PlanHistory history = new PlanHistory();
        history.setUserId(userId != null ? userId : 0L);
        history.setClientIp(clientIp);
        history.setProvinceCode(provinceCode);
        history.setVolunteerUnitType(policyUnitType);
        history.setTargetBatch(policyBatchName);
        history.setAgreedDisclaimer(Boolean.TRUE.equals(req.getAgreedDisclaimer()) ? 1 : 0);
        history.setDisclaimerVersion(req.getDisclaimerVersion());
        history.setDisclaimerConfirmedAt(LocalDateTime.now());
        history.setTotalScore(req.getTotalScore());
        history.setProvinceRank(R);
        history.setFirstSubject(req.getFirstSubject() != null ? req.getFirstSubject() : "");
        history.setStrategyMode(profile.getStrategyMode());
        history.setDecisionPriority(profile.getDecisionPriority());
        history.setCareerGoal(profile.getCareerGoal());
        history.setTuitionBudget(profile.getTuitionBudget());
        history.setAcceptPrivate(profile.isAcceptPrivate() ? 1 : 0);
        history.setAcceptSinoForeign(profile.isAcceptSinoForeign() ? 1 : 0);
        history.setDataQualityWarning(dataQualityWarning);
        SafetyCodeService.SafetyCodeIssue safetyCodeIssue = safetyCodeService.issue(req.getSafetyCode());
        history.setSafetyCodeHash(safetyCodeIssue.safetyCodeHash());
        history.setSafetyCodeCreatedAt(LocalDateTime.now());
        history.setSafetyCodeVersion(1);
        try {
            history.setResubjects(objectMapper.writeValueAsString(req.getResubjects()));
            history.setPreferredMajors(objectMapper.writeValueAsString(prefMajors));
            history.setPreferredRegions(objectMapper.writeValueAsString(prefRegions));
            history.setPlanJson(objectMapper.writeValueAsString(allItems));
            history.setManualReviewJson(objectMapper.writeValueAsString(manualReviewItems));
            history.setRequestSnapshotJson(objectMapper.writeValueAsString(
                    buildRequestSnapshot(req, profile, resolvedRanges.summary(), rankResolution.summary())));
        } catch (JsonProcessingException e) {
            throw new BizException("序列化方案失败");
        }
        history.setItemCount(allItems.size());
        history.setCreatedAt(LocalDateTime.now());

        // 写入指标 + 完成保存
        long generationCostMs = System.currentTimeMillis() - startedAt;
        metrics.setGenerationCostMs(generationCostMs);
        metrics.setGeneratedAtMs(System.currentTimeMillis());
        try {
            history.setMetricsJson(objectMapper.writeValueAsString(metrics));
        } catch (JsonProcessingException ignored) {
            history.setMetricsJson(null);
        }
        planHistoryMapper.insert(history);

        // 5. 返回结果
        PlanResult result = new PlanResult();
        result.setId(history.getId());
        result.setProvinceCode(provinceCode);
        result.setProvinceName("贵州");
        result.setVolunteerUnitType(policyUnitType);
        result.setVolunteerUnitLabel(policyUnitLabel);
        result.setTargetBatch(policyBatchName);
        result.setTargetCount(policyTargetCount);
        result.setTotalScore(req.getTotalScore());
        result.setProvinceRank(R);
        result.setFirstSubject(req.getFirstSubject());
        result.setResubjects(req.getResubjects());
        result.setPreferredMajors(prefMajors);
        result.setPreferredRegions(prefRegions);
        result.setStrategyMode(profile.getStrategyMode());
        result.setDecisionPriority(profile.getDecisionPriority());
        result.setCareerGoal(profile.getCareerGoal());
        result.setTuitionBudget(profile.getTuitionBudget());
        result.setAcceptPrivate(profile.isAcceptPrivate());
        result.setAcceptSinoForeign(profile.isAcceptSinoForeign());
        result.setSafetyCode(safetyCodeIssue.safetyCode());
        result.setAccessKey(safetyCodeIssue.safetyCode());
        result.setItems(allItems);
        result.setCreatedAt(history.getCreatedAt().toString());
        result.setDataQualityWarning(dataQualityWarning);
        result.setManualReviewItems(manualReviewItems);
        result.setMetrics(metrics);
        result.setReferenceProbabilityNotice(REFERENCE_PROBABILITY_NOTICE);
        result.setGradientRangeSummary(resolvedRanges.summary());
        result.setRankEstimate(rankResolution.summary());
        result.setAdvisorAdvice(buildAdvisorAdvice(result));

        // 13 维方案级诊断（VolunteerDiagnosisEngine）
        VolunteerDiagnosisEngine.DiagnosisContext diagnosisCtx = new VolunteerDiagnosisEngine.DiagnosisContext();
        diagnosisCtx.setDislikedMajors(req.getDislikedMajors() == null ? List.of() : req.getDislikedMajors());
        diagnosisCtx.setRiskPreference(profile.getStrategyMode());
        try {
            Map<String, Object> diagnosis = volunteerDiagnosisEngine.diagnose(allItems, policyTargetCount, diagnosisCtx);
            result.setDiagnosis(diagnosis);
        } catch (Exception ex) {
            log.warn("VolunteerDiagnosisEngine 诊断失败 planId={}: {}", history.getId(), ex.getMessage());
            result.setDiagnosis(Map.of(
                    "totalCount", allItems.size(),
                    "policyMaxCount", policyTargetCount,
                    "summary", "方案诊断暂不可用",
                    "warnings", List.of()));
        }

        // 监控统计
        metricsRecorder.recordCost(generationCostMs);
        if (allItems.size() < policyTargetCount) {
            metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_INCOMPLETE);
        }
        if (dataQualityWarning != null && !dataQualityWarning.isBlank()) {
            metricsRecorder.incr(VolunteerMetricsRecorder.DATA_QUALITY_WARNING_TRIGGERED);
        }
        if (manualReviewItems != null && !manualReviewItems.isEmpty()) {
            metricsRecorder.incr(VolunteerMetricsRecorder.MANUAL_REVIEW_TRIGGERED);
        }
        // === 算法报告 P1 评估指标 ===
        metricsRecorder.incr(VolunteerMetricsRecorder.RULE_VIOLATION_SAMPLE_TOTAL);
        if (metrics != null) {
            if (metrics.getRuleViolationCount() > 0) {
                metricsRecorder.incr(VolunteerMetricsRecorder.RULE_VIOLATION_TRIGGERED);
            }
            // 自适应阈值：基线由 strategyMode 决定（resolveStrategyThresholds），
            // 仅在确实超出当前策略容忍区间时上报，避免冲刺型策略下监控持续误报。
            if (metrics.isOverRiskExposureBreached()) {
                metricsRecorder.incr(VolunteerMetricsRecorder.OVER_RISK_EXPOSURE_TRIGGERED);
            }
            if (metrics.isFirstTwentyHitRateBreached()) {
                metricsRecorder.incr(VolunteerMetricsRecorder.FIRST_TWENTY_HIT_RATE_LOW);
            }
        }

        log.info("生成志愿方案: userId={}, rank={}, items={}/{}, costMs={}, manualReview={}",
                userId, R, allItems.size(), policyTargetCount, generationCostMs,
                manualReviewItems == null ? 0 : manualReviewItems.size());
        if (requestedSafetyCode) {
            cachePlanResult(resultKey, result);
        }
        success = true;
        return result;
        } finally {
            if (success) {
                metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_SUCCESS);
            } else {
                metricsRecorder.incr(VolunteerMetricsRecorder.GENERATE_FAILURE);
            }
            releaseGenerateLock(lockKey, lockValue);
        }
    }

    private String buildDataQualityWarning(GenerateRequest req, String subjectType, List<VolunteerItem> items,
                                           RankEstimateSummary rankEstimate) {
        List<String> warnings = new ArrayList<>();
        int itemCount = items != null ? items.size() : 0;
        int target = policyTargetCount(req);
        if (itemCount < target) {
            warnings.add(String.format(
                    "当前条件下只匹配到%d个志愿项，未达到%d个。建议放宽偏好条件，或等待专业级数据继续补全后重新生成。",
                    itemCount, target));
        }
        String subjectWarning = buildSubjectRequirementWarning(req, items);
        if (!subjectWarning.isBlank()) {
            warnings.add(subjectWarning);
        }
        String rankWarning = buildScoreRankWarning(req, subjectType, rankEstimate);
        if (!rankWarning.isBlank()) {
            warnings.add(rankWarning);
        }
        return warnings.isEmpty() ? null : String.join(" ", warnings);
    }

    private String appendNeighborBackfillWarning(String warning, GenerationStats stats) {
        if (stats == null || stats.getNeighborGradientBackfillCount() <= 0) {
            return warning;
        }
        String note = String.format("部分梯度候选不足，已从相邻梯度参考区间补充%d个志愿项，并在条目中标记补位来源。",
                stats.getNeighborGradientBackfillCount());
        if (warning == null || warning.isBlank()) {
            return note;
        }
        return warning + " " + note;
    }

    private String buildSubjectRequirementWarning(GenerateRequest req, List<VolunteerItem> items) {
        if (req == null || req.getResubjects() == null || req.getResubjects().isEmpty()
                || items == null || items.isEmpty()) {
            return "";
        }
        long uncheckedCount = items.stream()
                .filter(item -> item.getSubjectRequirementSource() == null
                        || item.getSubjectRequirementSource().isBlank()
                        || "missing".equals(item.getSubjectRequirementSource())
                        || "inferred".equals(item.getSubjectRequirementSource()))
                .count();
        if (uncheckedCount == 0) {
            return "";
        }
        return String.format(
                "选科要求数据仍需复核：当前方案中%d个志愿项未拿到官方再选科目要求或仅按专业名粗筛，医学、工学、师范、公安军警等专业必须以考试院和学校招生章程为准。",
                uncheckedCount);
    }

    private String buildScoreRankWarning(GenerateRequest req, String subjectType, RankEstimateSummary rankEstimate) {
        if (rankEstimate == null || req.getTotalScore() <= 0) {
            return "";
        }
        if (rankEstimate.isRankEstimated()) {
            return String.format(
                    "未手填全省位次，系统本次按%d分在%d年%s官方一分一段表的保守位次第%,d名建立梯度；正式填报前必须以考试院原表再次核对。",
                    req.getTotalScore(),
                    rankEstimate.getReferenceYear() == null ? 0 : rankEstimate.getReferenceYear(),
                    subjectType,
                    rankEstimate.getEffectiveRank() == null ? 0 : rankEstimate.getEffectiveRank());
        }
        if (Boolean.FALSE.equals(rankEstimate.getMatched())) {
            return String.format(
                    "分数与位次可能不一致：%d分按%d年%s官方一分一段表对应位次区间约%,d-%,d名，你填写的是%,d名。系统仍按你填写的位次生成，请以考试院原表为准。",
                    req.getTotalScore(),
                    rankEstimate.getReferenceYear() == null ? 0 : rankEstimate.getReferenceYear(),
                    subjectType,
                    rankEstimate.getRankLow() == null ? 0 : rankEstimate.getRankLow(),
                    rankEstimate.getRankHigh() == null ? 0 : rankEstimate.getRankHigh(),
                    rankEstimate.getSubmittedRank() == null ? 0 : rankEstimate.getSubmittedRank());
        }
        return "";
    }

    private RankResolution resolveRankResolution(String provinceCode, GenerateRequest req, String subjectType) {
        int submittedRank = req.getProvinceRank();
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(provinceCode);
        AlgorithmService.RankEstimate estimate = null;
        if (provinceRankService != null) {
            estimate = provinceRankService.estimateRank(provinceCode, req.getTotalScore(), subjectType, null);
        }

        if (submittedRank > 0) {
            RankEstimateSummary summary = buildRankEstimateSummary(policy, req, subjectType, estimate, submittedRank, false);
            return new RankResolution(submittedRank, submittedRank, summary);
        }

        if (estimate == null || estimate.getDataPoints() < 1 || estimate.getEstimatedRank() <= 0) {
            throw new BizException(String.format(
                    "未填写全省位次，且%s%s官方一分一段表暂不可用，无法按分数估算位次；请手动填写考试院确认的全省位次。",
                    policy.getProvinceName(), subjectType));
        }

        int effectiveRank = Math.max(1, estimate.getEstimatedRank());
        RankEstimateSummary summary = buildRankEstimateSummary(policy, req, subjectType, estimate, effectiveRank, true);
        return new RankResolution(null, effectiveRank, summary);
    }

    private RankEstimateSummary buildRankEstimateSummary(ProvincePolicyService.ProvincePolicy policy,
                                                         GenerateRequest req,
                                                         String subjectType,
                                                         AlgorithmService.RankEstimate estimate,
                                                         int effectiveRank,
                                                         boolean rankEstimated) {
        RankEstimateSummary summary = new RankEstimateSummary();
        summary.setProvinceCode(policy.getProvinceCode());
        summary.setProvinceName(policy.getProvinceName());
        summary.setSubjectType(subjectType);
        summary.setTotalScore(req.getTotalScore());
        summary.setSubmittedRank(req.getProvinceRank() > 0 ? req.getProvinceRank() : null);
        summary.setEffectiveRank(effectiveRank);
        summary.setRankEstimated(rankEstimated);
        if (estimate != null) {
            summary.setEstimatedRank(estimate.getEstimatedRank() > 0 ? estimate.getEstimatedRank() : null);
            summary.setRankLow(estimate.getRankLow() > 0 ? estimate.getRankLow() : null);
            summary.setRankHigh(estimate.getRankHigh() > 0 ? estimate.getRankHigh() : null);
            summary.setReferenceYear(estimate.getReferenceYear() > 0 ? estimate.getReferenceYear() : null);
            summary.setOfficialDataReady(estimate.getDataPoints() > 0);
            summary.setSourceName(estimate.getSource());
            summary.setSourceUrl(estimate.getSourceUrl());
            summary.setSourcePageUrl(estimate.getSourcePageUrl());
            summary.setParseMethod(estimate.getParseMethod());
            summary.setNote(estimate.getNote());
            if (req.getProvinceRank() > 0 && estimate.getDataPoints() > 0) {
                summary.setMatched(isRankMatched(req.getProvinceRank(), estimate));
            }
        }
        if (rankEstimated) {
            summary.setReminder(String.format(
                    "未手填全省位次，本次采用官方同分区间保守位次第%,d名生成；正式填报必须以%s原始一分一段表和成绩单核对。",
                    effectiveRank, policy.getOfficialSourceName()));
        } else {
            summary.setReminder(String.format(
                    "本次按你填写的全省位次第%,d名生成；分数位次校验只作一致性提醒，最终以%s为准。",
                    effectiveRank, policy.getOfficialSourceName()));
        }
        return summary;
    }

    private boolean isRankMatched(int submittedRank, AlgorithmService.RankEstimate estimate) {
        int low = Math.max(1, estimate.getRankLow());
        int high = Math.max(low, estimate.getRankHigh());
        return submittedRank >= low && submittedRank <= high;
    }

    private void validateGenerateRequest(GenerateRequest req) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        if (req.getTotalScore() <= 0 || req.getTotalScore() > 750) {
            throw new BizException("高考总分必须在1-750之间");
        }
        if (!"物理".equals(req.getFirstSubject()) && !"历史".equals(req.getFirstSubject())) {
            throw new BizException("首选科目必须为物理或历史");
        }
        if (req.getResubjects() == null || req.getResubjects().size() != 2) {
            throw new BizException("再选科目必须选择2门");
        }
        if (!Boolean.TRUE.equals(req.getAgreedDisclaimer())) {
            throw new BizException(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
        }
        if (!ComplianceConstants.DISCLAIMER_VERSION.equals(safeTrim(req.getDisclaimerVersion()))) {
            throw new BizException(ComplianceConstants.DISCLAIMER_CONFIRM_ERROR);
        }
    }

    private ResolvedGradientRanges resolveGradientRanges(GenerateRequest req, int provinceRank,
                                                         PreferenceProfile profile, int policyTargetCount) {
        Map<String, GradientRangeInput> inputs = defaultGradientInputs(profile.getStrategyMode());
        boolean custom = req.getGradientRanges() != null && !req.getGradientRanges().isEmpty();
        if (custom) {
            for (Map.Entry<String, GradientRangeInput> entry : req.getGradientRanges().entrySet()) {
                String gradient = normalizeGradientKey(entry.getKey());
                if (gradient.isBlank()) {
                    throw new BizException("梯度区间包含未知梯度，请使用冲/稳/保/垫");
                }
                GradientRangeInput input = entry.getValue();
                if (input == null) {
                    throw new BizException("梯度区间不能为空");
                }
                inputs.put(gradient, input);
            }
        }

        validateGradientInputs(inputs);
        Map<String, GradientRangeDetail> details = new LinkedHashMap<>();
        Map<String, RatioRange> ratioRanges = defaultRatioRanges(profile.getStrategyMode());
        for (String gradient : GRADIENT_ORDER) {
            GradientRangeInput input = inputs.get(gradient);
            RatioRange ratio = ratioRanges.get(gradient);
            int absoluteLow = Math.max(1, provinceRank + input.getRankOffsetMin());
            int absoluteHigh = Math.max(absoluteLow, provinceRank + input.getRankOffsetMax());
            int ratioLow = ratio == null ? absoluteLow : Math.max(1, (int) Math.round(provinceRank * ratio.min()));
            int ratioHigh = ratio == null ? absoluteHigh : Math.max(ratioLow, (int) Math.round(provinceRank * ratio.max()));

            int rankLow;
            int rankHigh;
            String rangeSourceNote;
            if (provinceRank <= 20_000 && !custom) {
                rankLow = ratioLow;
                rankHigh = ratioHigh;
                rangeSourceNote = "高分段采用位次比例区间，避免冲档过宽。";
            } else {
                rankLow = Math.max(absoluteLow, ratioLow);
                rankHigh = Math.min(absoluteHigh, ratioHigh);
                int minWindowWidth = minGradientWindowWidth(req, provinceRank);
                if (rankHigh < rankLow) {
                    rankLow = ratioLow;
                    rankHigh = ratioHigh;
                    rangeSourceNote = "绝对偏移与比例区间无交集，已回退为比例区间。";
                } else if (rankHigh - rankLow < minWindowWidth) {
                    RankWindow expanded = expandNarrowGradientWindow(rankLow, rankHigh, minWindowWidth);
                    rankLow = expanded.low();
                    rankHigh = expanded.high();
                    rangeSourceNote = String.format("绝对偏移与比例区间交集过窄，已按最小窗口%,d位自动扩展。", minWindowWidth);
                } else {
                    rangeSourceNote = "由绝对偏移区间与位次比例区间共同约束。";
                }
            }

            GradientRangeDetail detail = new GradientRangeDetail();
            detail.setGradient(gradient);
            detail.setRankOffsetMin(input.getRankOffsetMin());
            detail.setRankOffsetMax(input.getRankOffsetMax());
            detail.setRankLow(rankLow);
            detail.setRankHigh(rankHigh);
            detail.setTargetCount(targetCount(gradient, profile.getStrategyMode(), policyTargetCount));
            if (ratio != null) {
                detail.setRankRatioMin(ratio.min());
                detail.setRankRatioMax(ratio.max());
            }
            detail.setRangeSourceNote(rangeSourceNote);
            detail.setLabel(String.format("%s：第%,d ~ %,d位", gradient, detail.getRankLow(), detail.getRankHigh()));
            details.put(gradient, detail);
        }

        GradientRangeSummary summary = new GradientRangeSummary();
        summary.setSource(custom ? "custom" : "preset");
        summary.setStrategyMode(profile.getStrategyMode());
        summary.setRanges(details);
        summary.setExplanation("系统按你手填的官方全省位次，同时使用绝对偏移和位次比例约束冲、稳、保、垫（兜底）区间；默认配额采用偏稳健的冲/稳/保/兜底比例，再结合选科、专业级数据、偏好和复核规则生成志愿草稿。参考匹配不是录取承诺。");
        return new ResolvedGradientRanges(details, summary);
    }

    @SuppressWarnings("unused") // 兼容既有反射单测和旧内部调用入口。
    private ResolvedGradientRanges resolveGradientRanges(GenerateRequest req, int provinceRank,
                                                         PreferenceProfile profile) {
        return resolveGradientRanges(req, provinceRank, profile, policyTargetCount(req));
    }

    private int minGradientWindowWidth(GenerateRequest req, int provinceRank) {
        if (isSpecialtyBatch(req)) {
            return Math.max(2_000, (int) Math.round(provinceRank * 0.03D));
        }
        return Math.max(1_000, (int) Math.round(provinceRank * 0.02D));
    }

    private boolean isSpecialtyBatch(GenerateRequest req) {
        String batchCode = safeTrim(req == null ? null : req.getBatchCode());
        String batchName = safeTrim(req == null ? null : req.getPolicyBatchName());
        String unitType = safeTrim(req == null ? null : req.getPolicyVolunteerUnitType());
        return "NORMAL_SPECIALTY".equals(batchCode)
                || batchCode.contains("SPECIALTY")
                || batchName.contains("专科")
                || "MAJOR_96".equals(unitType) && batchName.contains("高职");
    }

    private RankWindow expandNarrowGradientWindow(int rankLow, int rankHigh, int minWindowWidth) {
        int low = Math.max(1, rankLow);
        int high = Math.max(low, rankHigh);
        int width = Math.max(1, minWindowWidth);
        int center = low + Math.max(0, high - low) / 2;
        int left = width / 2;
        int expandedLow = Math.max(1, center - left);
        int expandedHigh = expandedLow + width;
        if (expandedHigh < center) {
            expandedHigh = Integer.MAX_VALUE;
        }
        return new RankWindow(expandedLow, expandedHigh);
    }

    private Map<String, RatioRange> defaultRatioRanges(String strategyMode) {
        Map<String, RatioRange> ranges = new LinkedHashMap<>();
        if ("保守型".equals(strategyMode)) {
            putRatio(ranges, "冲", 0.75, 0.95);
            putRatio(ranges, "稳", 0.95, 1.25);
            putRatio(ranges, "保", 1.25, 2.00);
            putRatio(ranges, "垫", 2.00, 3.50);
            return ranges;
        }
        if ("冲刺型".equals(strategyMode)) {
            putRatio(ranges, "冲", 0.50, 0.95);
            putRatio(ranges, "稳", 0.95, 1.15);
            putRatio(ranges, "保", 1.15, 1.65);
            putRatio(ranges, "垫", 1.65, 2.80);
            return ranges;
        }
        putRatio(ranges, "冲", 0.65, 0.95);
        putRatio(ranges, "稳", 0.95, 1.20);
        putRatio(ranges, "保", 1.20, 1.80);
        putRatio(ranges, "垫", 1.80, 3.00);
        return ranges;
    }

    private void putRatio(Map<String, RatioRange> ranges, String gradient, double min, double max) {
        ranges.put(gradient, new RatioRange(min, max));
    }

    private Map<String, GradientRangeInput> defaultGradientInputs(String strategyMode) {
        Map<String, GradientRangeInput> ranges = new LinkedHashMap<>();
        if ("保守型".equals(strategyMode)) {
            putRange(ranges, "冲", -8000, -3000);
            putRange(ranges, "稳", -3000, 5000);
            putRange(ranges, "保", 5000, 15000);
            putRange(ranges, "垫", 15000, 30000);
            return ranges;
        }
        if ("冲刺型".equals(strategyMode)) {
            putRange(ranges, "冲", -15000, -3000);
            putRange(ranges, "稳", -3000, 3000);
            putRange(ranges, "保", 3000, 10000);
            putRange(ranges, "垫", 10000, 25000);
            return ranges;
        }
        putRange(ranges, "冲", -10000, -3000);
        putRange(ranges, "稳", -3000, 4000);
        putRange(ranges, "保", 4000, 12000);
        putRange(ranges, "垫", 12000, 26000);
        return ranges;
    }

    private void putRange(Map<String, GradientRangeInput> ranges, String gradient, int min, int max) {
        GradientRangeInput input = new GradientRangeInput();
        input.setRankOffsetMin(min);
        input.setRankOffsetMax(max);
        ranges.put(gradient, input);
    }

    private void validateGradientInputs(Map<String, GradientRangeInput> inputs) {
        for (String gradient : GRADIENT_ORDER) {
            GradientRangeInput input = inputs.get(gradient);
            if (input == null || input.getRankOffsetMin() == null || input.getRankOffsetMax() == null) {
                throw new BizException(String.format("%s档位次区间必须填写起止偏移", gradient));
            }
            int min = input.getRankOffsetMin();
            int max = input.getRankOffsetMax();
            if (min > max) {
                throw new BizException(String.format("%s档位次区间起点不能大于终点", gradient));
            }
            if (min < MAX_NEGATIVE_OFFSET || max > MAX_POSITIVE_OFFSET) {
                throw new BizException(String.format("%s档位次区间超出允许范围", gradient));
            }
        }
        if (inputs.get("冲").getRankOffsetMax() >= 0) {
            throw new BizException("冲档区间必须整体位于当前位次之前，不能跨到更低位次");
        }
        if (inputs.get("稳").getRankOffsetMin() > 0 || inputs.get("稳").getRankOffsetMax() < 0) {
            throw new BizException("稳档区间必须覆盖当前位次附近");
        }
        if (inputs.get("保").getRankOffsetMin() < 0 || inputs.get("垫").getRankOffsetMin() < 0) {
            throw new BizException("保档和垫档区间不能跨到当前位次之前");
        }
        if (inputs.get("冲").getRankOffsetMax() > inputs.get("稳").getRankOffsetMin()
                || inputs.get("稳").getRankOffsetMax() > inputs.get("保").getRankOffsetMin()
                || inputs.get("保").getRankOffsetMax() > inputs.get("垫").getRankOffsetMin()) {
            throw new BizException("梯度区间顺序必须保持冲、稳、保、垫，不要交叉");
        }
    }

    private String normalizeGradientKey(String key) {
        String value = safeTrim(key).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "冲", "chong" -> "冲";
            case "稳", "wen" -> "稳";
            case "保", "bao" -> "保";
            case "垫", "dian" -> "垫";
            default -> "";
        };
    }

    private GradientCounts targetCounts(String strategyMode) {
        return targetCounts(strategyMode, TOTAL_COUNT);
    }

    private GradientCounts targetCounts(String strategyMode, int maxVolunteerCount) {
        int max = maxVolunteerCount <= 0 ? TOTAL_COUNT : maxVolunteerCount;
        if (max == 96) {
            if ("保守型".equals(strategyMode)) {
                return new GradientCounts(10, 34, 34, 18);
            }
            if ("冲刺型".equals(strategyMode)) {
                return new GradientCounts(29, 38, 19, 10);
            }
            return new GradientCounts(19, 38, 29, 10);
        }
        if (max == 60) {
            if ("保守型".equals(strategyMode)) {
                return new GradientCounts(6, 21, 21, 12);
            }
            if ("冲刺型".equals(strategyMode)) {
                return new GradientCounts(18, 24, 12, 6);
            }
            return new GradientCounts(12, 24, 18, 6);
        }
        double[] ratios;
        if ("保守型".equals(strategyMode)) {
            ratios = new double[]{0.10, 0.35, 0.35, 0.20};
        } else if ("冲刺型".equals(strategyMode)) {
            ratios = new double[]{0.30, 0.40, 0.20, 0.10};
        } else {
            ratios = new double[]{0.20, 0.40, 0.30, 0.10};
        }
        int chong = (int) Math.floor(max * ratios[0]);
        int wen = (int) Math.floor(max * ratios[1]);
        int bao = (int) Math.floor(max * ratios[2]);
        int dian = Math.max(0, max - chong - wen - bao);
        int sum = chong + wen + bao + dian;
        while (sum < max) {
            wen++;
            sum++;
        }
        while (sum > max && dian > 0) {
            dian--;
            sum--;
        }
        return new GradientCounts(chong, wen, bao, dian);
    }

    private int targetCount(String gradient, String strategyMode) {
        return targetCount(gradient, strategyMode, TOTAL_COUNT);
    }

    private int targetCount(String gradient, String strategyMode, int maxVolunteerCount) {
        return targetCounts(strategyMode, maxVolunteerCount).count(gradient);
    }

    private int policyTargetCount(GenerateRequest req) {
        if (req != null && req.getPolicyMaxVolunteerCount() != null && req.getPolicyMaxVolunteerCount() > 0) {
            return req.getPolicyMaxVolunteerCount();
        }
        return TOTAL_COUNT;
    }

    private String policyVolunteerUnitType(GenerateRequest req) {
        String value = req == null ? "" : safeTrim(req.getPolicyVolunteerUnitType());
        return value.isBlank() ? ProvincePolicyService.UNIT_MAJOR_96 : value;
    }

    private String policyVolunteerUnitLabel(GenerateRequest req) {
        String value = req == null ? "" : safeTrim(req.getPolicyVolunteerUnitLabel());
        return value.isBlank() ? "专业（类）+ 院校" : value;
    }

    private String policyBatchName(GenerateRequest req) {
        String value = req == null ? "" : safeTrim(req.getPolicyBatchName());
        return value.isBlank() ? "普通本科批" : value;
    }

    private void applyRangeContext(List<VolunteerItem> items, GradientRangeDetail range) {
        if (items == null || range == null) {
            return;
        }
        for (VolunteerItem item : items) {
            boolean within = item.getHistoryMinRank() >= range.getRankLow()
                    && item.getHistoryMinRank() <= range.getRankHigh();
            boolean backfilled = NEIGHBOR_GRADIENT_BACKFILL.equals(item.getFillReason());
            item.setWithinConfiguredRange(within && !backfilled);
            item.setOutsideConfiguredRange(!within || backfilled);
            String note = within && !backfilled
                    ? String.format("参考位次位于本次%s档区间：第%,d ~ %,d位", range.getGradient(), range.getRankLow(), range.getRankHigh())
                    : String.format("参考位次超出本次%s档区间：第%,d ~ %,d位，请重点复核", range.getGradient(), range.getRankLow(), range.getRankHigh());
            if (backfilled) {
                note = note + "；由相邻梯度参考区间补充";
            }
            item.setRangeNote(note);
        }
    }

    private void backfillGradientFromNeighbors(String targetGradient, List<VolunteerItem> targetItems, int targetCount,
                                               List<String> neighborGradients, ResolvedGradientRanges ranges,
                                               String subjectType, List<String> resubjects,
                                               PreferenceProfile profile, GenerationStats stats,
                                               CandidateFilterEngine.FilterCriteria criteria,
                                               List<List<VolunteerItem>> allGradientItems) {
        if (targetItems == null || targetItems.size() >= targetCount || neighborGradients == null || ranges == null) {
            return;
        }
        Set<String> existingKeys = allGradientItems == null ? new HashSet<>() : allGradientItems.stream()
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(this::volunteerItemDedupKey)
                .collect(Collectors.toCollection(HashSet::new));
        int added = 0;
        for (String neighborGradient : neighborGradients) {
            if (targetItems.size() >= targetCount) {
                break;
            }
            GradientRangeDetail neighborRange = ranges.get(neighborGradient);
            if (neighborRange == null) {
                continue;
            }
            int fetchCount = Math.max(TOTAL_COUNT, targetCount + (targetCount - targetItems.size()) + 32);
            List<VolunteerItem> candidates = pickGradient(neighborGradient, subjectType,
                    neighborRange.getRankLow(), neighborRange.getRankHigh(), resubjects,
                    fetchCount, profile, null, criteria);
            for (VolunteerItem candidate : candidates) {
                if (targetItems.size() >= targetCount) {
                    break;
                }
                String key = volunteerItemDedupKey(candidate);
                if (!existingKeys.add(key)) {
                    continue;
                }
                candidate.setGradient(targetGradient);
                candidate.setOutsideConfiguredRange(true);
                candidate.setFillReason(NEIGHBOR_GRADIENT_BACKFILL);
                targetItems.add(candidate);
                added++;
            }
        }
        if (stats != null && added > 0) {
            stats.addNeighborGradientBackfill(added);
        }
    }

    private String volunteerItemDedupKey(VolunteerItem item) {
        if (item == null) {
            return "";
        }
        return safeText(item.getSchoolId()) + "|" + safeText(item.getUniversityName()) + "|"
                + safeText(item.getMajorName()) + "|" + item.getHistoryMinRank() + "|"
                + safeText(item.getDataSourceType());
    }

    private void applyRangeCounts(ResolvedGradientRanges ranges, List<VolunteerItem> chongItems,
                                  List<VolunteerItem> wenItems, List<VolunteerItem> baoItems,
                                  List<VolunteerItem> dianItems) {
        ranges.get("冲").setActualCount(chongItems == null ? 0 : chongItems.size());
        ranges.get("稳").setActualCount(wenItems == null ? 0 : wenItems.size());
        ranges.get("保").setActualCount(baoItems == null ? 0 : baoItems.size());
        ranges.get("垫").setActualCount(dianItems == null ? 0 : dianItems.size());
    }

    private void appendRangeSummaryQualityNote(GradientRangeSummary summary, GenerationStats stats) {
        if (summary == null || stats == null || stats.getSpecialExcludedCount() <= 0) {
            return;
        }
        summary.setExplanation(summary.getExplanation()
                + String.format(" 本次已默认排除%d条专项、军警、艺术体育、性别限制、定向等普通志愿不适用记录。", stats.getSpecialExcludedCount()));
    }

    /**
     * 按梯度挑选志愿项 — 优先使用专业分数线，不足时回退院校级数据
     *
     * <p>过滤层次：</p>
     * <ol>
     *   <li>specialTypeReason：招生类型（专项/军警/艺体/定向）排除</li>
     *   <li>matchResubject：选科再选过滤</li>
     *   <li>matchesConstraints：民办/中外/学费 budget 三档</li>
     *   <li>{@link CandidateFilterEngine#filterCandidates}：完整 16 项硬规则
     *       （含 dislikedMajors/medicalLimitations/singleSubjectScores/foreignLanguage/gender/qualifications）</li>
     * </ol>
     * <p>被任一层拒绝的候选都不会进入 deduped；不允许被「凑数」逻辑回填。</p>
     */
    private List<VolunteerItem> pickGradient(String gradient, String subjectType,
                                              int rankLow, int rankHigh,
                                              List<String> resubjects, int maxCount,
                                              PreferenceProfile profile, GenerationStats stats,
                                              CandidateFilterEngine.FilterCriteria criteria) {
        Map<String, University> schoolCache = new HashMap<>();
        // ── Step 1: 从专业分数线表查候选 ──
        List<MajorScoreGz> majorCandidates = scoreLineService.findMajorCandidates(subjectType, rankLow, rankHigh, resubjects);

        // 按 schoolId + majorName + batch 去重，优先最新年份
        Map<String, MajorScoreGz> majorDeduped = new LinkedHashMap<>();
        Map<String, RequirementResolution> requirementCache = new HashMap<>();
        for (MajorScoreGz m : majorCandidates) {
            University uni = getUniversity(m.getSchoolId(), schoolCache);
            String specialReason = specialTypeReason(criteria, uni, m.getUniversityName(), m.getMajorName(), m.getBatch());
            if (!specialReason.isBlank()) {
                if (stats != null) stats.incrementSpecialExcluded();
                continue;
            }
            RequirementResolution requirement = resolveMajorRequirement(m, subjectType);
            requirementCache.put(majorRequirementKey(m), requirement);
            if (!matchResubject(requirement.getRequirement(), resubjects)) {
                continue;
            }
            if (!matchesConstraints(uni, m.getUniversityName(), m.getMajorName(), profile)) {
                continue;
            }
            // CandidateFilterEngine 16 项硬规则（dislikedMajors/medicalLimitations/foreignLanguage 等）
            CandidateFilterEngine.CandidatePlan plan = toCandidatePlan(m, uni, requirement, subjectType, criteria);
            if (criteria != null && hasHardRuleViolation(criteria, plan)) {
                continue;
            }
            String key = m.getSchoolId() + "|" + m.getMajorName() + "|" + (m.getBatch() != null ? m.getBatch() : "");
            MajorScoreGz existing = majorDeduped.get(key);
            if (existing == null || m.getYear() > existing.getYear()) {
                majorDeduped.put(key, m);
            }
        }

        // 每所学校保留最多3个专业，优先本科批 + 最新年份
        Map<String, List<MajorScoreGz>> majorBySchool = new LinkedHashMap<>();
        for (MajorScoreGz m : majorDeduped.values()) {
            majorBySchool.computeIfAbsent(m.getSchoolId(), k -> new ArrayList<>()).add(m);
        }

        List<VolunteerItem> majorItems = new ArrayList<>();
        for (List<MajorScoreGz> schoolMajors : majorBySchool.values()) {
            // 本科批优先排序，再按年份降序
            schoolMajors.sort((a, b) -> {
                boolean aMain = a.getBatch() != null && a.getBatch().contains("本科批") && !a.getBatch().contains("专项");
                boolean bMain = b.getBatch() != null && b.getBatch().contains("本科批") && !b.getBatch().contains("专项");
                if (aMain != bMain) return aMain ? -1 : 1;
                return Integer.compare(b.getYear(), a.getYear());
            });
            // 每校最多3个专业
            int limit = Math.min(3, schoolMajors.size());
            for (int i = 0; i < limit; i++) {
                MajorScoreGz major = schoolMajors.get(i);
                RequirementResolution requirement = requirementCache.get(majorRequirementKey(major));
                if (requirement == null) {
                    requirement = resolveMajorRequirement(major, subjectType);
                }
                majorItems.add(toVolunteerItem(major, gradient, subjectType, schoolCache, requirement));
            }
        }

        sortSelectionCandidates(majorItems, gradient, rankLow, rankHigh, profile);

        // 如果专业数据已够，直接返回
        if (majorItems.size() >= maxCount) {
            return majorItems.stream().limit(maxCount).collect(Collectors.toList());
        }

        // ── Step 2: 不足部分从院校分数线表补充 ──
        Set<String> coveredSchools = majorItems.stream()
                .map(VolunteerItem::getSchoolId)
                .collect(Collectors.toSet());

        List<ScoreLineGz> fallbackCandidates = scoreLineService.findCandidates(subjectType, rankLow, rankHigh, resubjects);

        // 院校级去重：每校一条
        Map<String, ScoreLineGz> bySchool = new LinkedHashMap<>();
        for (ScoreLineGz sl : fallbackCandidates) {
            if (coveredSchools.contains(sl.getSchoolId())) continue;
            University uni = getUniversity(sl.getSchoolId(), schoolCache);
            String specialReason = specialTypeReason(criteria, uni, sl.getUniversityName(), sl.getMajorName(), sl.getBatch());
            if (!specialReason.isBlank()) {
                if (stats != null) stats.incrementSpecialExcluded();
                continue;
            }
            if (!matchResubject(sl.getResubjectRequirement(), resubjects)) {
                continue;
            }
            if (!matchesConstraints(uni, sl.getUniversityName(), sl.getMajorName(), profile)) {
                continue;
            }
            // CandidateFilterEngine 16 项硬规则同样作用于院校级回退
            CandidateFilterEngine.CandidatePlan plan = toCandidatePlan(sl, uni, subjectType, criteria);
            if (criteria != null && hasHardRuleViolation(criteria, plan)) {
                continue;
            }
            String sid = sl.getSchoolId();
            ScoreLineGz existing = bySchool.get(sid);
            if (existing == null) {
                bySchool.put(sid, sl);
            } else {
                String curBatch = sl.getBatch() != null ? sl.getBatch() : "";
                String exBatch = existing.getBatch() != null ? existing.getBatch() : "";
                boolean curIsMain = curBatch.contains("本科批") && !curBatch.contains("专项");
                boolean exIsMain = exBatch.contains("本科批") && !exBatch.contains("专项");
                if (curIsMain && !exIsMain) {
                    bySchool.put(sid, sl);
                } else if (sl.getYear() > existing.getYear()) {
                    bySchool.put(sid, sl);
                }
            }
        }

        List<ScoreLineGz> fallbackList = new ArrayList<>(bySchool.values());
        int remaining = maxCount - majorItems.size();
        List<VolunteerItem> fallbackItems = fallbackList.stream()
                .map(sl -> {
                    VolunteerItem item = new VolunteerItem();
                    item.setProvinceCode(ProvincePolicyService.GZ);
                    item.setVolunteerUnitType(ProvincePolicyService.UNIT_MAJOR_96);
                    item.setVolunteerUnitLabel("专业（类）+ 院校");
                    item.setUniversityName(sl.getUniversityName());
                    String displayName = sl.getBatch() != null && !sl.getBatch().isEmpty() ? sl.getBatch() : "普通类";
                    item.setMajorName(sl.getMajorName() != null && !sl.getMajorName().isEmpty()
                            ? sl.getMajorName() : displayName);
                    item.setGradient(gradient);
                    item.setHistoryMinScore(sl.getMinScore() != null ? sl.getMinScore() : 0);
                    item.setHistoryMinRank(sl.getMinRank() != null ? sl.getMinRank() : 0);
                    item.setReferenceYear(sl.getYear());
                    item.setResubjectRequirement(sl.getResubjectRequirement() != null ? sl.getResubjectRequirement() : "");
                    item.setSubjectRequirementSource(item.getResubjectRequirement().isBlank() ? "missing" : "score_line");
                    item.setLatestPlanCount(sl.getPlanCount());
                    item.setSchoolId(sl.getSchoolId());
                    applyUniversityInfo(item, getUniversity(sl.getSchoolId(), schoolCache));
                    item.setDataSourceType("院校级");
                    item.setConfidenceLabel(resolveConfidence(sl.getYear(), false));
                    item.setLegacySubjectFallback(isLegacySubjectFallback(sl.getSubjectType(), subjectType));
                    item.setSpecialTypeFlag(false);
                    item.setRecruitType(classifyRecruitType(getUniversity(sl.getSchoolId(), schoolCache),
                            sl.getUniversityName(), sl.getMajorName(), sl.getBatch()));
                    return item;
                }).collect(Collectors.toList());
        sortSelectionCandidates(fallbackItems, gradient, rankLow, rankHigh, profile);
        if (fallbackItems.size() > remaining) {
            fallbackItems = fallbackItems.stream().limit(remaining).collect(Collectors.toList());
        }

        // 合并专业级 + 院校级补充
        List<VolunteerItem> result = new ArrayList<>(majorItems);
        result.addAll(fallbackItems);
        return result;
    }

    /**
     * 把 GenerateRequest 转成 CandidateFilterEngine 的 16 项硬规则输入条件。
     *
     * <p>未传字段使用合理默认（普通类、本科批、acceptPrivateSchool/CoOp 默认 true）；
     * 用户明确拒绝民办/中外合作时（acceptPrivate=false / acceptSinoForeign=false）会反映到 criteria。</p>
     */
    private CandidateFilterEngine.FilterCriteria buildFilterCriteria(GenerateRequest req, String provinceCode, String subjectType) {
        CandidateFilterEngine.FilterCriteria c = new CandidateFilterEngine.FilterCriteria();
        if (req == null) return c;
        c.setYear(req.getYear());
        c.setProvince(provinceCode);
        c.setBatchCode(req.getBatchCode() == null || req.getBatchCode().isBlank() ? null : req.getBatchCode());
        c.setCandidateType(req.getCandidateType() == null || req.getCandidateType().isBlank() ? "普通类" : req.getCandidateType());
        c.setSubjectType(subjectType);
        // selectedSubjects 优先用新版字段，回退老版 resubjects
        List<String> selected = req.getSelectedSubjects() != null && !req.getSelectedSubjects().isEmpty()
                ? req.getSelectedSubjects() : req.getResubjects();
        c.setSelectedSubjects(selected == null ? List.of() : new ArrayList<>(selected));
        c.setMaxTuition(req.getMaxTuition());
        // 用户接受标志：优先新版字段（acceptPrivateSchool/acceptChineseForeignCoop），回退老版（acceptPrivate/acceptSinoForeign）
        Boolean acceptPriv = req.getAcceptPrivateSchool() != null
                ? req.getAcceptPrivateSchool()
                : (req.getAcceptPrivate() == null ? Boolean.TRUE : req.getAcceptPrivate());
        Boolean acceptCoop = req.getAcceptChineseForeignCoop() != null
                ? req.getAcceptChineseForeignCoop()
                : (req.getAcceptSinoForeign() == null ? Boolean.TRUE : req.getAcceptSinoForeign());
        c.setAcceptPrivateSchool(acceptPriv);
        c.setAcceptChineseForeignCoop(acceptCoop);
        c.setDislikedMajors(req.getDislikedMajors() == null ? List.of() : new ArrayList<>(req.getDislikedMajors()));
        c.setMedicalLimitations(req.getMedicalLimitations() == null ? List.of() : new ArrayList<>(req.getMedicalLimitations()));
        c.setSingleSubjectScores(req.getSingleSubjectScores() == null ? Map.of() : new LinkedHashMap<>(req.getSingleSubjectScores()));
        c.setForeignLanguage(req.getForeignLanguage());
        c.setGender(req.getGender());
        c.setQualifications(req.getQualificationTags() == null ? List.of() : new ArrayList<>(req.getQualificationTags()));
        c.setRejectedConditions(List.of());
        return c;
    }

    /** MajorScoreGz → CandidatePlan（专业级，CandidateFilterEngine 的输入）。 */
    private CandidateFilterEngine.CandidatePlan toCandidatePlan(MajorScoreGz m, University uni,
                                                                RequirementResolution requirement,
                                                                String subjectType,
                                                                CandidateFilterEngine.FilterCriteria criteria) {
        CandidateFilterEngine.CandidatePlan p = new CandidateFilterEngine.CandidatePlan();
        p.setYear(null);
        p.setProvince(criteria == null ? null : criteria.getProvince());
        p.setBatchCode(safeText(m.getBatch()));
        p.setCandidateType(criteria == null ? "普通类" : criteria.getCandidateType());
        p.setSubjectType(safeText(subjectType));
        p.setSelectedSubjectRequirement(requirement == null ? "" : safeText(requirement.getRequirement()));
        p.setMajorName(safeText(m.getMajorName()));
        p.setTuition(null);
        p.setPrivateSchool(isPrivateUni(uni));
        p.setChineseForeignCoop(isCooperativeUni(uni, m.getUniversityName(), m.getMajorName()));
        p.setRemarks("");
        p.setSpecialLimit("");
        return p;
    }

    /** ScoreLineGz → CandidatePlan（院校级回退，CandidateFilterEngine 的输入）。 */
    private CandidateFilterEngine.CandidatePlan toCandidatePlan(ScoreLineGz sl, University uni,
                                                                String subjectType,
                                                                CandidateFilterEngine.FilterCriteria criteria) {
        CandidateFilterEngine.CandidatePlan p = new CandidateFilterEngine.CandidatePlan();
        p.setYear(null);
        p.setProvince(criteria == null ? null : criteria.getProvince());
        p.setBatchCode(safeText(sl.getBatch()));
        p.setCandidateType(criteria == null ? "普通类" : criteria.getCandidateType());
        p.setSubjectType(safeText(subjectType));
        p.setSelectedSubjectRequirement(safeText(sl.getResubjectRequirement()));
        p.setMajorName(safeText(sl.getMajorName()));
        p.setTuition(null);
        p.setPrivateSchool(isPrivateUni(uni));
        p.setChineseForeignCoop(isCooperativeUni(uni, sl.getUniversityName(), sl.getMajorName()));
        p.setRemarks("");
        p.setSpecialLimit("");
        return p;
    }

    /**
     * 调用 CandidateFilterEngine 单条过滤；命中任一硬规则返回 true，调用方据此跳过该候选。
     * 该方法不会修改 plan 的 filterReasons（CandidateFilterEngine 已写入 plan 内）；
     * 主流程不再使用被拒条目，写入 filterReasons 仅作未来审计 hook。
     */
    private boolean hasHardRuleViolation(CandidateFilterEngine.FilterCriteria criteria,
                                         CandidateFilterEngine.CandidatePlan plan) {
        if (criteria == null || plan == null) return false;
        CandidateFilterEngine.FilterResult result = candidateFilterEngine.filterCandidates(criteria, List.of(plan));
        return result != null && !result.getRejected().isEmpty();
    }

    private boolean isPrivateUni(University uni) {
        if (uni == null) return false;
        String nature = uni.getNatureName() == null ? "" : uni.getNatureName();
        List<String> tags = uni.getTags() == null ? List.of() : uni.getTags();
        return nature.contains("民办") || tags.contains("民办");
    }

    private boolean isCooperativeUni(University uni, String schoolName, String majorName) {
        String name = schoolName == null ? "" : schoolName;
        String major = majorName == null ? "" : majorName;
        String nature = uni == null || uni.getNatureName() == null ? "" : uni.getNatureName();
        List<String> tags = uni == null || uni.getTags() == null ? List.of() : uni.getTags();
        return tags.contains("中外合作办学")
                || tags.contains("内地与港澳台合作办学")
                || nature.contains("中外合作")
                || name.contains("国际学院")
                || name.contains("中外合作")
                || name.contains("港澳台")
                || major.contains("中外合作")
                || major.contains("联合培养");
    }

    private void sortSelectionCandidates(List<VolunteerItem> items, String gradient,
                                         int rankLow, int rankHigh, PreferenceProfile profile) {
        int targetRank = selectionTargetRank(gradient, rankLow, rankHigh);
        items.sort(Comparator
                .comparingDouble((VolunteerItem item) -> selectionScore(item, targetRank, rankLow, rankHigh, profile))
                .reversed()
                .thenComparingInt(VolunteerItem::getHistoryMinRank)
                .thenComparing(item -> safeText(item.getSchoolId()))
                .thenComparing(item -> safeText(item.getMajorName())));
    }

    private int selectionTargetRank(String gradient, int rankLow, int rankHigh) {
        if ("冲".equals(gradient)) {
            return rankHigh;
        }
        if ("保".equals(gradient) || "垫".equals(gradient)) {
            return rankLow;
        }
        return rankLow + Math.max(0, (rankHigh - rankLow) / 2);
    }

    private double selectionScore(VolunteerItem item, int targetRank, int rankLow, int rankHigh,
                                  PreferenceProfile profile) {
        int span = Math.max(1, rankHigh - rankLow);
        double fit = 1.0 - Math.min(1.0, Math.abs(item.getHistoryMinRank() - targetRank) * 1.0 / span);
        double score = fit * 45;
        score += "专业级".equals(item.getDataSourceType()) ? 20 : 6;
        score += requirementSelectionScore(item.getSubjectRequirementSource());
        score += Math.max(0, item.getReferenceYear() - 2020) * 1.2;
        score += planAvailabilitySelectionScore(item.getLatestPlanCount());

        if ("高可信".equals(item.getConfidenceLabel())) {
            score += 6;
        } else if ("中可信".equals(item.getConfidenceLabel())) {
            score += 3;
        }

        if ("学校优先".equals(profile.getDecisionPriority()) && hasEliteTag(item)) {
            score += 4;
        }
        if ("专业优先".equals(profile.getDecisionPriority()) && "专业级".equals(item.getDataSourceType())) {
            score += 6;
        }
        if ("就业优先".equals(profile.getCareerGoal()) && isCareerPractical(item)) {
            score += 4;
        }
        if ("升学优先".equals(profile.getCareerGoal()) && hasEliteTag(item)) {
            score += 4;
        }
        if ("城市机会优先".equals(profile.getCareerGoal()) && isOpportunityCity(item)) {
            score += 4;
        }
        score += Math.min(12, preferenceSelectionScore(item, profile) * 0.35);
        return score;
    }

    private double planAvailabilitySelectionScore(Integer latestPlanCount) {
        if (latestPlanCount == null || latestPlanCount <= 0) {
            return -3;
        }
        if (latestPlanCount >= 30) {
            return 6;
        }
        if (latestPlanCount >= 15) {
            return 4;
        }
        if (latestPlanCount >= 5) {
            return 2;
        }
        return -2;
    }

    private double requirementSelectionScore(String source) {
        return switch (safeText(source)) {
            case "official_requirement" -> 16;
            case "score_line" -> 8;
            case "inferred" -> 3;
            default -> 0;
        };
    }

    private double preferenceSelectionScore(VolunteerItem item, PreferenceProfile profile) {
        double score = 0;
        String major = safeText(item.getMajorName()).toLowerCase();
        for (String pref : profile.getPreferredMajors()) {
            String key = safeText(pref).trim().toLowerCase();
            if (!key.isEmpty() && (major.contains(key) || fuzzyMajorMatch(major, key))) {
                score += 18;
                break;
            }
        }

        String areaText = safeText(item.getProvince()) + safeText(item.getCity()) + safeText(item.getUniversityName());
        for (String region : profile.getPreferredRegions()) {
            String key = safeText(region).trim();
            if (!key.isEmpty() && areaText.contains(key)) {
                score += 14;
                break;
            }
        }
        return score;
    }

    /**
     * MajorScoreGz → VolunteerItem
     */
    private VolunteerItem toVolunteerItem(MajorScoreGz m, String gradient, String requestedSubjectType,
                                          Map<String, University> schoolCache,
                                          RequirementResolution requirement) {
        VolunteerItem item = new VolunteerItem();
        item.setProvinceCode(ProvincePolicyService.GZ);
        item.setVolunteerUnitType(ProvincePolicyService.UNIT_MAJOR_96);
        item.setVolunteerUnitLabel("专业（类）+ 院校");
        item.setUniversityName(m.getUniversityName());
        item.setMajorName(m.getMajorName());
        item.setGradient(gradient);
        item.setHistoryMinScore(m.getMinScore() != null ? m.getMinScore() : 0);
        item.setHistoryMinRank(m.getMinRank() != null ? m.getMinRank() : 0);
        item.setReferenceYear(m.getYear());
        item.setResubjectRequirement(requirement != null ? requirement.getRequirement() : "");
        item.setSubjectRequirementSource(requirement != null ? requirement.getSource() : "missing");
        item.setLatestPlanCount(m.getPlanCount());
        if (requirement != null) {
            item.setRequirementSourceUrl(requirement.getSourceUrl());
            item.setRequirementSourceYear(requirement.getYear());
            item.setRequirementSourceName(requirement.getSourceName());
        }
        item.setSchoolId(m.getSchoolId());
        applyUniversityInfo(item, getUniversity(m.getSchoolId(), schoolCache));
        item.setDataSourceType("专业级");
        item.setConfidenceLabel(resolveConfidence(m.getYear(), true));
        item.setLegacySubjectFallback(isLegacySubjectFallback(m.getSubjectType(), requestedSubjectType));
        item.setSpecialTypeFlag(false);
        item.setRecruitType(classifyRecruitType(getUniversity(m.getSchoolId(), schoolCache),
                m.getUniversityName(), m.getMajorName(), m.getBatch()));
        return item;
    }

    private void enrichWithHistoryRecords(List<VolunteerItem> items, String subjectType) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (VolunteerItem item : items) {
            if (item.getSchoolId() == null || item.getSchoolId().isBlank()) {
                item.setHistoryRecords(List.of());
                continue;
            }
            try {
                List<ScoreLineService.ScoreLineView> views = scoreLineService.findRecentHistory(
                        item.getSchoolId(), item.getMajorName(), subjectType, 3);
                item.setHistoryRecords(views.stream().map(this::toHistoryRecord).toList());
            } catch (Exception e) {
                log.warn("近三年录取记录查询失败: schoolId={}, majorName={}",
                        item.getSchoolId(), item.getMajorName(), e);
                item.setHistoryRecords(List.of());
            }
        }
    }

    private void enrichWithPlanAndRankSignals(List<VolunteerItem> items, int studentRank) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (VolunteerItem item : items) {
            int rankGap = item.getHistoryMinRank() - studentRank;
            item.setRankGap(rankGap);
            item.setRankGapRatio(studentRank > 0
                    ? Math.round(rankGap * 1000.0 / studentRank) / 10.0
                    : 0);

            PlanSignal signal = analyzePlanSignal(item);
            if ((item.getLatestPlanCount() == null || item.getLatestPlanCount() <= 0)
                    && signal.latestPlanCount() != null && signal.latestPlanCount() > 0) {
                item.setLatestPlanCount(signal.latestPlanCount());
            }
            item.setPlanTrend(signal.trend());
            item.setPlanRiskNote(signal.note());
            item.setPlanExpansionIndex(signal.expansionIndex());
            item.setPlanExpansionLabel(signal.expansionLabel());
            item.setPlanExpansionNote(signal.expansionNote());
            item.setSchoolEnrollmentIndex(signal.supplyIndex());
            item.setSchoolEnrollmentLabel(signal.supplyLabel());
            item.setSchoolEnrollmentNote(signal.supplyNote());
        }
        applyPeerSupplyAdjustment(items);
        for (VolunteerItem item : items) {
            adjustAdmissionProbabilityWithPlanSignal(item);
        }
    }

    private PlanSignal analyzePlanSignal(VolunteerItem item) {
        List<HistoryRecord> records = item.getHistoryRecords() == null ? List.of() : item.getHistoryRecords();
        List<HistoryRecord> withPlan = records.stream()
                .filter(record -> record.getPlanCount() != null && record.getPlanCount() > 0)
                .sorted(Comparator.comparing(
                        HistoryRecord::getYear,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Integer fallbackPlan = item.getLatestPlanCount();
        if (withPlan.isEmpty()) {
            if (fallbackPlan != null && fallbackPlan > 0) {
                double supply = calcSupplyIndex(fallbackPlan, 100, 1, "单年计划", item);
                return new PlanSignal(fallbackPlan, "单年计划",
                        String.format("最近一年招生计划%d人，缺少连续年份计划数，需核对当年专业目录。", fallbackPlan),
                        planAvailabilitySelectionScore(fallbackPlan),
                        100, "单年计划", "只有最近一年计划数，扩招指数按100作为中性处理。",
                        supply, supplyLabel(supply),
                        String.format("招生供给指数%.0f分：最近计划%d人，缺少连续年份基准。", supply, fallbackPlan));
            }
            double conservativeSupply = calcConservativeSupplyIndex(item, records.size());
            return new PlanSignal(null, "计划数暂缺", "招生计划数暂缺，需核对当年官方专业目录。", -5,
                    0, "计划暂缺", "缺少当年计划数，暂不能计算扩招指数。",
                    conservativeSupply, supplyLabel(conservativeSupply),
                    String.format("缺少计划数，招生供给指数按保守下限%.0f分处理，只参考数据层级、年份和历史记录完整度。", conservativeSupply));
        }

        HistoryRecord latest = withPlan.get(0);
        int latestPlan = latest.getPlanCount();
        double baseScore = planAvailabilitySelectionScore(latestPlan);
        if (withPlan.size() == 1) {
            double supply = calcSupplyIndex(latestPlan, 100, 1, "单年计划", item);
            return new PlanSignal(latestPlan, "单年计划",
                    String.format("%d年招生计划%d人，缺少连续年份计划数，需结合当年目录复核。",
                            latest.getYear(), latestPlan),
                    baseScore,
                    100, "单年计划", "只有一年有效计划数，扩招指数按100作为中性处理。",
                    supply, supplyLabel(supply),
                    String.format("招生供给指数%.0f分：%d年计划%d人，连续年份不足。", supply, latest.getYear(), latestPlan));
        }

        double previousAverage = withPlan.stream()
                .skip(1)
                .map(HistoryRecord::getPlanCount)
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        HistoryRecord previous = withPlan.get(1);
        int previousPlan = previous.getPlanCount();
        int delta = latestPlan - previousPlan;
        double yearRatio = previousPlan > 0 ? delta * 1.0 / previousPlan : 0;
        double expansionIndex = previousAverage > 0 ? round1(latestPlan * 100.0 / previousAverage) : 100;
        double baselineDeltaRatio = previousAverage > 0 ? (latestPlan - previousAverage) / previousAverage : 0;

        String trend;
        double adjustment = baseScore;
        if ((delta >= 2 && yearRatio >= 0.15) || baselineDeltaRatio >= 0.15) {
            trend = "扩招";
            adjustment += 4;
        } else if ((delta <= -2 && yearRatio <= -0.15) || baselineDeltaRatio <= -0.15) {
            trend = "缩招";
            adjustment -= 6;
        } else {
            trend = "基本稳定";
            adjustment += 2;
        }
        String note = String.format("%d年计划%d人，较%d年%s%d人（%.0f%%），判定为%s。",
                latest.getYear(), latestPlan,
                previous.getYear(),
                delta >= 0 ? "增加" : "减少",
                Math.abs(delta),
                Math.abs(yearRatio) * 100,
                trend);
        String expansionNote = String.format("扩招指数%.1f：%d年计划%d人，相对近年有效计划均值%.1f人。",
                expansionIndex, latest.getYear(), latestPlan, previousAverage);
        double supply = calcSupplyIndex(latestPlan, expansionIndex, withPlan.size(), trend, item);
        return new PlanSignal(latestPlan, trend, note, adjustment,
                expansionIndex, expansionLabel(expansionIndex, trend), expansionNote,
                supply, supplyLabel(supply),
                String.format("招生供给指数%.0f分：综合计划规模、扩招指数、历史年份数和数据层级。", supply));
    }

    private double calcSupplyIndex(Integer planCount, double expansionIndex, int historyCount,
                                   String trend, VolunteerItem item) {
        if (planCount == null || planCount <= 0) {
            return 0;
        }
        double score;
        if (planCount >= 60) score = 74;
        else if (planCount >= 30) score = 66;
        else if (planCount >= 15) score = 56;
        else if (planCount >= 8) score = 48;
        else if (planCount >= 4) score = 38;
        else score = 28;

        if (expansionIndex >= 130) score += 12;
        else if (expansionIndex >= 115) score += 8;
        else if (expansionIndex > 0 && expansionIndex <= 70) score -= 14;
        else if (expansionIndex > 0 && expansionIndex <= 85) score -= 9;
        else if ("基本稳定".equals(trend)) score += 4;

        score += Math.min(8, Math.max(0, historyCount) * 3);
        if ("专业级".equals(item.getDataSourceType()) || "院校专业组".equals(item.getDataSourceType())) {
            score += 5;
        }
        if ("院校级".equals(item.getDataSourceType())) {
            score -= 6;
        }
        return Math.max(0, Math.min(100, round1(score)));
    }

    private double calcConservativeSupplyIndex(VolunteerItem item, int historyCount) {
        double score = 24;
        if ("专业级".equals(item.getDataSourceType()) || "院校专业组".equals(item.getDataSourceType())) {
            score += 8;
        } else if ("院校级".equals(item.getDataSourceType())) {
            score += 3;
        }
        score += Math.min(10, Math.max(0, historyCount) * 3);
        Integer year = item.getReferenceYear();
        if (year != null && year >= 2025) score += 5;
        else if (year != null && year >= 2024) score += 3;
        if (item.isWithinConfiguredRange()) {
            score += 2;
        }
        return Math.max(18, Math.min(42, round1(score)));
    }

    private void applyPeerSupplyAdjustment(List<VolunteerItem> items) {
        List<Integer> plans = items.stream()
                .map(VolunteerItem::getLatestPlanCount)
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .sorted()
                .toList();
        if (plans.size() < 5) {
            return;
        }
        double median = plans.get(plans.size() / 2);
        if (median <= 0) {
            return;
        }
        for (VolunteerItem item : items) {
            Integer plan = item.getLatestPlanCount();
            if (plan == null || plan <= 0 || item.getSchoolEnrollmentIndex() <= 0) {
                continue;
            }
            double peerRatio = plan / median;
            double adjustment = 0;
            if (peerRatio >= 2.0) adjustment = 10;
            else if (peerRatio >= 1.5) adjustment = 6;
            else if (peerRatio <= 0.4) adjustment = -10;
            else if (peerRatio <= 0.7) adjustment = -6;
            if (adjustment == 0) {
                continue;
            }
            double adjusted = Math.max(0, Math.min(100, round1(item.getSchoolEnrollmentIndex() + adjustment)));
            item.setSchoolEnrollmentIndex(adjusted);
            item.setSchoolEnrollmentLabel(supplyLabel(adjusted));
            item.setSchoolEnrollmentNote(item.getSchoolEnrollmentNote()
                    + String.format(" 同批候选计划中位数约%.0f人，本条计划%d人，供给指数%s%.0f分。",
                    median, plan, adjustment > 0 ? "上调" : "下调", Math.abs(adjustment)));
        }
    }

    private void adjustAdmissionProbabilityWithPlanSignal(VolunteerItem item) {
        if (item.getAdmissionProb() <= 0) {
            return;
        }
        double adjustment = 0;
        if (item.getPlanExpansionIndex() > 0) {
            adjustment += Math.max(-9, Math.min(9, (item.getPlanExpansionIndex() - 100) / 6.0));
        } else {
            adjustment -= 4;
        }
        if (item.getSchoolEnrollmentIndex() > 0) {
            adjustment += Math.max(-6, Math.min(6, (item.getSchoolEnrollmentIndex() - 55) / 8.0));
        } else {
            adjustment -= 4;
        }
        if (item.getLatestPlanCount() != null && item.getLatestPlanCount() > 0 && item.getLatestPlanCount() <= 3) {
            adjustment -= 5;
        }
        if ("缩招".equals(item.getPlanTrend())) {
            adjustment -= 4;
        } else if ("扩招".equals(item.getPlanTrend())) {
            adjustment += 3;
        }
        double adjusted = Math.max(1, Math.min(99, round1(item.getAdmissionProb() + adjustment)));
        item.setAdmissionProb(adjusted);
        item.setProbLevel(probLevelWithPlan(adjusted, item.getProbLevel()));
        applyChanceFields(item, 0);
    }

    private String probLevelWithPlan(double probability, String currentLevel) {
        String suffix = safeText(currentLevel).contains("单年") ? "单年参考·计划修正" : "计划修正";
        if (probability >= 70) return "机会指数较高·" + suffix;
        if (probability >= 45) return "机会指数中等·" + suffix;
        return "机会指数偏低·" + suffix;
    }

    private void normalizePublicChanceFields(List<VolunteerItem> items, int studentRank) {
        if (items == null) {
            return;
        }
        for (VolunteerItem item : items) {
            applyChanceFields(item, studentRank);
        }
    }

    /**
     * 仅做范围 clamp + 缺失字段兜底；chanceScore 主值由 {@link #applyRulePrediction} 写入，
     * 这里不再用任何线性映射重新生产，避免主链路出现两套互相冲突的机会指数算法。
     *
     * <p>缓存命中分支（{@link #readCachedPlanResult}）和早期 plan_json 反序列化分支
     * 可能拿到 chanceScore=0 的旧数据，此时本方法保留旧行为：以 dataConfidenceScore /
     * confidenceLabel 派生 confidenceLevel，但不会重新算 chanceScore。</p>
     */
    private void applyChanceFields(VolunteerItem item, int studentRank) {
        if (item == null) {
            return;
        }
        // 1. chanceScore 只做范围 clamp，不再重新生产
        int chanceScore = Math.max(0, Math.min(100, item.getChanceScore()));
        item.setChanceScore(chanceScore);

        // 2. 派生 chanceLevel / riskLevel / confidenceLevel：
        //    若 FallbackRulePredictionEngine 已经写入则保留，否则按 chanceScore 兜底派生
        if (item.getChanceLevel() == null || item.getChanceLevel().isBlank()) {
            item.setChanceLevel(chanceLevel(chanceScore));
        }
        if (item.getRiskLevel() == null || item.getRiskLevel().isBlank() || "未知".equals(item.getRiskLevel())) {
            item.setRiskLevel(riskLevelFromChance(chanceScore, item.getDataConfidenceScore()));
        }
        if (item.getConfidenceLevel() == null || item.getConfidenceLevel().isBlank()) {
            item.setConfidenceLevel(confidenceLevel(item.getDataConfidenceScore(), item.getConfidenceLabel()));
        }
        if (item.getDataConfidence() <= 0) {
            item.setDataConfidence(item.getDataConfidenceScore() > 0
                    ? item.getDataConfidenceScore()
                    : confidenceScore(item.getConfidenceLabel()));
        }

        // 3. predictedMinRank / rankDiff 兜底：FallbackRulePredictionEngine 已经写入时不覆盖
        if (item.getPredictedMinRank() <= 0) {
            int predicted = item.getPredictedRank() > 0 ? item.getPredictedRank() : item.getHistoryMinRank();
            item.setPredictedMinRank(Math.max(0, predicted));
        }
        if (item.getRankDiff() == 0 && studentRank > 0 && item.getPredictedMinRank() > 0) {
            item.setRankDiff(item.getPredictedMinRank() - studentRank);
        } else if (item.getRankDiff() == 0 && item.getRankGap() != 0) {
            item.setRankDiff(item.getRankGap());
        }
    }

    private String chanceLevel(int chanceScore) {
        if (chanceScore >= 90) return "兜底参考";
        if (chanceScore >= 75) return "稳妥参考";
        if (chanceScore >= 50) return "适中";
        return "冲刺参考";
    }

    private String riskLevelFromChance(int chanceScore, int dataConfidenceScore) {
        if (dataConfidenceScore > 0 && dataConfidenceScore < 40 && chanceScore >= 75) {
            return "中等";
        }
        if (chanceScore >= 90) return "很低";
        if (chanceScore >= 75) return "较低";
        if (chanceScore >= 50) return "中等";
        return "较高";
    }

    private String confidenceLevel(int dataConfidenceScore, String label) {
        int score = dataConfidenceScore > 0 ? dataConfidenceScore : confidenceScore(label);
        if (score >= 80) return "高";
        if (score >= 60) return "中";
        if (score >= 40) return "低";
        return "数据不足";
    }

    private int confidenceScore(String label) {
        if ("高可信".equals(label)) return 85;
        if ("中可信".equals(label)) return 65;
        if ("需复核".equals(label)) return 35;
        return 0;
    }

    private String expansionLabel(double expansionIndex, String trend) {
        if (expansionIndex <= 0) return "计划待核验";
        if ("扩招".equals(trend)) return "实际扩招";
        if ("缩招".equals(trend)) return "实际缩招";
        return "计划稳定";
    }

    private String supplyLabel(double supplyIndex) {
        if (supplyIndex >= 80) return "招生供给强";
        if (supplyIndex >= 60) return "供给中上";
        if (supplyIndex >= 45) return "供给中等";
        if (supplyIndex > 0) return "供给偏紧";
        return "供给待核验";
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private HistoryRecord toHistoryRecord(ScoreLineService.ScoreLineView view) {
        HistoryRecord record = new HistoryRecord();
        record.setYear(view.getYear());
        record.setMinScore(view.getMinScore());
        record.setMinRank(view.getMinRank());
        record.setAvgScore(view.getAvgScore());
        record.setMaxScore(view.getMaxScore());
        record.setPlanCount(view.getPlanCount());
        record.setBatch(view.getBatch());
        record.setSubjectType(view.getSubjectType());
        record.setDataSourceType(view.getDataSourceType());
        record.setConfidenceLabel(view.getConfidenceLabel());
        record.setRankSourceType(view.getRankSourceType());
        record.setRankSourceNote(view.getRankSourceNote());
        record.setRankLow(view.getRankLow());
        record.setRankHigh(view.getRankHigh());
        record.setRankSourceUrl(view.getRankSourceUrl());
        record.setRankSourcePageUrl(view.getRankSourcePageUrl());
        return record;
    }

    /**
     * 再选科目匹配 — 待再选科目数据入库后启用
     * "不限" → 任何人都能报
     * "化学" → 再选包含化学
     * "化学和生物" → 再选同时包含化学和生物
     * "化学或生物" → 再选包含化学或生物之一
     */
    private boolean matchResubject(String requirement, List<String> resubjects) {
        if (requirement == null || requirement.isBlank() || "不限".equals(requirement)) {
            return true;
        }
        if (resubjects == null || resubjects.isEmpty()) {
            return false;
        }
        String req = requirement.trim();
        if (req.contains("和")) {
            String[] parts = req.split("和");
            return Arrays.stream(parts).allMatch(p -> resubjects.contains(p.trim()));
        }
        if (req.contains("或")) {
            String[] parts = req.split("或");
            return Arrays.stream(parts).anyMatch(p -> resubjects.contains(p.trim()));
        }
        return resubjects.contains(req);
    }

    private String inferResubjectRequirement(String majorName) {
        if (majorName == null || majorName.isBlank()) {
            return "";
        }
        String normalized = majorName.replace('（', '(').replace('）', ')');
        if (normalized.contains("物理+化学") || normalized.contains("物理和化学")) {
            return "化学";
        }
        if (normalized.contains("化学和生物") || normalized.contains("化学、生物") || normalized.contains("化学+生物")) {
            return "化学和生物";
        }
        if (normalized.contains("化学或生物")) {
            return "化学或生物";
        }
        if (normalized.contains("政治") && normalized.contains("必选")) {
            return "政治";
        }
        if (normalized.contains("化学") && (normalized.contains("必选") || normalized.contains("限选") || normalized.contains("选考"))) {
            return "化学";
        }
        if (normalized.contains("生物") && (normalized.contains("必选") || normalized.contains("限选") || normalized.contains("选考"))) {
            return "生物";
        }
        return "";
    }

    private RequirementResolution resolveMajorRequirement(MajorScoreGz score, String subjectType) {
        RequirementResolution result = new RequirementResolution();
        if (score == null) {
            return result;
        }
        try {
            String majorName = safeText(score.getMajorName()).trim();
            MajorRequirementGz official = majorRequirementGzMapper.selectLatestByMajor(
                    score.getSchoolId(), majorName, subjectType, majorLookupCore(majorName));
            if (official != null && official.getResubjectRequirement() != null
                    && !official.getResubjectRequirement().isBlank()) {
                result.setRequirement(official.getResubjectRequirement());
                result.setSource("official_requirement");
                result.setYear(official.getYear());
                result.setSourceUrl(official.getSourceUrl());
                result.setSourceName(official.getSourceName());
                return result;
            }
        } catch (Exception e) {
            log.warn("官方选科要求查询失败: schoolId={}, majorName={}", score.getSchoolId(), score.getMajorName(), e);
        }
        if (score.getResubjectRequirement() != null && !score.getResubjectRequirement().isBlank()) {
            result.setRequirement(score.getResubjectRequirement());
            result.setSource("score_line");
            return result;
        }
        String inferred = inferResubjectRequirement(score.getMajorName());
        if (!inferred.isBlank()) {
            result.setRequirement(inferred);
            result.setSource("inferred");
        }
        return result;
    }

    private String majorLookupCore(String majorName) {
        String text = safeText(majorName).trim();
        if (text.isEmpty()) {
            return "";
        }
        text = text.replace('(', '（').replace(')', '）');
        int bracket = text.indexOf('（');
        if (bracket > 0) {
            text = text.substring(0, bracket);
        }
        int note = text.indexOf('[');
        if (note > 0) {
            text = text.substring(0, note);
        }
        text = text.replaceAll("\\s+", "");
        return text;
    }

    private String majorRequirementKey(MajorScoreGz score) {
        if (score == null) {
            return "";
        }
        return safeText(score.getSchoolId()) + "|" + safeText(score.getMajorName());
    }

    /**
     * 映射首选科目：前端传 "物理"/"历史" → 数据库中可能是 "物理类"/"历史类" 或 "理科"/"文科"
     */
    private String mapSubjectType(String firstSubject) {
        if ("物理".equals(firstSubject)) return "物理类";
        if ("历史".equals(firstSubject)) return "历史类";
        return firstSubject;
    }

    private PreferenceProfile toPreferenceProfile(GenerateRequest req) {
        PreferenceProfile profile = new PreferenceProfile();
        if (req.getPreferredMajors() != null) profile.setPreferredMajors(req.getPreferredMajors());
        if (req.getPreferredRegions() != null) profile.setPreferredRegions(req.getPreferredRegions());
        if (req.getStrategyMode() != null && !req.getStrategyMode().isBlank()) profile.setStrategyMode(req.getStrategyMode());
        if (req.getDecisionPriority() != null && !req.getDecisionPriority().isBlank()) profile.setDecisionPriority(req.getDecisionPriority());
        if (req.getCareerGoal() != null && !req.getCareerGoal().isBlank()) profile.setCareerGoal(req.getCareerGoal());
        if (req.getTuitionBudget() != null && !req.getTuitionBudget().isBlank()) profile.setTuitionBudget(req.getTuitionBudget());
        if (req.getAcceptPrivate() != null) profile.setAcceptPrivate(req.getAcceptPrivate());
        if (req.getAcceptSinoForeign() != null) profile.setAcceptSinoForeign(req.getAcceptSinoForeign());
        // VolunteerSortEngine 用的 4 个权重字段透传
        profile.setMajorPriority(req.getMajorPriority());
        profile.setSchoolPriority(req.getSchoolPriority());
        profile.setCityPriority(req.getCityPriority());
        profile.setEmploymentPriority(req.getEmploymentPriority());
        return profile;
    }

    private University getUniversity(String schoolId, Map<String, University> cache) {
        if (schoolId == null || schoolId.isBlank()) return null;
        return cache.computeIfAbsent(schoolId, scoreLineService::getUniversityById);
    }

    private boolean matchesConstraints(University uni, String schoolName, String majorName, PreferenceProfile profile) {
        String nature = uni != null && uni.getNatureName() != null ? uni.getNatureName() : "";
        List<String> tags = uni != null && uni.getTags() != null ? uni.getTags() : List.of();
        String schoolText = schoolName != null ? schoolName : "";
        String majorText = majorName != null ? majorName : "";

        boolean isPrivate = "民办".equals(nature) || tags.contains("民办");
        boolean isSinoForeign = tags.contains("中外合作办学")
                || tags.contains("内地与港澳台合作办学")
                || nature.contains("中外合作")
                || schoolText.contains("国际学院")
                || schoolText.contains("中外合作")
                || schoolText.contains("港澳台")
                || majorText.contains("中外合作")
                || majorText.contains("联合培养");

        if (!profile.isAcceptPrivate() && isPrivate) return false;
        if (!profile.isAcceptSinoForeign() && isSinoForeign) return false;

        return switch (profile.getTuitionBudget()) {
            case "低预算" -> !isPrivate && !isSinoForeign;
            case "均衡预算" -> !isSinoForeign;
            default -> true;
        };
    }

    private boolean isLegacySubjectFallback(String actualSubjectType, String requestedSubjectType) {
        String actual = safeText(actualSubjectType);
        String requested = safeText(requestedSubjectType);
        return ("物理类".equals(requested) && "理科".equals(actual))
                || ("历史类".equals(requested) && "文科".equals(actual));
    }

    private String specialTypeReason(CandidateFilterEngine.FilterCriteria criteria,
                                     University uni, String schoolName, String majorName, String batch) {
        String recruitType = classifyRecruitType(uni, schoolName, majorName, batch);
        if (criteria == null) {
            return RecruitTypeClassifier.isExclusiveFromMainList(recruitType)
                    ? RecruitTypeClassifier.exclusionReason(recruitType)
                    : "";
        }
        return RecruitTypeClassifier.exclusionReason(
                recruitType, criteria.getBatchCode(), criteria.getCandidateType(), batch);
    }

    private String specialTypeReason(University uni, String schoolName, String majorName, String batch) {
        return specialTypeReason(null, uni, schoolName, majorName, batch);
    }

    private String classifyRecruitType(University uni, String schoolName, String majorName, String batch) {
        List<String> tags = uni != null && uni.getTags() != null ? uni.getTags() : List.of();
        return RecruitTypeClassifier.classify(schoolName, majorName, batch, tags);
    }

    private void applyUniversityInfo(VolunteerItem item, University university) {
        if (university == null) {
            item.setProvince("");
            item.setCity("");
            item.setTags(List.of());
            item.setSchoolNature("");
            return;
        }
        item.setProvince(university.getProvince() != null ? university.getProvince() : "");
        item.setCity(university.getCity() != null ? university.getCity() : "");
        item.setTags(university.getTags() != null ? university.getTags() : List.of());
        item.setSchoolNature(university.getNatureName() != null ? university.getNatureName() : "");
    }

    private String resolveConfidence(Integer year, boolean majorLevel) {
        if (year == null) return "需复核";
        if (majorLevel && year >= 2024) return "高可信";
        if (majorLevel) return "中可信";
        return year >= 2024 ? "中可信" : "需复核";
    }

    /**
     * 整体志愿顺序固定为 冲→稳→保→垫（任何策略模式都不变），
     * 梯度内部排序由 {@link VolunteerSortEngine} 按 PreferenceWeights 加权产出。
     *
     * <p>保守型 / 均衡型 / 冲刺型仅影响梯度内 chance/confidence/risk 三个系数和
     * major/school/city/employment 四个权重的微调，不会再翻转整体顺序。</p>
     */
    private void applyPlanOrdering(List<VolunteerItem> items, PreferenceProfile profile) {
        if (items == null || items.isEmpty()) return;
        VolunteerSortEngine.PreferenceWeights weights = VolunteerSortEngine.PreferenceWeights.fromStrategyAndProfile(
                profile == null ? "均衡型" : profile.getStrategyMode(),
                profile == null ? null : profile.getDecisionPriority(),
                profile == null ? null : profile.getCareerGoal(),
                profile == null ? null : profile.getMajorPriority(),
                profile == null ? null : profile.getSchoolPriority(),
                profile == null ? null : profile.getCityPriority(),
                profile == null ? null : profile.getEmploymentPriority());
        List<VolunteerItem> sorted = volunteerSortEngine.sort(new ArrayList<>(items), weights);
        items.clear();
        items.addAll(sorted);
        // sortEngine 已经写入 recommendationScore + index，本方法不再额外处理
    }

    private int compareByPreference(VolunteerItem a, VolunteerItem b, PreferenceProfile profile) {
        double aScore = rankingScore(a, profile);
        double bScore = rankingScore(b, profile);
        int diff = Double.compare(bScore, aScore);
        if (diff != 0) return diff;
        return Integer.compare(a.getHistoryMinRank(), b.getHistoryMinRank());
    }

    private double rankingScore(VolunteerItem item, PreferenceProfile profile) {
        double score = item.getDataConfidenceScore() * 0.55
                + referenceFitBonus(item.getReferenceFitLevel())
                + (item.getMatchScore()) * 0.35
                + (item.getAdmissionProb()) * 0.12
                + item.getPrecisionScore() * 0.18;
        score += planSignalScore(item);
        score += rankGapScore(item);
        if ("学校优先".equals(profile.getDecisionPriority())) {
            score += hasEliteTag(item) ? 8 : 0;
        } else {
            score += "专业级".equals(item.getDataSourceType()) ? 10 : 0;
        }

        if ("保守型".equals(profile.getStrategyMode())) {
            score += "green".equals(item.getRiskColor()) ? 14 : "yellow".equals(item.getRiskColor()) ? 7 : 0;
            score += "较高".equals(item.getReferenceFitLevel()) ? 10 : 0;
        } else if ("冲刺型".equals(profile.getStrategyMode())) {
            score += item.getGradient().equals("冲") ? 12 : item.getGradient().equals("稳") ? 6 : 0;
            score += "偏低".equals(item.getReferenceFitLevel()) ? 4 : 0;
        }

        if ("就业优先".equals(profile.getCareerGoal())) {
            score += isCareerPractical(item) ? 8 : 0;
        } else if ("升学优先".equals(profile.getCareerGoal())) {
            score += hasEliteTag(item) ? 10 : 0;
        } else if ("城市机会优先".equals(profile.getCareerGoal())) {
            score += isOpportunityCity(item) ? 10 : 0;
        }

        return score;
    }

    private double planSignalScore(VolunteerItem item) {
        String trend = safeText(item.getPlanTrend());
        double score = planAvailabilitySelectionScore(item.getLatestPlanCount());
        if ("扩招".equals(trend)) {
            score += 6;
        } else if ("缩招".equals(trend)) {
            score -= 8;
        } else if ("基本稳定".equals(trend)) {
            score += 3;
        } else if ("计划数暂缺".equals(trend)) {
            score -= 6;
        }
        if (item.getPlanExpansionIndex() > 0) {
            score += Math.max(-8, Math.min(8, (item.getPlanExpansionIndex() - 100) / 8.0));
        }
        if (item.getSchoolEnrollmentIndex() > 0) {
            score += Math.max(-6, Math.min(6, (item.getSchoolEnrollmentIndex() - 55) / 7.0));
        }
        return score;
    }

    private double rankGapScore(VolunteerItem item) {
        double ratio = item.getRankGapRatio();
        if ("冲".equals(item.getGradient())) {
            return ratio >= -15 && ratio <= -2 ? 6 : -3;
        }
        if ("稳".equals(item.getGradient())) {
            return ratio >= -6 && ratio <= 12 ? 7 : -2;
        }
        if ("保".equals(item.getGradient()) || "垫".equals(item.getGradient())) {
            return ratio >= 8 ? 7 : -2;
        }
        return 0;
    }

    private double referenceFitBonus(String level) {
        return switch (safeText(level)) {
            case "较高" -> 24;
            case "中等" -> 14;
            case "偏低" -> 4;
            default -> -10;
        };
    }

    private boolean hasEliteTag(VolunteerItem item) {
        return item.getTags() != null
                && (item.getTags().contains("985")
                || item.getTags().contains("211")
                || item.getTags().contains("双一流"));
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean isCareerPractical(VolunteerItem item) {
        String major = item.getMajorName() != null ? item.getMajorName() : "";
        return Stream.of("计算机", "软件", "数据", "电子", "自动化", "电气", "护理", "临床", "口腔", "师范", "法学", "会计")
                .anyMatch(major::contains);
    }

    private boolean isOpportunityCity(VolunteerItem item) {
        String city = item.getCity() != null ? item.getCity() : "";
        String province = item.getProvince() != null ? item.getProvince() : "";
        return Stream.of("北京", "上海", "广州", "深圳", "杭州", "南京", "武汉", "成都", "西安", "重庆", "苏州")
                .anyMatch(v -> city.contains(v) || province.contains(v));
    }

    private void enrichWithDecisionSupport(List<VolunteerItem> items, PreferenceProfile profile) {
        for (VolunteerItem item : items) {
            item.setRecommendReason(buildRecommendReason(item, profile));
            item.setRiskReason(buildRiskReason(item, profile));
            item.setAlternativeOption(buildAlternativeOption(item));
            item.setSuitableFor(buildSuitableFor(item, profile));
        }
    }

    private String buildRecommendReason(VolunteerItem item, PreferenceProfile profile) {
        List<String> reasons = new ArrayList<>();
        if ((item.getMatchScore()) >= 60) {
            reasons.add("与你填写的专业/地区偏好高度匹配");
        }
        if ("专业级".equals(item.getDataSourceType())) {
            reasons.add("基于专业级录取数据，参考价值更高");
        }
        if ("扩招".equals(item.getPlanTrend())) {
            reasons.add("最近招生计划有扩招信号，安全边际相对改善");
        } else if ("基本稳定".equals(item.getPlanTrend()) && item.getLatestPlanCount() != null && item.getLatestPlanCount() >= 15) {
            reasons.add("招生计划相对稳定，且计划数具备一定规模");
        }
        if (item.getSchoolEnrollmentIndex() >= 75) {
            reasons.add("院校招生供给指数较高，同梯度内供给更充分");
        }
        if (item.getPrecisionScore() >= 82) {
            reasons.add("推荐依据完整度较高，位次、计划和来源链路更清楚");
        }
        if ("较高".equals(item.getReferenceFitLevel())) {
            reasons.add("参考匹配等级较高，适合放入当前梯度草稿");
        } else if ("中等".equals(item.getReferenceFitLevel())) {
            reasons.add("参考匹配处于中等区间，建议与同梯度院校横向比较");
        }
        if ("学校优先".equals(profile.getDecisionPriority()) && item.getTags() != null
                && (item.getTags().contains("985") || item.getTags().contains("211") || item.getTags().contains("双一流"))) {
            reasons.add("学校平台资源更强，适合学校优先策略");
        }
        if ("专业优先".equals(profile.getDecisionPriority()) && "专业级".equals(item.getDataSourceType())) {
            reasons.add("专业方向明确，更适合专业优先策略");
        }
        if ("城市机会优先".equals(profile.getCareerGoal()) && isOpportunityCity(item)) {
            reasons.add("所在城市资源和实习机会相对更集中");
        }
        return reasons.isEmpty() ? "可作为同梯度中的备选项，建议与相近院校一起对比后决定" : String.join("；", reasons);
    }

    private String buildRiskReason(VolunteerItem item, PreferenceProfile profile) {
        List<String> risks = new ArrayList<>();
        if ("院校级".equals(item.getDataSourceType())) {
            risks.add("该条目为院校级回退数据，专业精度低于专业级数据");
        }
        if ("red".equals(item.getRiskColor()) || item.getAdmissionProb() < 40) {
            risks.add("波动偏大，存在滑档或专业落差风险");
        }
        if (item.getTrend() != null && item.getTrend().contains("加剧")) {
            risks.add("近年竞争有加剧迹象");
        }
        if ("缩招".equals(item.getPlanTrend())) {
            risks.add("最近招生计划有缩招信号，需下调乐观判断");
        } else if ("计划数暂缺".equals(item.getPlanTrend())) {
            risks.add("招生计划数暂缺，无法评估计划变化对位次的影响");
        } else if (item.getLatestPlanCount() != null && item.getLatestPlanCount() > 0 && item.getLatestPlanCount() <= 3) {
            risks.add("招生计划数较少，录取位次可能更易波动");
        }
        if (item.getPlanExpansionIndex() > 0 && item.getPlanExpansionIndex() <= 85) {
            risks.add("扩招指数偏低，存在缩招或供给收紧信号");
        }
        if (item.getSchoolEnrollmentIndex() > 0 && item.getSchoolEnrollmentIndex() < 45) {
            risks.add("院校招生供给指数偏低，小计划数可能放大偶然波动");
        }
        if (item.getPrecisionScore() > 0 && item.getPrecisionScore() < 55) {
            risks.add("推荐精度偏低，需优先核验计划数、位次来源和专业目录");
        }
        if (!profile.isAcceptSinoForeign() && isSinoForeignLike(item)) {
            risks.add("名称中含合作办学特征，需再次核验招生章程");
        }
        return risks.isEmpty() ? "整体风险可控，但仍需结合招生章程和专业限制复核" : String.join("；", risks);
    }

    private String buildAlternativeOption(VolunteerItem item) {
        if ("冲".equals(item.getGradient())) {
            return "如果担心风险偏高，可优先关注同城或同专业方向中的“稳”档院校";
        }
        if ("稳".equals(item.getGradient())) {
            return "可准备 1 所同层次但位次更宽松的备选院校，避免临场调整被动";
        }
        if ("保".equals(item.getGradient()) || "垫".equals(item.getGradient())) {
            return "若更看重学校层次，可搭配 1 所专业相近但地区稍弱的院校进行替换";
        }
        return "建议与同梯度、同专业方向院校横向比较后再排序";
    }

    private String buildSuitableFor(VolunteerItem item, PreferenceProfile profile) {
        if ("学校优先".equals(profile.getDecisionPriority()) && item.getTags() != null
                && (item.getTags().contains("985") || item.getTags().contains("211") || item.getTags().contains("双一流"))) {
            return "更适合看重学校平台、升学资源和城市曝光度的考生";
        }
        if ("专业级".equals(item.getDataSourceType())) {
            return "更适合已经明确专业方向、希望减少专业偏差的考生";
        }
        if ("保守型".equals(profile.getStrategyMode())) {
            return "更适合作为稳妥保底选择，帮助你把方案底座做实";
        }
        return "适合作为梯度中的功能位志愿，和相近学校一起整体考虑";
    }

    private boolean isSinoForeignLike(VolunteerItem item) {
        String name = (item.getUniversityName() != null ? item.getUniversityName() : "")
                + (item.getMajorName() != null ? item.getMajorName() : "");
        return name.contains("中外合作") || name.contains("联合学院") || name.contains("国际");
    }

    private void applyReliabilityExplanations(List<VolunteerItem> items, int studentRank) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (VolunteerItem item : items) {
            List<String> flags = item.getReviewFlags() != null ? item.getReviewFlags() : List.of();
            boolean special = flags.contains("military_police")
                    || flags.contains("art_sports")
                    || flags.contains("special_admission");
            item.setSpecialTypeFlag(special);
            if (special) {
                item.setExcludedReason("疑似特殊类型招生，必须按当年专业目录和招生章程人工核验");
            }
            item.setReferenceFitLevel(resolveReferenceFitLevel(item));
            item.setDataConfidenceScore(calcDataConfidenceScore(item, flags));
            item.setPrecisionScore(calcPrecisionScore(item, flags));
            item.setPrecisionLabel(resolvePrecisionLabel(item.getPrecisionScore()));
            item.setPrecisionNote(buildPrecisionNote(item));
            item.setAlgorithmExplanation(buildAlgorithmExplanation(item, studentRank, flags));
        }
    }

    private String resolveReferenceFitLevel(VolunteerItem item) {
        if (item == null || !item.isWithinConfiguredRange()) {
            return "需复核";
        }
        double prob = item.getAdmissionProb();
        String probLevel = safeText(item.getProbLevel());
        if (probLevel.contains("数据不足") || prob <= 0) {
            return switch (safeText(item.getGradient())) {
                case "保", "垫" -> "较高";
                case "稳" -> "中等";
                default -> "偏低";
            };
        }
        if (prob >= 70) return "较高";
        if (prob >= 45) return "中等";
        return "偏低";
    }

    private int calcDataConfidenceScore(VolunteerItem item, List<String> flags) {
        int score = 0;
        score += "专业级".equals(item.getDataSourceType()) ? 34 : 12;
        Integer year = item.getReferenceYear();
        if (year != null && year >= 2025) score += 20;
        else if (year != null && year >= 2024) score += 16;
        else if (year != null && year >= 2023) score += 9;
        else score += 4;

        score += switch (safeText(item.getSubjectRequirementSource())) {
            case "official_requirement" -> 24;
            case "score_line" -> 12;
            case "inferred" -> 4;
            default -> 0;
        };
        int historyCount = item.getHistoryRecords() == null ? 0 : item.getHistoryRecords().size();
        score += Math.min(12, historyCount * 4);
        if (item.getLatestPlanCount() != null && item.getLatestPlanCount() > 0) {
            score += Math.min(8, Math.max(2, item.getLatestPlanCount() / 5));
        } else {
            score -= 6;
        }
        if ("缩招".equals(item.getPlanTrend())) score -= 6;
        else if ("扩招".equals(item.getPlanTrend()) || "基本稳定".equals(item.getPlanTrend())) score += 4;
        if (item.getPlanExpansionIndex() >= 115) score += 3;
        else if (item.getPlanExpansionIndex() > 0 && item.getPlanExpansionIndex() <= 85) score -= 5;
        if (item.getSchoolEnrollmentIndex() >= 75) score += 4;
        else if (item.getSchoolEnrollmentIndex() > 0 && item.getSchoolEnrollmentIndex() < 45) score -= 5;
        if (!item.isLegacySubjectFallback()) score += 8;
        if (!item.isWithinConfiguredRange()) score -= 20;
        if (item.isNeedsManualReview()) score -= 10;
        if (flags.contains("non_major_level")) score -= 12;
        if (flags.contains("missing_subject_requirement") || flags.contains("inferred_subject_requirement")) score -= 10;
        if (item.isSpecialTypeFlag()) score -= 25;
        return Math.max(0, Math.min(100, score));
    }

    private int calcPrecisionScore(VolunteerItem item, List<String> flags) {
        double score = item.getDataConfidenceScore() * 0.45;
        score += switch (safeText(item.getReferenceFitLevel())) {
            case "较高" -> 20;
            case "中等" -> 13;
            case "偏低" -> 5;
            default -> 0;
        };
        score += item.getSchoolEnrollmentIndex() * 0.20;
        if (item.getPlanExpansionIndex() > 0) {
            score += Math.max(-8, Math.min(8, (item.getPlanExpansionIndex() - 100) / 7.0));
        } else {
            score -= 8;
        }
        if ("green".equals(item.getRiskColor())) score += 6;
        else if ("yellow".equals(item.getRiskColor())) score += 1;
        else if ("red".equals(item.getRiskColor())) score -= 10;
        if (!item.isWithinConfiguredRange()) score -= 15;
        if (item.isNeedsManualReview()) score -= 8;
        if (flags.contains("non_major_level")) score -= 6;
        if (item.getLatestPlanCount() != null && item.getLatestPlanCount() > 0 && item.getLatestPlanCount() <= 3) {
            score -= 6;
        }
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    private String resolvePrecisionLabel(int precisionScore) {
        if (precisionScore >= 82) return "精度较高";
        if (precisionScore >= 65) return "精度中等";
        if (precisionScore >= 45) return "精度偏低";
        return "必须复核";
    }

    private String buildPrecisionNote(VolunteerItem item) {
        return String.format("精度分%d：综合数据置信度%d、参考匹配%s、扩招指数%s、招生供给%s；该分数只衡量推荐依据完整度，不代表录取承诺。",
                item.getPrecisionScore(),
                item.getDataConfidenceScore(),
                safeText(item.getReferenceFitLevel()).isBlank() ? "待评估" : item.getReferenceFitLevel(),
                item.getPlanExpansionIndex() > 0 ? String.format("%.1f", item.getPlanExpansionIndex()) : "待核验",
                item.getSchoolEnrollmentIndex() > 0 ? String.format("%.0f", item.getSchoolEnrollmentIndex()) : "待核验");
    }

    private String buildAlgorithmExplanation(VolunteerItem item, int studentRank, List<String> flags) {
        List<String> parts = new ArrayList<>();
        parts.add(String.format("按本次%s档位次区间筛入，参考位次第%,d位，与你填写的官方位次相差%,d位",
                safeText(item.getGradient()),
                item.getHistoryMinRank(),
                item.getHistoryMinRank() - studentRank));
        if (item.getRankGapRatio() != 0) {
            parts.add(String.format("位次差约%.1f%%，正数代表历史最低位次更宽松", item.getRankGapRatio()));
        }
        parts.add(String.format("数据口径为%s，参考年份%d年，数据置信度%d分",
                safeText(item.getDataSourceType()).isBlank() ? "待复核" : item.getDataSourceType(),
                item.getReferenceYear(),
                item.getDataConfidenceScore()));
        if (item.getLatestPlanCount() != null && item.getLatestPlanCount() > 0) {
            parts.add(String.format("最近计划数%d人，计划趋势：%s",
                    item.getLatestPlanCount(), safeText(item.getPlanTrend()).isBlank() ? "待观察" : item.getPlanTrend()));
        } else {
            parts.add("招生计划数暂缺，计划因素已降权");
        }
        if (item.getPlanRiskNote() != null && !item.getPlanRiskNote().isBlank()) {
            parts.add(item.getPlanRiskNote());
        }
        if (item.getPlanExpansionIndex() > 0) {
            parts.add(String.format("当年实际扩招指数%.1f（%s）",
                    item.getPlanExpansionIndex(), safeText(item.getPlanExpansionLabel())));
        }
        if (item.getSchoolEnrollmentIndex() > 0) {
            parts.add(String.format("院校招生供给指数%.0f（%s）",
                    item.getSchoolEnrollmentIndex(), safeText(item.getSchoolEnrollmentLabel())));
        }
        parts.add("选科要求来源：" + requirementSourceText(item.getSubjectRequirementSource()));
        if (item.isLegacySubjectFallback()) {
            parts.add("使用了2021-2023文理科历史数据作兼容参考，已降权处理");
        }
        if (item.getHistoryRecords() != null && !item.getHistoryRecords().isEmpty()) {
            parts.add(String.format("已展示%d条近年录取记录，专业级优先，缺失时回退院校级", item.getHistoryRecords().size()));
            boolean missingPlanCount = item.getHistoryRecords().stream()
                    .noneMatch(record -> record.getPlanCount() != null && record.getPlanCount() > 0);
            if (missingPlanCount) {
                parts.add("招生计划数暂缺，需核对当年官方专业目录");
            }
        }
        if (flags.contains("missing_subject_requirement") || flags.contains("inferred_subject_requirement")
                || flags.contains("non_major_level") || item.isSpecialTypeFlag()) {
            parts.add("存在需人工复核项，请以贵州省招生考试院、学校招生章程和专业目录为准");
        }
        parts.add("参考匹配等级：" + safeText(item.getReferenceFitLevel()));
        if (item.getPrecisionNote() != null && !item.getPrecisionNote().isBlank()) {
            parts.add(item.getPrecisionNote());
        }
        return String.join("；", parts) + "。";
    }

    private String requirementSourceText(String source) {
        return switch (safeText(source)) {
            case "official_requirement" -> "官方选科要求库";
            case "score_line" -> "录取线字段";
            case "inferred" -> "专业名称推断";
            default -> "缺失，需复核";
        };
    }

    private void cachePlanResult(String resultKey, PlanResult result) {
        String safetyCode = result.getSafetyCode();
        String accessKey = result.getAccessKey();
        try {
            result.setSafetyCode("");
            result.setAccessKey("");
            String payload = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(resultKey, payload, Duration.ofSeconds(generateCacheSeconds));
        } catch (Exception e) {
            log.warn("缓存志愿方案失败: key={}", resultKey, e);
        } finally {
            result.setSafetyCode(safetyCode);
            result.setAccessKey(accessKey);
        }
    }

    private PlanResult readCachedPlanResult(String payload, String safetyCode) {
        try {
            PlanResult result = objectMapper.readValue(payload, PlanResult.class);
            result.setSafetyCode(safetyCode);
            result.setAccessKey(safetyCode);
            return result;
        } catch (Exception e) {
            throw new BizException("缓存方案解析失败");
        }
    }

    private PlanResult waitForGenerateResult(String resultKey, String safetyCode) {
        long deadline = System.currentTimeMillis() + generateWaitMillis;
        while (System.currentTimeMillis() < deadline) {
            String payload = stringRedisTemplate.opsForValue().get(resultKey);
            if (payload != null && !payload.isBlank()) {
                return readCachedPlanResult(payload, safetyCode);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private void releaseGenerateLock(String lockKey, String lockValue) {
        try {
            String current = stringRedisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                stringRedisTemplate.delete(lockKey);
            }
        } catch (Exception e) {
            log.warn("释放志愿生成锁失败: key={}", lockKey, e);
        }
    }

    private String buildGenerateFingerprint(GenerateRequest req, Long userId, String clientIp) {
        try {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("scope", userId != null && userId > 0 ? "u:" + userId : "ip:" + safeTrim(clientIp));
            normalized.put("provinceCode", normalizeProvinceCode(req.getProvinceCode()));
            normalized.put("year", req.getYear() == null ? admissionYearService.getActiveAdmissionYear() : req.getYear());
            normalized.put("batchCode", safeTrim(req.getBatchCode()));
            normalized.put("candidateType", safeTrim(req.getCandidateType()));
            normalized.put("totalScore", req.getTotalScore());
            normalized.put("provinceRank", req.getProvinceRank());
            normalized.put("firstSubject", safeTrim(req.getFirstSubject()));
            normalized.put("resubjects", normalizeList(req.getResubjects()));
            normalized.put("preferredMajors", normalizeList(req.getPreferredMajors()));
            normalized.put("preferredRegions", normalizeList(req.getPreferredRegions()));
            normalized.put("strategyMode", safeTrim(req.getStrategyMode()));
            normalized.put("decisionPriority", safeTrim(req.getDecisionPriority()));
            normalized.put("careerGoal", safeTrim(req.getCareerGoal()));
            normalized.put("tuitionBudget", safeTrim(req.getTuitionBudget()));
            normalized.put("acceptPrivate", Boolean.TRUE.equals(req.getAcceptPrivate()));
            normalized.put("acceptSinoForeign", Boolean.TRUE.equals(req.getAcceptSinoForeign()));
            normalized.put("disclaimerVersion", safeTrim(req.getDisclaimerVersion()));
            normalized.put("gradientRanges", normalizeGradientRanges(req.getGradientRanges()));
            normalized.put("safetyCodeFingerprint", safetyCodeService.fingerprint(req.getSafetyCode()));
            String payload = objectMapper.writeValueAsString(normalized);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("请求指纹生成失败");
        }
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private Map<String, List<Integer>> normalizeGradientRanges(Map<String, GradientRangeInput> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Integer>> normalized = new TreeMap<>();
        for (Map.Entry<String, GradientRangeInput> entry : ranges.entrySet()) {
            String gradient = normalizeGradientKey(entry.getKey());
            GradientRangeInput input = entry.getValue();
            if (gradient.isBlank() || input == null) {
                continue;
            }
            normalized.put(gradient, List.of(
                    input.getRankOffsetMin() == null ? 0 : input.getRankOffsetMin(),
                    input.getRankOffsetMax() == null ? 0 : input.getRankOffsetMax()));
        }
        return normalized;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeProvinceCode(String provinceCode) {
        String value = safeTrim(provinceCode).toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            return ProvincePolicyService.GZ;
        }
        if (!ProvincePolicyService.GZ.equals(value)) {
            throw new BizException("贵州 96 专业志愿生成接口仅支持 provinceCode=GZ；院校专业组省份请走专业组策略。");
        }
        return value;
    }

    public String buildPlanAccessKey(Long planId) {
        return safetyCodeService.buildLegacyAccessKey(planId);
    }

    public boolean isValidPlanAccessKey(Long planId, String accessKey) {
        PlanHistory history = planHistoryMapper.selectById(planId);
        return history != null && safetyCodeService.verify(planId, accessKey, history.getSafetyCodeHash());
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("访问密钥生成失败");
        }
    }

    /**
     * 查询用户历史方案
     */
    public List<PlanResult> getHistory(Long userId) {
        List<PlanHistory> histories = planHistoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlanHistory>()
                        .eq(PlanHistory::getUserId, userId)
                        .orderByDesc(PlanHistory::getCreatedAt)
                        .last("LIMIT 10"));

        return histories.stream().map(this::toPlanResult).collect(Collectors.toList());
    }

    public PlanHistory getPlanById(Long planId) {
        return planHistoryMapper.selectById(planId);
    }

    public PlanResult getPlanResult(Long planId, String accessKey) {
        if (planId == null) {
            return null;
        }
        PlanHistory history = planHistoryMapper.selectById(planId);
        if (history == null || !safetyCodeService.verify(planId, accessKey, history.getSafetyCodeHash())) {
            return null;
        }
        return toPlanResult(history);
    }

    public PlanResult getPlanResultForInternal(Long planId) {
        if (planId == null) {
            return null;
        }
        PlanHistory history = planHistoryMapper.selectById(planId);
        return history == null ? null : toPlanResult(history);
    }

    private PlanResult toPlanResult(PlanHistory h) {
        PlanResult r = new PlanResult();
        r.setId(h.getId());
        r.setAccessKey(safeTrim(h.getSafetyCodeHash()).isBlank() ? buildPlanAccessKey(h.getId()) : "");
        String provinceCode = safeTrim(h.getProvinceCode()).isBlank()
                ? ProvincePolicyService.GZ
                : safeTrim(h.getProvinceCode()).toUpperCase(Locale.ROOT);
        String unitType = safeTrim(h.getVolunteerUnitType()).isBlank()
                ? ProvincePolicyService.UNIT_MAJOR_96
                : safeTrim(h.getVolunteerUnitType());
        r.setProvinceCode(provinceCode);
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(provinceCode);
        r.setProvinceName(policy.getProvinceName());
        r.setVolunteerUnitType(unitType);
        r.setVolunteerUnitLabel(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45.equals(unitType)
                ? "院校专业组" : "专业（类）+ 院校");
        r.setTargetBatch(!safeTrim(h.getTargetBatch()).isBlank()
                ? h.getTargetBatch()
                : policy.getTargetBatch());
        r.setTargetCount(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45.equals(unitType) ? policy.getTargetCount() : TOTAL_COUNT);
        r.setTotalScore(h.getTotalScore() != null ? h.getTotalScore() : 0);
        r.setProvinceRank(h.getProvinceRank() != null ? h.getProvinceRank() : 0);
        r.setFirstSubject(h.getFirstSubject());
        try {
            r.setResubjects(parseStringList(h.getResubjects()));
            r.setPreferredMajors(parseStringList(h.getPreferredMajors()));
            r.setPreferredRegions(parseStringList(h.getPreferredRegions()));
            if (h.getPlanJson() != null && !h.getPlanJson().isBlank()) {
                r.setItems(objectMapper.readValue(h.getPlanJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, VolunteerItem.class)));
                normalizePublicChanceFields(r.getItems(), r.getProvinceRank());
            } else {
                r.setItems(List.of());
            }
            if (h.getManualReviewJson() != null && !h.getManualReviewJson().isBlank()) {
                r.setManualReviewItems(objectMapper.readValue(h.getManualReviewJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ManualReviewItem.class)));
            }
            if (h.getMetricsJson() != null && !h.getMetricsJson().isBlank()) {
                r.setMetrics(objectMapper.readValue(h.getMetricsJson(), PlanMetrics.class));
                if (r.getMetrics() != null && r.getMetrics().getTargetCount() > 0) {
                    r.setTargetCount(r.getMetrics().getTargetCount());
                }
            }
            if (h.getRequestSnapshotJson() != null && !h.getRequestSnapshotJson().isBlank()) {
                Map<String, Object> snapshot = objectMapper.readValue(h.getRequestSnapshotJson(),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                Object targetCount = snapshot.get("targetCount");
                if (targetCount instanceof Number number && number.intValue() > 0) {
                    r.setTargetCount(number.intValue());
                }
                Object rangeSummary = snapshot.get("gradientRangeSummary");
                if (rangeSummary != null) {
                    r.setGradientRangeSummary(objectMapper.convertValue(rangeSummary, GradientRangeSummary.class));
                }
                Object rankEstimate = snapshot.get("rankEstimate");
                if (rankEstimate != null) {
                    r.setRankEstimate(objectMapper.convertValue(rankEstimate, RankEstimateSummary.class));
                }
            }
        } catch (Exception e) {
            log.warn("反序列化历史方案失败: planId={}", h.getId(), e);
            if (r.getResubjects() == null) r.setResubjects(List.of());
            if (r.getPreferredMajors() == null) r.setPreferredMajors(List.of());
            if (r.getPreferredRegions() == null) r.setPreferredRegions(List.of());
            if (r.getItems() == null) r.setItems(List.of());
        }
        r.setStrategyMode(h.getStrategyMode());
        r.setDecisionPriority(h.getDecisionPriority());
        r.setCareerGoal(h.getCareerGoal());
        r.setTuitionBudget(h.getTuitionBudget());
        if (h.getAcceptPrivate() != null) r.setAcceptPrivate(h.getAcceptPrivate() == 1);
        if (h.getAcceptSinoForeign() != null) r.setAcceptSinoForeign(h.getAcceptSinoForeign() == 1);
        if (h.getDataQualityWarning() != null && !h.getDataQualityWarning().isBlank()) {
            r.setDataQualityWarning(h.getDataQualityWarning());
        } else if (r.getItems() != null && r.getItems().size() < r.getTargetCount()) {
            r.setDataQualityWarning(String.format(
                    "当前方案仅包含%d个志愿项，未达到%d个。请结合官方数据复核后再使用。",
                    r.getItems().size(), r.getTargetCount()));
        }
        r.setReferenceProbabilityNotice(REFERENCE_PROBABILITY_NOTICE);
        r.setCreatedAt(h.getCreatedAt() != null ? h.getCreatedAt().toString() : "");
        r.setAdvisorAdvice(buildAdvisorAdvice(r));
        return r;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> list = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return list != null ? list : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 意向匹配打分：根据考生填写的意向专业/地区，为每个志愿项计算匹配度
     */
    private void enrichWithPreference(List<VolunteerItem> items, List<String> prefMajors, List<String> prefRegions) {
        // 预加载学校省份信息
        Map<String, String> schoolProvinceCache = new HashMap<>();

        for (VolunteerItem item : items) {
            int score = 0;
            boolean majorMatch = false;
            boolean regionMatch = false;

            // 专业关键词匹配（模糊匹配）
            if (!prefMajors.isEmpty() && item.getMajorName() != null) {
                String majorLower = item.getMajorName().toLowerCase();
                for (String pref : prefMajors) {
                    if (pref != null && !pref.isBlank() && majorLower.contains(pref.trim().toLowerCase())) {
                        majorMatch = true;
                        score += 60;
                        break;
                    }
                }
                // 大类模糊匹配（如 "计算机" 匹配 "计算机科学与技术"、"计算机类"）
                if (!majorMatch) {
                    for (String pref : prefMajors) {
                        if (pref != null && !pref.isBlank()) {
                            String p = pref.trim().toLowerCase();
                            // 专业名包含关键词 或 关键词包含专业名的核心部分
                            if (majorLower.contains(p) || (p.length() >= 2 && fuzzyMajorMatch(majorLower, p))) {
                                majorMatch = true;
                                score += 40; // 模糊匹配分数稍低
                                break;
                            }
                        }
                    }
                }
            }

            // 地区匹配
            if (!prefRegions.isEmpty() && item.getSchoolId() != null) {
                String province = schoolProvinceCache.computeIfAbsent(item.getSchoolId(), sid -> {
                    try {
                        com.gzly.entity.University uni = scoreLineService.getUniversityById(sid);
                        return uni != null && uni.getProvince() != null ? uni.getProvince() : "";
                    } catch (Exception e) { return ""; }
                });
                String city = item.getCity() != null ? item.getCity() : "";
                for (String pref : prefRegions) {
                    if (pref != null && !pref.isBlank()) {
                        String p = pref.trim();
                        if (province.contains(p) || city.contains(p) ||
                            (item.getUniversityName() != null && item.getUniversityName().contains(p))) {
                            regionMatch = true;
                            score += 40;
                            break;
                        }
                    }
                }
            }

            // 生成标签
            if (majorMatch && regionMatch) {
                item.setMatchTag("双匹配");
            } else if (majorMatch) {
                item.setMatchTag("专业匹配");
            } else if (regionMatch) {
                item.setMatchTag("地区匹配");
            } else {
                item.setMatchTag("");
            }
            item.setMatchScore(Math.min(score, 100));
        }
    }

    /**
     * 专业大类模糊匹配
     */
    private boolean fuzzyMajorMatch(String majorName, String keyword) {
        // 专业大类映射表
        Map<String, List<String>> categoryMap = Map.ofEntries(
            Map.entry("计算机", List.of("软件", "人工智能", "数据科学", "大数据", "网络工程", "信息安全", "智能科学")),
            Map.entry("电子", List.of("通信", "电气", "自动化", "微电子", "光电", "集成电路")),
            Map.entry("机械", List.of("车辆", "工业设计", "智能制造", "机器人")),
            Map.entry("医学", List.of("临床", "口腔", "护理", "药学", "中医", "针灸", "康复", "医学影像", "预防医学")),
            Map.entry("法学", List.of("法律", "知识产权", "政治学")),
            Map.entry("经济", List.of("金融", "财务", "会计", "审计", "税收", "保险", "投资")),
            Map.entry("管理", List.of("工商", "市场营销", "人力资源", "物流", "电子商务", "旅游")),
            Map.entry("建筑", List.of("土木", "城乡规划", "风景园林", "工程管理")),
            Map.entry("教育", List.of("学前", "小学教育", "特殊教育", "体育教育")),
            Map.entry("外语", List.of("英语", "日语", "翻译", "商务英语", "法语", "德语", "西班牙语")),
            Map.entry("设计", List.of("视觉传达", "环境设计", "产品设计", "数字媒体艺术", "动画")),
            Map.entry("新闻", List.of("传播", "广告", "广播电视", "网络与新媒体"))
        );

        for (Map.Entry<String, List<String>> entry : categoryMap.entrySet()) {
            String cat = entry.getKey();
            List<String> related = entry.getValue();
            boolean keywordInCat = keyword.contains(cat) || cat.contains(keyword);
            if (!keywordInCat) {
                for (String r : related) {
                    if (keyword.contains(r) || r.contains(keyword)) { keywordInCat = true; break; }
                }
            }
            if (keywordInCat) {
                if (majorName.contains(cat)) return true;
                for (String r : related) {
                    if (majorName.contains(r)) return true;
                }
            }
        }
        return false;
    }

    /**
     * 证据链补全：批量加载招生章程/专业目录/收费链接，把官方资料指向志愿条目，
     * 同时根据数据来源类型、选科要求来源、办学性质等打上人工复核标签。
     */
    private void enrichWithEvidence(List<VolunteerItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<String> schoolIds = new HashSet<>();
        for (VolunteerItem item : items) {
            if (item.getSchoolId() != null && !item.getSchoolId().isBlank()) {
                schoolIds.add(item.getSchoolId());
            }
        }
        Map<String, UniOfficialLink> linkMap = officialLinkService.loadBySchoolIds(schoolIds);
        for (VolunteerItem item : items) {
            UniOfficialLink link = linkMap.get(item.getSchoolId());
            if (link != null) {
                item.setSchoolOfficialUrl(blankToNull(link.getSchoolSite()));
                item.setAdmissionSiteUrl(blankToNull(link.getAdmissionSite()));
                item.setAdmissionBrochureUrl(blankToNull(link.getAdmissionBrochureUrl()));
                item.setMajorCatalogUrl(blankToNull(link.getMajorCatalogUrl()));
                item.setTuitionInfoUrl(blankToNull(link.getTuitionInfoUrl()));
            }
            // 复核标签
            List<String> flags = new ArrayList<>();
            String requirementSource = item.getSubjectRequirementSource();
            if (requirementSource == null || requirementSource.isBlank() || "missing".equals(requirementSource)) {
                flags.add("missing_subject_requirement");
            } else if ("inferred".equals(requirementSource)) {
                flags.add("inferred_subject_requirement");
            } else if ("score_line".equals(requirementSource)) {
                flags.add("score_line_subject_requirement");
            }
            if (!"专业级".equals(item.getDataSourceType())) {
                flags.add("non_major_level");
            }
            String major = safeText(item.getMajorName());
            String universityName = safeText(item.getUniversityName());
            String nature = safeText(item.getSchoolNature());
            List<String> tags = item.getTags() != null ? item.getTags() : List.of();
            if ("民办".equals(nature) || tags.contains("民办")) {
                flags.add("private_school");
            }
            if (universityName.contains("中外合作") || universityName.contains("国际学院")
                    || major.contains("中外合作") || major.contains("联合培养")
                    || tags.contains("中外合作办学") || tags.contains("内地与港澳台合作办学")) {
                flags.add("sino_foreign");
            }
            if (containsAny(major, "军医", "军校", "公安", "警察", "国防", "刑侦", "海关")) {
                flags.add("military_police");
            }
            if (containsAny(major, "临床", "口腔", "中医", "针灸", "麻醉", "医学影像", "护理", "药学", "预防医学")) {
                flags.add("medical_special");
            }
            if (containsAny(major, "美术", "音乐", "舞蹈", "戏剧", "影视", "设计", "播音", "体育", "运动")) {
                flags.add("art_sports");
            }
            if (containsAny(major, "定向", "免费师范", "公费师范", "专项")) {
                flags.add("special_admission");
            }
            Integer year = item.getReferenceYear();
            int currentYear = LocalDateTime.now().getYear();
            if (year == null || year <= 0 || year < currentYear - 2) {
                flags.add("outdated_reference_year");
            }
            item.setReviewFlags(flags);
            item.setNeedsManualReview(!flags.isEmpty()
                    && (flags.contains("missing_subject_requirement")
                    || flags.contains("inferred_subject_requirement")
                    || flags.contains("non_major_level")
                    || flags.contains("private_school")
                    || flags.contains("sino_foreign")
                    || flags.contains("military_police")
                    || flags.contains("medical_special")
                    || flags.contains("art_sports")
                    || flags.contains("special_admission")));
        }
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) return false;
        for (String key : keywords) {
            if (key != null && !key.isBlank() && text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private AdvisorAdvice buildAdvisorAdvice(PlanResult plan) {
        List<VolunteerItem> items = plan == null || plan.getItems() == null ? List.of() : plan.getItems();
        AdvisorAdvice advice = new AdvisorAdvice();
        advice.setTitle("张雪峰.skill 报考建议");
        advice.setPositioning(buildPositioningAdvice(plan));
        advice.setPriorityAdvice(buildPriorityAdvice(plan, items));
        advice.setGradientAdvice(buildAdvisorGradientAdvice(plan, items));
        advice.setCityAdvice(buildCityAdvice(plan, items));
        advice.setMajorAdvice(buildMajorAdvice(plan, items));
        advice.setPlanChangeAdvice(buildPlanChangeAdvice(items));
        advice.setRiskChecklist(buildAdvisorRiskChecklist(plan, items));
        advice.setActionItems(buildAdvisorActionItems());
        advice.setSourceNote(ADVISOR_SOURCE_NOTE);
        advice.setSourceProjectName(ADVISOR_SOURCE_PROJECT_NAME);
        advice.setSourceProjectUrl(ADVISOR_SOURCE_PROJECT_URL);
        return advice;
    }

    private String buildPositioningAdvice(PlanResult plan) {
        int rank = plan == null ? 0 : plan.getProvinceRank();
        String provinceName = plan == null || safeText(plan.getProvinceName()).isBlank() ? "本省" : plan.getProvinceName();
        String tier;
        if (rank <= 0) {
            tier = "需先核对官方一分一段位次";
        } else if (rank <= 5_000) {
            tier = "高分段，重点在学校平台、强专业和大类分流规则之间取舍";
        } else if (rank <= 20_000) {
            tier = "较高位次段，可在优质院校平台与目标专业之间做组合优化";
        } else if (rank <= 60_000) {
            tier = "本科主流竞争段，城市机会、专业适配和保底厚度会直接影响结果质量";
        } else if (rank <= 120_000) {
            tier = "本科机会段，应把稳妥与保底做实，谨慎处理民办、中外合作和高学费项目";
        } else {
            tier = "保底优先段，先确保不滑到无可接受选项，再讨论城市和专业偏好";
        }
        return String.format("以%s官方位次第%,d名为边界建立可行集：%s。所有冲刺项都应服从这个边界，不能只看学校名气。",
                provinceName, Math.max(rank, 0), tier);
    }

    private String buildPriorityAdvice(PlanResult plan, List<VolunteerItem> items) {
        String decisionPriority = safeText(plan == null ? "" : plan.getDecisionPriority());
        String careerGoal = safeText(plan == null ? "" : plan.getCareerGoal());
        String tuitionBudget = safeText(plan == null ? "" : plan.getTuitionBudget());
        long eliteCount = items.stream().filter(this::hasEliteTag).count();
        long majorLevelCount = items.stream().filter(item -> "专业级".equals(item.getDataSourceType())).count();
        if ("低预算".equals(tuitionBudget) || Boolean.FALSE.equals(plan == null ? null : plan.getAcceptPrivate())) {
            return "当前家庭成本约束较强，先把公办、学费清楚、计划稳定的稳/保项做厚；民办、中外合作和高收费项目只能在全家确认预算后再保留。";
        }
        if ("学校优先".equals(decisionPriority)) {
            return String.format("当前选择学校优先，方案里有%d个985/211/双一流标签项。建议用“500强测试”和就业去向倒推学校平台价值，但不要用院校级回退数据替代专业级核验。", eliteCount);
        }
        if ("城市机会优先".equals(careerGoal)) {
            return "当前目标偏城市机会，优先保留大城市且专业不明显吃亏的稳/保项；外地强校要看是否利于未来就业地。";
        }
        return String.format("当前选择专业优先，方案中%d个条目有专业级数据。建议用就业倒推法看普通毕业生中位数去向，先锁定目标专业方向，再用学校和城市做第二层筛选。", majorLevelCount);
    }

    private String buildAdvisorGradientAdvice(PlanResult plan, List<VolunteerItem> items) {
        int target = plan == null ? TOTAL_COUNT : Math.max(1, plan.getTargetCount());
        long chong = items.stream().filter(item -> "冲".equals(item.getGradient())).count();
        long wen = items.stream().filter(item -> "稳".equals(item.getGradient())).count();
        long baoDian = items.stream().filter(item -> "保".equals(item.getGradient()) || "垫".equals(item.getGradient())).count();
        double chongRatio = chong * 100.0 / target;
        double wenRatio = wen * 100.0 / target;
        double safeRatio = baoDian * 100.0 / target;
        String warning = safeRatio < 25 ? "保底厚度偏薄，建议补强保/垫项。" : "保底厚度基本可用，但仍需逐条核对专业限制。";
        return String.format("当前梯度约为：冲%.0f%%、稳%.0f%%、保垫%.0f%%。参考策略是冲不超过约30%%、稳占主体、保底不空心。%s",
                chongRatio, wenRatio, safeRatio, warning);
    }

    private String buildCityAdvice(PlanResult plan, List<VolunteerItem> items) {
        List<String> preferredRegions = plan == null || plan.getPreferredRegions() == null ? List.of() : plan.getPreferredRegions();
        long opportunityCityCount = items.stream().filter(this::isOpportunityCity).count();
        if (preferredRegions.isEmpty()) {
            return String.format("你没有填写地区偏好。方案中约%d个条目位于机会密度较高的城市；普通本科段尤其建议重视实习、校招和未来就业地。", opportunityCityCount);
        }
        long matched = items.stream().filter(item -> regionMatches(item, preferredRegions)).count();
        return String.format("你偏好的地区为%s，当前命中%d个条目。若未来就业地明确，优先保留目标城市/周边省会的稳妥项。",
                String.join("、", preferredRegions), matched);
    }

    private String buildMajorAdvice(PlanResult plan, List<VolunteerItem> items) {
        List<String> preferredMajors = plan == null || plan.getPreferredMajors() == null ? List.of() : plan.getPreferredMajors();
        if (preferredMajors.isEmpty()) {
            return "你没有填写明确意向专业。建议先把不能接受的专业排除，再按就业优先/升学优先/城市机会优先确定专业大类；不要只看顶尖案例，要看普通毕业生的中位数去向。";
        }
        long matched = items.stream().filter(item -> majorMatches(item, preferredMajors)).count();
        return String.format("你偏好的专业方向为%s，当前命中%d个条目。热门专业不要只看名称，必须核对培养方案、转专业政策、就业质量报告和普通毕业生去向。",
                String.join("、", preferredMajors), matched);
    }

    private String buildPlanChangeAdvice(List<VolunteerItem> items) {
        long expanded = items.stream().filter(item -> "扩招".equals(item.getPlanTrend())).count();
        long shrunk = items.stream().filter(item -> "缩招".equals(item.getPlanTrend())).count();
        long missing = items.stream().filter(item -> "计划数暂缺".equals(item.getPlanTrend())).count();
        long small = items.stream().filter(item -> item.getLatestPlanCount() != null
                && item.getLatestPlanCount() > 0
                && item.getLatestPlanCount() <= 3).count();
        return String.format("招生计划信号：扩招%d项、缩招%d项、计划暂缺%d项、小计划数%d项。缩招和小计划数会放大位次波动，不能只按去年最低位次判断。",
                expanded, shrunk, missing, small);
    }

    private List<String> buildAdvisorRiskChecklist(PlanResult plan, List<VolunteerItem> items) {
        List<String> risks = new ArrayList<>();
        PlanMetrics metrics = plan == null ? null : plan.getMetrics();
        int manualReviewCount = metrics == null ? (int) items.stream().filter(VolunteerItem::isNeedsManualReview).count() : metrics.getManualReviewCount();
        if (manualReviewCount > 0) {
            risks.add(String.format("有%d个条目进入人工复核清单，必须先看招生章程、专业目录和选科要求来源。", manualReviewCount));
        }
        long shrinkCount = items.stream().filter(item -> "缩招".equals(item.getPlanTrend())).count();
        if (shrinkCount > 0) {
            risks.add(String.format("有%d个条目出现缩招信号，建议降低排序或替换为同梯度更稳项。", shrinkCount));
        }
        long nonMajor = items.stream().filter(item -> !"专业级".equals(item.getDataSourceType())
                && !ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45.equals(item.getVolunteerUnitType())).count();
        if (nonMajor > 0) {
            risks.add(String.format("有%d个贵州条目使用院校级回退数据，专业精度不足，只能作为备选。", nonMajor));
        }
        if (items.stream().anyMatch(item -> "red".equals(item.getRiskColor()))) {
            risks.add("存在高波动风险项，不能把多个高风险项连续放在前段。");
        }
        risks.add("所有医学、军警、定向、公费师范、中外合作、民办高学费项目都要逐条核对限制条件。");
        return risks;
    }

    private List<String> buildAdvisorActionItems() {
        return List.of(
                "先按官方一分一段表再次确认位次，系统只按你手填位次生成，不替你改位次。",
                "把“稳”和“保/垫”里专业级、高可信、计划稳定的条目标为保留，形成方案底座。",
                "对“冲”项只保留有明确学校平台或专业价值的条目，避免为了名气牺牲不可接受专业。",
                "逐条打开证据链，核对招生章程、专业目录、选科要求、学费、体检/语种/单科限制。",
                "导出决策草稿后，和家长/考生按城市、学校、专业三项重新排序，最终以考试院填报系统为准。"
        );
    }

    private boolean regionMatches(VolunteerItem item, List<String> regions) {
        String text = safeText(item.getProvince()) + safeText(item.getCity()) + safeText(item.getUniversityName());
        return regions.stream().map(this::safeText).filter(v -> !v.isBlank()).anyMatch(text::contains);
    }

    private boolean majorMatches(VolunteerItem item, List<String> majors) {
        String major = safeText(item.getMajorName()).toLowerCase(Locale.ROOT);
        return majors.stream()
                .map(this::safeText)
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .filter(value -> !value.isBlank())
                .anyMatch(value -> major.contains(value) || fuzzyMajorMatch(major, value));
    }

    /**
     * 构建强制人工复核清单：把所有触发复核标签的志愿条目按梯度顺序整理成中文说明。
     */
    private List<ManualReviewItem> buildManualReviewList(List<VolunteerItem> items) {
        List<ManualReviewItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (VolunteerItem item : items) {
            if (!item.isNeedsManualReview()) {
                continue;
            }
            ManualReviewItem entry = new ManualReviewItem();
            entry.setIndex(item.getIndex());
            entry.setUniversityName(item.getUniversityName());
            entry.setMajorName(item.getMajorName());
            entry.setGradient(item.getGradient());
            entry.setConfidenceLabel(item.getConfidenceLabel());
            entry.setDataSourceType(item.getDataSourceType());
            entry.setSubjectRequirementSource(item.getSubjectRequirementSource());

            List<String> reasons = new ArrayList<>();
            List<String> flags = item.getReviewFlags() != null ? item.getReviewFlags() : List.of();
            if (flags.contains("missing_subject_requirement")) {
                reasons.add("缺少官方再选科目要求，需以考试院与学校招生章程为准");
            }
            if (flags.contains("inferred_subject_requirement")) {
                reasons.add("再选科目要求为系统按专业名推断，请人工核实");
            }
            if (flags.contains("score_line_subject_requirement")) {
                reasons.add("再选科目来源为投档线字段，未取自官方专业要求库，建议核对原文");
            }
            if (flags.contains("non_major_level")) {
                reasons.add("当前数据为院校级回退（非专业级），录取参考精度较低");
            }
            if (flags.contains("private_school")) {
                reasons.add("民办院校，请确认学费、住宿、就业去向等真实情况");
            }
            if (flags.contains("sino_foreign")) {
                reasons.add("中外/港澳台合作办学项目，学费、外语、毕业证书要求与普通项目差异较大");
            }
            if (flags.contains("military_police")) {
                reasons.add("军警/国防类，需通过政审、体检、面试等环节，必须核对当年招生简章");
            }
            if (flags.contains("medical_special")) {
                reasons.add("医学相关专业对色觉/视力/单科成绩有专项要求，请按学校招生章程核对");
            }
            if (flags.contains("art_sports")) {
                reasons.add("艺术/体育类专业涉及统考成绩与文化课双线，需查看专业目录与省考试院公告");
            }
            if (flags.contains("special_admission")) {
                reasons.add("含定向/免费师范/专项计划字样，履约要求与普通本科不同，必须查阅协议条款");
            }
            if (flags.contains("outdated_reference_year")) {
                reasons.add("参考年份较早，请关注近 2 年录取波动并以最新数据复核");
            }
            entry.setReasons(reasons);

            List<String> evidenceLinks = new ArrayList<>();
            addLinkIfPresent(evidenceLinks, item.getAdmissionBrochureUrl());
            addLinkIfPresent(evidenceLinks, item.getMajorCatalogUrl());
            addLinkIfPresent(evidenceLinks, item.getTuitionInfoUrl());
            addLinkIfPresent(evidenceLinks, item.getAdmissionSiteUrl());
            addLinkIfPresent(evidenceLinks, item.getSchoolOfficialUrl());
            addLinkIfPresent(evidenceLinks, item.getRequirementSourceUrl());
            entry.setEvidenceLinks(evidenceLinks);

            result.add(entry);
        }
        result.sort(Comparator.comparingInt(ManualReviewItem::getIndex));
        return result;
    }

    private void addLinkIfPresent(List<String> links, String url) {
        if (url != null && !url.isBlank() && !links.contains(url)) {
            links.add(url);
        }
    }

    private PortfolioSafety assessPortfolioSafety(List<VolunteerItem> items) {
        if (items == null || items.isEmpty()) {
            return new PortfolioSafety(0, "数据不足", "志愿表为空，无法评估整表安全度。", 0);
        }
        List<VolunteerItem> safeTail = items.stream()
                .filter(item -> "保".equals(item.getGradient()) || "垫".equals(item.getGradient()))
                .filter(item -> !item.isSpecialTypeFlag())
                .filter(item -> item.getDataConfidenceScore() >= 45 || "高可信".equals(item.getConfidenceLabel()))
                .sorted((a, b) -> Double.compare(itemSafetyScore(b), itemSafetyScore(a)))
                .limit(18)
                .toList();
        if (safeTail.isEmpty()) {
            return new PortfolioSafety(0, "需加厚保/兜底",
                    "保/兜底区缺少可用于整表兜底的高可信条目，建议补充更稳妥的专业级志愿。", 0);
        }

        // 报告 R5：把单条志愿的 chanceScore 通过 ProbabilityCalibration 映射到校准概率，
        // 再交给蒙特卡洛仿真计算"整表至少录取一条"的频率；替代原有安全垫经验折扣估算。
        List<Double> calibratedProbs = new ArrayList<>(safeTail.size());
        for (VolunteerItem item : safeTail) {
            double p;
            if (item.getChanceScore() > 0) {
                p = ProbabilityCalibration.fromChanceScore(item.getChanceScore());
            } else if (item.getAdmissionProb() > 0) {
                // 兼容早期仅写入 admissionProb 的链路（本科 45 志愿生成器 / 单测 stub）
                p = ProbabilityCalibration.fromRawProbability(item.getAdmissionProb() / 100.0);
            } else {
                p = ProbabilityCalibration.fromChanceScore(0);
            }
            if ("red".equals(item.getRiskColor())) {
                p = Math.max(0.02, p - 0.05);
            } else if ("green".equals(item.getRiskColor())) {
                p = Math.min(0.97, p + 0.02);
            }
            if ("缩招".equals(item.getPlanTrend())) {
                p = Math.max(0.02, p - 0.03);
            }
            if (item.getDataConfidenceScore() > 0 && item.getDataConfidenceScore() < 60) {
                p = Math.max(0.02, p - 0.03);
            }
            calibratedProbs.add(p);
        }
        double hitRate = PortfolioMonteCarloSimulator.listHitRate(calibratedProbs);
        double probability = Math.max(0, Math.min(99.9, round1(hitRate * 100)));
        String level;
        if (probability >= PORTFOLIO_SAFETY_THRESHOLD) {
            level = "安全垫充足";
        } else if (probability >= 95) {
            level = "安全垫可用";
        } else {
            level = "需加厚保/兜底";
        }
        String note = String.format(Locale.ROOT,
                "按保/兜底区前%d个高可信条目的校准概率做 %d 次蒙特卡洛仿真，整表至少录取一条的参考频率约%.1f%%；该值仅用于检查列表是否过于激进。",
                safeTail.size(), PortfolioMonteCarloSimulator.DEFAULT_ITERATIONS, probability);
        return new PortfolioSafety(probability, level, note, safeTail.size());
    }

    private double itemSafetyScore(VolunteerItem item) {
        return itemSafetyProbability(item) * 100
                + item.getDataConfidenceScore() * 0.35
                + item.getPrecisionScore() * 0.25
                + ("green".equals(item.getRiskColor()) ? 8 : "yellow".equals(item.getRiskColor()) ? 3 : -5);
    }

    private double itemSafetyProbability(VolunteerItem item) {
        double p = item.getAdmissionProb() > 0
                ? item.getAdmissionProb() / 100.0
                : fallbackSafetyProbability(item);
        if ("red".equals(item.getRiskColor())) {
            p -= 0.08;
        } else if ("green".equals(item.getRiskColor())) {
            p += 0.03;
        }
        if ("缩招".equals(item.getPlanTrend())) {
            p -= 0.06;
        } else if ("扩招".equals(item.getPlanTrend()) || "基本稳定".equals(item.getPlanTrend())) {
            p += 0.03;
        }
        if (item.isNeedsManualReview()) {
            p -= 0.05;
        }
        if (item.getDataConfidenceScore() > 0 && item.getDataConfidenceScore() < 60) {
            p -= 0.04;
        }
        return Math.max(0.05, Math.min(0.92, p));
    }

    private double fallbackSafetyProbability(VolunteerItem item) {
        return switch (safeText(item.getGradient())) {
            case "垫" -> 0.88;
            case "保" -> 0.78;
            case "稳" -> 0.55;
            default -> 0.32;
        };
    }

    private String appendPortfolioSafetyWarning(String warning, PortfolioSafety safety) {
        if (safety == null || safety.probability() >= PORTFOLIO_SAFETY_THRESHOLD) {
            return warning;
        }
        String safetyWarning = safety.note() + " 建议减少冲档连续项，增加专业级、高可信、计划稳定的保/兜底志愿。";
        if (warning == null || warning.isBlank()) {
            return safetyWarning;
        }
        return warning + " " + safetyWarning;
    }

    /**
     * 汇总监控指标。
     */
    @SuppressWarnings("unused") // 反射单测覆盖该纯函数入口。
    private PlanMetrics buildPlanMetrics(List<VolunteerItem> items, List<ManualReviewItem> manualReview) {
        return buildPlanMetrics(items, manualReview, 0, null);
    }

    @SuppressWarnings("unused") // 保留给反射测试和后续仅统计特殊类型的调用入口。
    private PlanMetrics buildPlanMetrics(List<VolunteerItem> items, List<ManualReviewItem> manualReview,
                                         int specialExcludedCount) {
        return buildPlanMetrics(items, manualReview, specialExcludedCount, null);
    }

    private PlanMetrics buildPlanMetrics(List<VolunteerItem> items, List<ManualReviewItem> manualReview,
                                         int specialExcludedCount, PortfolioSafety portfolioSafety) {
        return buildPlanMetrics(items, manualReview, specialExcludedCount, portfolioSafety,
                TOTAL_COUNT, ProvincePolicyService.GZ, ProvincePolicyService.UNIT_MAJOR_96, null);
    }

    private PlanMetrics buildPlanMetrics(List<VolunteerItem> items, List<ManualReviewItem> manualReview,
                                         int specialExcludedCount, PortfolioSafety portfolioSafety,
                                         int targetCount, String provinceCode, String volunteerUnitType) {
        return buildPlanMetrics(items, manualReview, specialExcludedCount, portfolioSafety,
                targetCount, provinceCode, volunteerUnitType, null);
    }

    /**
     * 按策略模式返回 [overRiskExposureBaseline, firstTwentyHitRateBaseline]。
     *
     * <p>报告 P1 给出的中性参考是 overRisk &lt; 10%、firstTwentyHit ≥ 30%；该口径只对
     * 「保守 / 中性配比」适用。GZLY 的梯度策略允许冲刺型把前 20 几乎全部填满冲档，
     * 这种场景 chanceScore &lt; 35 是设计行为而非异常，因此把告警基线按策略调整：</p>
     *
     * <ul>
     *   <li>保守型：冲档约 10%，前 20 中冲+稳，overRisk 基线 0.55，hit 基线 0.20。</li>
     *   <li>均衡型：冲档约 20%，前 20 全部为冲，overRisk 基线 0.85，hit 基线 0.10。</li>
     *   <li>冲刺型：冲档约 30%，前 20 全部为冲，overRisk 基线 1.00（不告警），hit 基线 0.05。</li>
     * </ul>
     */
    private double[] resolveStrategyThresholds(String strategyMode) {
        if ("保守型".equals(strategyMode)) return new double[]{0.55, 0.20};
        if ("冲刺型".equals(strategyMode)) return new double[]{1.00, 0.05};
        return new double[]{0.85, 0.10}; // 默认按均衡型
    }

    private PlanMetrics buildPlanMetrics(List<VolunteerItem> items, List<ManualReviewItem> manualReview,
                                         int specialExcludedCount, PortfolioSafety portfolioSafety,
                                         int targetCount, String provinceCode, String volunteerUnitType,
                                         String strategyMode) {
        PlanMetrics metrics = new PlanMetrics();
        if (items == null || items.isEmpty()) {
            metrics.setSpecialExcludedCount(Math.max(0, specialExcludedCount));
            metrics.setTargetCount(targetCount <= 0 ? TOTAL_COUNT : targetCount);
            metrics.setProvinceCode(safeTrim(provinceCode).isBlank() ? ProvincePolicyService.GZ : provinceCode);
            metrics.setVolunteerUnitType(safeTrim(volunteerUnitType).isBlank()
                    ? ProvincePolicyService.UNIT_MAJOR_96 : volunteerUnitType);
            if (portfolioSafety != null) {
                metrics.setPortfolioSafetyProbability(portfolioSafety.probability());
                metrics.setPortfolioSafetyLevel(portfolioSafety.level());
                metrics.setPortfolioSafetyNote(portfolioSafety.note());
                metrics.setSafeTailCount(portfolioSafety.safeTailCount());
            }
            return metrics;
        }
        int chong = 0, wen = 0, bao = 0, dian = 0;
        int missing = 0, nonMajor = 0;
        int legacyFallback = 0, lowConfidence = 0, officialRequirement = 0;
        int expanded = 0, shrunk = 0, missingPlanIndex = 0, highSupply = 0, lowSupply = 0, precisionSum = 0;
        for (VolunteerItem item : items) {
            switch (safeText(item.getGradient())) {
                case "冲" -> chong++;
                case "稳" -> wen++;
                case "保" -> bao++;
                case "垫" -> dian++;
                default -> {
                }
            }
            String src = item.getSubjectRequirementSource();
            if (src == null || src.isBlank() || "missing".equals(src) || "inferred".equals(src)) {
                missing++;
            }
            if (!"专业级".equals(item.getDataSourceType())) {
                nonMajor++;
            }
            if (item.isLegacySubjectFallback()) {
                legacyFallback++;
            }
            if (item.getDataConfidenceScore() > 0 && item.getDataConfidenceScore() < 60) {
                lowConfidence++;
            }
            if ("official_requirement".equals(src)) {
                officialRequirement++;
            }
            if ("扩招".equals(item.getPlanTrend())) {
                expanded++;
            } else if ("缩招".equals(item.getPlanTrend())) {
                shrunk++;
            }
            if (item.getPlanExpansionIndex() <= 0) {
                missingPlanIndex++;
            }
            if (item.getSchoolEnrollmentIndex() >= 75) {
                highSupply++;
            } else if (item.getSchoolEnrollmentIndex() > 0 && item.getSchoolEnrollmentIndex() < 45) {
                lowSupply++;
            }
            precisionSum += item.getPrecisionScore();
        }
        metrics.setTotalCount(items.size());
        metrics.setTargetCount(targetCount <= 0 ? TOTAL_COUNT : targetCount);
        metrics.setProvinceCode(safeTrim(provinceCode).isBlank() ? ProvincePolicyService.GZ : provinceCode);
        metrics.setVolunteerUnitType(safeTrim(volunteerUnitType).isBlank()
                ? ProvincePolicyService.UNIT_MAJOR_96 : volunteerUnitType);
        metrics.setChongCount(chong);
        metrics.setWenCount(wen);
        metrics.setBaoCount(bao);
        metrics.setDianCount(dian);
        metrics.setMissingRequirementCount(missing);
        metrics.setNonMajorLevelCount(nonMajor);
        metrics.setManualReviewCount(manualReview == null ? 0 : manualReview.size());
        metrics.setLegacyFallbackCount(legacyFallback);
        metrics.setSpecialExcludedCount(Math.max(0, specialExcludedCount));
        metrics.setLowConfidenceCount(lowConfidence);
        metrics.setOfficialRequirementCount(officialRequirement);
        metrics.setExpandedPlanCount(expanded);
        metrics.setShrunkPlanCount(shrunk);
        metrics.setMissingPlanIndexCount(missingPlanIndex);
        metrics.setHighSupplyCount(highSupply);
        metrics.setLowSupplyCount(lowSupply);
        metrics.setAvgPrecisionScore(items.isEmpty() ? 0 : Math.round(precisionSum * 1.0f / items.size()));
        if (portfolioSafety != null) {
            metrics.setPortfolioSafetyProbability(portfolioSafety.probability());
            metrics.setPortfolioSafetyLevel(portfolioSafety.level());
            metrics.setPortfolioSafetyNote(portfolioSafety.note());
            metrics.setSafeTailCount(portfolioSafety.safeTailCount());
        }
        // === 算法报告 P1 评估指标 ===
        Map<String, Integer> recruitBreakdown = new LinkedHashMap<>();
        int ruleViolations = 0;
        int firstWindow = Math.min(20, items.size());
        int firstWindowOver = 0;
        int firstWindowHit = 0;
        for (int i = 0; i < items.size(); i++) {
            VolunteerItem item = items.get(i);
            String rt = safeText(item.getRecruitType());
            if (rt.isBlank()) rt = RecruitTypeClassifier.NORMAL;
            recruitBreakdown.merge(rt, 1, Integer::sum);
            if (!RecruitTypeClassifier.NORMAL.equals(rt)) {
                ruleViolations++;
            }
            if (i < firstWindow) {
                if (item.getChanceScore() > 0 && item.getChanceScore() < 35) firstWindowOver++;
                if (item.getChanceScore() >= 75) firstWindowHit++;
            }
        }
        metrics.setRecruitTypeBreakdown(recruitBreakdown);
        metrics.setRuleViolationCount(ruleViolations);
        metrics.setRuleViolationRate(items.isEmpty() ? 0d : (double) ruleViolations / items.size());
        double overRisk = firstWindow == 0 ? 0d : (double) firstWindowOver / firstWindow;
        double hitRate = firstWindow == 0 ? 0d : (double) firstWindowHit / firstWindow;
        metrics.setOverRiskExposure(overRisk);
        metrics.setFirstTwentyHitRate(hitRate);
        // 自适应阈值：按 strategyMode 区分保守 / 均衡 / 冲刺
        double[] thresholds = resolveStrategyThresholds(strategyMode);
        metrics.setStrategyMode(safeTrim(strategyMode).isBlank() ? "均衡型" : strategyMode);
        metrics.setOverRiskExposureBaseline(thresholds[0]);
        metrics.setFirstTwentyHitRateBaseline(thresholds[1]);
        metrics.setOverRiskExposureBreached(overRisk > thresholds[0]);
        metrics.setFirstTwentyHitRateBreached(hitRate < thresholds[1]);
        return metrics;
    }

    /**
     * 构建用于持久化的请求快照，剥除 cardKey 等敏感字段。
     */
    @SuppressWarnings("unused") // 旧版反序列化兼容入口，当前主流程调用带梯度摘要的重载。
    private Map<String, Object> buildRequestSnapshot(GenerateRequest req, PreferenceProfile profile) {
        return buildRequestSnapshot(req, profile, null);
    }

    private Map<String, Object> buildRequestSnapshot(GenerateRequest req, PreferenceProfile profile,
                                                     GradientRangeSummary gradientRangeSummary) {
        return buildRequestSnapshot(req, profile, gradientRangeSummary, null);
    }

    private Map<String, Object> buildRequestSnapshot(GenerateRequest req, PreferenceProfile profile,
                                                     GradientRangeSummary gradientRangeSummary,
                                                     RankEstimateSummary rankEstimate) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("totalScore", req.getTotalScore());
        snapshot.put("provinceCode", normalizeProvinceCode(req.getProvinceCode()));
        snapshot.put("volunteerUnitType", policyVolunteerUnitType(req));
        snapshot.put("targetBatch", policyBatchName(req));
        snapshot.put("targetCount", policyTargetCount(req));
        snapshot.put("provinceRank", req.getProvinceRank());
        snapshot.put("firstSubject", safeText(req.getFirstSubject()));
        snapshot.put("resubjects", req.getResubjects() == null ? List.of() : req.getResubjects());
        snapshot.put("preferredMajors", profile.getPreferredMajors());
        snapshot.put("preferredRegions", profile.getPreferredRegions());
        snapshot.put("strategyMode", profile.getStrategyMode());
        snapshot.put("decisionPriority", profile.getDecisionPriority());
        snapshot.put("careerGoal", profile.getCareerGoal());
        snapshot.put("tuitionBudget", profile.getTuitionBudget());
        snapshot.put("acceptPrivate", profile.isAcceptPrivate());
        snapshot.put("acceptSinoForeign", profile.isAcceptSinoForeign());
        snapshot.put("agreedDisclaimer", Boolean.TRUE.equals(req.getAgreedDisclaimer()));
        snapshot.put("disclaimerVersion", safeText(req.getDisclaimerVersion()));
        snapshot.put("gradientRanges", normalizeGradientRanges(req.getGradientRanges()));
        if (gradientRangeSummary != null) {
            snapshot.put("gradientRangeSummary", gradientRangeSummary);
        }
        if (rankEstimate != null) {
            snapshot.put("rankEstimate", rankEstimate);
        }
        return snapshot;
    }

    /**
     * 算法增强: 为每个志愿项计算机会指数、风险评估、分数线预测
     *
     * <p>chanceScore 链路：</p>
     * <ol>
     *   <li>{@link AlgorithmService#calcProbability} 仅保留作为内部 admissionProb 基线（WRITE_ONLY）。</li>
     *   <li>读 {@link AlgorithmService#getRecentLines} 近 5 年历史 → {@link FeatureBuildEngine#buildRankFeatures}
     *       提取 rankVolatility3y / planChangeRate / dataConfidence。</li>
     *   <li>{@link AlgorithmService#predictScore} 得 predictedRank（作为 referenceRank）。</li>
     *   <li>{@link FallbackRulePredictionEngine#predict} 完整 sigmoid + 4 项 penalty 算 chanceScore。</li>
     * </ol>
     * <p>主链路不再使用任何 rankGapToChanceScore 类的简化线性映射。</p>
     */
    private void enrichWithAlgorithms(List<VolunteerItem> items, int studentRank, String subjectType) {
        for (VolunteerItem item : items) {
            try {
                String algorithmMajorName = "专业级".equals(item.getDataSourceType())
                        ? item.getMajorName()
                        : null;
                // 内部机会基线（WRITE_ONLY，不外露）
                AlgorithmService.AdmissionProbability prob = algorithmService.calcProbability(
                        studentRank, item.getSchoolId(), algorithmMajorName, subjectType);
                item.setAdmissionProb(prob.getProbability());
                item.setProbLevel(prob.getLevel());

                // 风险评估（保留风险颜色和定性标签，但 riskLevel 文本会被 FallbackRulePredictionEngine 覆盖）
                AlgorithmService.RiskAssessment risk = algorithmService.assessRisk(
                        item.getSchoolId(), algorithmMajorName, subjectType);
                item.setRiskColor(risk.getRiskColor());

                // 分数线预测：predictedRank 作为 FallbackRulePredictionEngine 的 referenceRank
                AlgorithmService.ScorePrediction prediction = algorithmService.predictScore(
                        item.getSchoolId(), algorithmMajorName, subjectType);
                item.setPredictedRank(prediction.getPredictedRank());
                item.setTrend(prediction.getTrend());

                // 用 FeatureBuildEngine 抽位次特征 + FallbackRulePredictionEngine 算完整机会指数
                applyRulePrediction(item, studentRank, subjectType, algorithmMajorName);
                applyChanceFields(item, studentRank);
            } catch (Exception e) {
                log.warn("算法增强失败: {} - {}: {}", item.getUniversityName(), item.getMajorName(), e.getMessage());
                item.setAdmissionProb(0);
                item.setProbLevel("未知");
                item.setRiskLevel("未知");
                item.setRiskColor("gray");
                // 异常时仍尝试用规则公式兜底（用 historyMinRank 作 referenceRank）
                try {
                    applyRulePrediction(item, studentRank, subjectType, null);
                } catch (Exception ignore) {
                    // 真正的双重失败：保持 chanceScore=0，前端会显示"未知"
                }
                applyChanceFields(item, studentRank);
            }
        }
    }

    /**
     * 用 FeatureBuildEngine + FallbackRulePredictionEngine 计算完整机会指数。
     *
     * <p>调用方应已设置 item.predictedRank 和 item.historyMinRank；
     * 该方法会：</p>
     * <ul>
     *   <li>读 5 年历史录取数据，构建 RankFeature（rankVolatility3y / planChangeRate / dataConfidence）；</li>
     *   <li>取 referenceRank = predictedRank > 0 ? predictedRank : historyMinRank；</li>
     *   <li>调 FallbackRulePredictionEngine.predict 得 chanceScore / chanceLevel / riskLevel / confidenceLevel；</li>
     *   <li>把所有字段写回 item，并把特征值缓存到 item.rankVolatility3y / planChangeRate / hotTrendScore（WRITE_ONLY）。</li>
     * </ul>
     */
    private void applyRulePrediction(VolunteerItem item, int studentRank, String subjectType, String algorithmMajorName) {
        // 1. 读历史 ScoreLineGz → 抽 minRank/planCount lag
        List<ScoreLineGz> history;
        try {
            history = algorithmService.getRecentLines(item.getSchoolId(), algorithmMajorName, subjectType, 5);
        } catch (Exception ex) {
            history = List.of();
        }
        List<Integer> minRanks = new ArrayList<>();
        List<Integer> planCounts = new ArrayList<>();
        for (ScoreLineGz sl : history) {
            if (sl.getMinRank() != null && sl.getMinRank() > 0) {
                minRanks.add(sl.getMinRank());
            }
            if (sl.getPlanCount() != null && sl.getPlanCount() > 0) {
                planCounts.add(sl.getPlanCount());
            }
        }

        // 2. FeatureBuildEngine 构造 10 维位次特征（核心：rankVolatility3y / planChangeRate / dataConfidence）
        FeatureBuildEngine.RankFeature feature = featureBuildEngine.buildRankFeatures(minRanks, planCounts);

        // 3. referenceRank：优先 predictedRank → historyMinRank → 任意已知
        int referenceRank = item.getPredictedRank() > 0
                ? item.getPredictedRank()
                : (item.getHistoryMinRank() > 0 ? item.getHistoryMinRank() : 0);

        // 4. hotTrendScore 暂无数据源 → 0；未来在专业热度数据落库后接入
        double hotTrendScore = item.getHotTrendScore();

        // 5. FallbackRulePredictionEngine.predict 完整公式
        FallbackRulePredictionEngine.Prediction p = fallbackRulePredictionEngine.predict(
                Math.max(1, studentRank),
                referenceRank,
                feature.getRankVolatility3y(),
                feature.getPlanChangeRate(),
                feature.getDataConfidence(),
                hotTrendScore);

        // 6. 写回 item：chanceScore / chanceLevel / riskLevel / confidenceLevel / dataConfidence / predictedMinRank / rankDiff
        item.setChanceScore(p.getChanceScore());
        item.setChanceLevel(p.getChanceLevel());
        item.setRiskLevel(p.getRiskLevel());
        item.setConfidenceLevel(p.getConfidenceLevel());
        item.setDataConfidence(p.getDataConfidence());
        item.setPredictedMinRank(p.getPredictedMinRank());
        item.setRankDiff(p.getRankDiff());

        // 7. 特征值缓存（WRITE_ONLY，仅用于审计 / 后续训练样本回流）
        item.setRankVolatility3y(feature.getRankVolatility3y());
        item.setPlanChangeRate(feature.getPlanChangeRate());
    }
}
