package com.gzly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzly.algorithm.core.CandidateProvider;
import com.gzly.algorithm.core.ThreeOneTwoSubjectMatcher;
import com.gzly.algorithm.core.VolunteerUnitType;
import com.gzly.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 推荐生成统一编排入口（docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md 4.2）。
 *
 * <p>{@link #generateWithPolicy} 是**唯一**对外生成流程：公共请求归一 → 就绪度门禁 →
 * 年度政策解析与注入 → 按志愿单位路由生成（专业组路径带请求锁与结果缓存）→ ML 应用 →
 * 政策/警示包装。旧 /volunteer/generate 与新 /volunteer/recommend 均走本入口，
 * 不再存在绕过门禁与政策注入的旁路。</p>
 */
@Slf4j
@Service
public class RecommendationOrchestrator {

    /** 与贵州链路 gzly.stability.* 默认值同口径：结果缓存 120s、锁 30s、等待 4s。 */
    private static final Duration GROUP_RESULT_CACHE_TTL = Duration.ofSeconds(120);
    private static final Duration GROUP_LOCK_TTL = Duration.ofSeconds(30);
    private static final long GROUP_WAIT_MILLIS = 4000;
    private static final long GROUP_WAIT_POLL_MILLIS = 300;

    /**
     * 释放专业组请求锁的原子 compare-and-delete：仅当锁值仍等于持锁者写入的 lockValue 时才删除，
     * 避免 GET+DEL 窗口内误删他人重抢后的同名锁。
     */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
            Long.class);

    private final ProvincePolicyService provincePolicyService;
    private final ProvinceReadinessService provinceReadinessService;
    private final PolicyRuleService policyRuleService;
    private final MlPredictionService mlPredictionService;
    private final VolunteerService volunteerService;
    private final ProfessionalGroupVolunteerService professionalGroupVolunteerService;
    private final ObjectMapper objectMapper;
    private final Map<VolunteerUnitType, CandidateProvider> providers = new EnumMap<>(VolunteerUnitType.class);

    /**
     * 可选依赖：专业组路径的请求锁/结果缓存。为空（单测直构）时退化为直通生成。
     * 贵州链路在 VolunteerService 内部已有同款锁，不重复加。
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    public RecommendationOrchestrator(ProvincePolicyService provincePolicyService,
                                      ProvinceReadinessService provinceReadinessService,
                                      PolicyRuleService policyRuleService,
                                      MlPredictionService mlPredictionService,
                                      VolunteerService volunteerService,
                                      ProfessionalGroupVolunteerService professionalGroupVolunteerService,
                                      ObjectMapper objectMapper,
                                      List<CandidateProvider> candidateProviders) {
        this.provincePolicyService = provincePolicyService;
        this.provinceReadinessService = provinceReadinessService;
        this.policyRuleService = policyRuleService;
        this.mlPredictionService = mlPredictionService;
        this.volunteerService = volunteerService;
        this.professionalGroupVolunteerService = professionalGroupVolunteerService;
        this.objectMapper = objectMapper;
        for (CandidateProvider provider : candidateProviders) {
            providers.put(provider.supports(), provider);
        }
    }

    /**
     * 完整生成流程：归一 → 门禁 → 政策 → 路由生成 → ML → 包装。
     * 两个公开接口（/volunteer/recommend、/volunteer/generate）统一调用本方法。
     */
    public VolunteerService.PlanResult generateWithPolicy(VolunteerService.GenerateRequest req,
                                                          Long userId, String clientIp) {
        if (req == null) {
            throw new BizException("参数不能为空");
        }
        normalizePublicRequest(req);
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        // 就绪度门禁先于政策解析：未就绪省份应返回数据缺口说明，而不是"政策未配置"。
        provinceReadinessService.requireGenerationReady(provinceCode, resolveTrack(provinceCode, req));
        PolicyRuleService.PolicyContext policy = policyRuleService.requirePolicy(
                provinceCode, req.getYear(), req.getCandidateType(), req.getBatchCode());
        applyPolicyToRequest(req, policy.getConfig(), provinceCode);

        VolunteerService.PlanResult plan = generateRouted(provinceCode, req, userId, clientIp);
        MlPredictionService.ApplyResult mlResult = mlPredictionService.applyPredictions(
                req, plan, policy.getConfig().getMaxVolunteerCount());

        List<String> warnings = new ArrayList<>();
        if (policy.getWarning() != null && !policy.getWarning().isBlank()) {
            warnings.add(policy.getWarning());
        }
        if (plan.getDataQualityWarning() != null && !plan.getDataQualityWarning().isBlank()) {
            warnings.add(plan.getDataQualityWarning());
        }
        plan.setPolicy(policyRuleService.toPublicPolicy(policy.getConfig()));
        plan.setModelInfo(mlResult.toMap());
        plan.setWarnings(warnings);
        return plan;
    }

    /**
     * 门禁 + 路由（不含政策解析与 ML 包装），保留给内部复用与单测。
     */
    public VolunteerService.PlanResult generate(VolunteerService.GenerateRequest req, Long userId, String clientIp) {
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        provinceReadinessService.requireGenerationReady(provinceCode, resolveTrack(provinceCode, req));
        return generateRouted(provinceCode, req, userId, clientIp);
    }

    /** 阶段 2 并线用：按志愿单位类型取候选提供者。 */
    public CandidateProvider providerOf(VolunteerUnitType unitType) {
        return providers.get(unitType);
    }

    // ══════════════════ 路由与专业组请求锁 ══════════════════

    private VolunteerService.PlanResult generateRouted(String provinceCode,
                                                       VolunteerService.GenerateRequest req,
                                                       Long userId, String clientIp) {
        if (!provincePolicyService.isProfessionalGroupProvince(provinceCode)) {
            // 贵州链路：VolunteerService.generate 内部已有指纹锁 + 结果缓存
            return volunteerService.generate(req, userId, clientIp);
        }
        return generateGroupWithLock(req, userId, clientIp);
    }

    /**
     * 专业组路径的幂等保护：与贵州链路同款「结果缓存 + 请求锁」。
     * 此前专业组没有任何防重，重复点击会重复计算并重复落库。
     */
    private VolunteerService.PlanResult generateGroupWithLock(VolunteerService.GenerateRequest req,
                                                              Long userId, String clientIp) {
        if (stringRedisTemplate == null) {
            return professionalGroupVolunteerService.generate(req, userId, clientIp);
        }
        String fingerprint = buildFingerprint(req, userId, clientIp);
        String resultKey = "gzly:v7:pg:result:" + fingerprint;
        String lockKey = "gzly:v7:pg:lock:" + fingerprint;

        try {
            String cached = stringRedisTemplate.opsForValue().get(resultKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, VolunteerService.PlanResult.class);
            }
        } catch (Exception e) {
            log.debug("专业组结果缓存读取失败，继续生成: {}", e.getMessage());
        }

        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = null;
        try {
            acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, GROUP_LOCK_TTL);
        } catch (Exception e) {
            log.debug("专业组请求锁获取异常，直通生成: {}", e.getMessage());
        }
        if (Boolean.FALSE.equals(acquired)) {
            VolunteerService.PlanResult waiting = waitForGroupResult(resultKey);
            if (waiting != null) {
                return waiting;
            }
            throw new BizException("相同请求正在处理中，请稍后再试");
        }
        try {
            VolunteerService.PlanResult plan = professionalGroupVolunteerService.generate(req, userId, clientIp);
            try {
                stringRedisTemplate.opsForValue().set(resultKey,
                        objectMapper.writeValueAsString(plan), GROUP_RESULT_CACHE_TTL);
            } catch (Exception e) {
                log.debug("专业组结果缓存写入失败: {}", e.getMessage());
            }
            return plan;
        } finally {
            releaseGroupLock(lockKey, lockValue);
        }
    }

    private VolunteerService.PlanResult waitForGroupResult(String resultKey) {
        long deadline = System.currentTimeMillis() + GROUP_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(GROUP_WAIT_POLL_MILLIS);
                String cached = stringRedisTemplate.opsForValue().get(resultKey);
                if (cached != null && !cached.isBlank()) {
                    return objectMapper.readValue(cached, VolunteerService.PlanResult.class);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception ignore) {
                // 反序列化/网络异常继续等
            }
        }
        return null;
    }

    private void releaseGroupLock(String lockKey, String lockValue) {
        try {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
        } catch (Exception e) {
            log.debug("专业组请求锁释放失败（将随 TTL 过期）: {}", e.getMessage());
        }
    }

    private String buildFingerprint(VolunteerService.GenerateRequest req, Long userId, String clientIp) {
        try {
            String payload = objectMapper.writeValueAsString(req) + "|" + userId + "|" + clientIp;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // 指纹失败退化为随机键（等价于无缓存直通），不阻断生成
            return UUID.randomUUID().toString();
        }
    }

    // ══════════════════ 公共请求归一与政策注入（自 VolunteerRecommendController 收敛至此） ══════════════════

    private String resolveTrack(String provinceCode, VolunteerService.GenerateRequest req) {
        if (provincePolicyService.isThreeThreeProvince(provinceCode)) {
            return com.gzly.algorithm.core.ThreeThreeSubjectMatcher.TRACK_COMPREHENSIVE;
        }
        return ThreeOneTwoSubjectMatcher.toTrackLabel(req.getFirstSubject());
    }

    private void normalizePublicRequest(VolunteerService.GenerateRequest req) {
        String provinceCode = provincePolicyService.normalizeProvinceCode(req.getProvinceCode());
        req.setProvinceCode(provinceCode);
        req.setBatchCode(policyRuleService.normalizeBatchCode(req.getBatchCode()));
        if (req.getScore() != null && req.getScore() > 0) {
            req.setTotalScore(req.getScore());
        }
        if (req.getRank() != null && req.getRank() > 0) {
            req.setProvinceRank(req.getRank());
        }
        if ((req.getFirstSubject() == null || req.getFirstSubject().isBlank()) && req.getSubjectType() != null) {
            String subject = req.getSubjectType().replace("类", "").trim();
            req.setFirstSubject(subject);
        }
        if ((req.getResubjects() == null || req.getResubjects().isEmpty()) && req.getSelectedSubjects() != null) {
            String first = req.getFirstSubject() == null ? "" : req.getFirstSubject().trim();
            req.setResubjects(req.getSelectedSubjects().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(s -> !s.equals(first) && !(s + "类").equals(req.getSubjectType()))
                    .limit(2)
                    .toList());
        }
        if (req.getPreferredRegions() == null || req.getPreferredRegions().isEmpty()) {
            List<String> regions = new ArrayList<>();
            if (req.getPreferredCities() != null) {
                regions.addAll(req.getPreferredCities());
            }
            if (req.getPreferredProvinces() != null) {
                regions.addAll(req.getPreferredProvinces());
            }
            req.setPreferredRegions(regions);
        }
        if (req.getAcceptPrivate() == null && req.getAcceptPrivateSchool() != null) {
            req.setAcceptPrivate(req.getAcceptPrivateSchool());
        }
        if (req.getAcceptSinoForeign() == null && req.getAcceptChineseForeignCoop() != null) {
            req.setAcceptSinoForeign(req.getAcceptChineseForeignCoop());
        }
        if (req.getStrategyMode() == null || req.getStrategyMode().isBlank()) {
            req.setStrategyMode(normalizeRiskPreference(req.getRiskPreference()));
        }
    }

    private String normalizeRiskPreference(String riskPreference) {
        if (riskPreference == null || riskPreference.isBlank()) {
            return "均衡型";
        }
        String value = riskPreference.trim().toLowerCase();
        return switch (value) {
            case "conservative", "保守", "保守型" -> "保守型";
            case "aggressive", "激进", "冲刺", "冲刺型" -> "冲刺型";
            default -> "均衡型";
        };
    }

    /**
     * 政策上下文注入。目前经 GenerateRequest 的 policy* 字段传递（过渡机制，
     * 待演进为独立 GenerationContext，见设计文档待决事项）。
     */
    private void applyPolicyToRequest(VolunteerService.GenerateRequest req,
                                      com.gzly.entity.PolicyRuleConfig config,
                                      String provinceCode) {
        if (req == null || config == null) {
            return;
        }
        req.setPolicyMaxVolunteerCount(config.getMaxVolunteerCount());
        req.setPolicyBatchName(config.getBatchName());
        req.setPolicyVolunteerUnitLabel(config.getVolunteerMode());
        req.setPolicyGradientPresetJson(config.getGradientPresetJson());
        req.setPolicyChanceParamsJson(config.getChanceParamsJson());
        String unitType = config.getVolunteerUnitType();
        if (unitType == null || unitType.isBlank()) {
            unitType = provincePolicyService.isProfessionalGroupProvince(provinceCode)
                    ? ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45
                    : ProvincePolicyService.UNIT_MAJOR_96;
        }
        req.setPolicyVolunteerUnitType(unitType);
    }
}
