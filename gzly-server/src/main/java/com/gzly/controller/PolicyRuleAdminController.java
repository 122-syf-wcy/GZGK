package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gzly.common.Result;
import com.gzly.entity.PolicyRuleConfig;
import com.gzly.mapper.PolicyRuleConfigMapper;
import com.gzly.service.PolicyRuleService;
import com.gzly.service.ProvincePolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/policy-rules")
@RequiredArgsConstructor
public class PolicyRuleAdminController {

    private final PolicyRuleConfigMapper mapper;
    private final ProvincePolicyService provincePolicyService;
    private final PolicyRuleService policyRuleService;

    @GetMapping
    public Result<List<PolicyRuleConfig>> list(@RequestParam(required = false) String province,
                                               @RequestParam(required = false) Integer year,
                                               @RequestParam(required = false) String candidateType,
                                               @RequestParam(required = false) String batchCode) {
        LambdaQueryWrapper<PolicyRuleConfig> wrapper = new LambdaQueryWrapper<PolicyRuleConfig>()
                .orderByDesc(PolicyRuleConfig::getYear)
                .orderByAsc(PolicyRuleConfig::getBatchCode);
        if (province != null && !province.isBlank()) {
            wrapper.eq(PolicyRuleConfig::getProvince, provincePolicyService.normalizeProvinceCode(province));
        }
        if (year != null && year > 0) {
            wrapper.eq(PolicyRuleConfig::getYear, year);
        }
        if (candidateType != null && !candidateType.isBlank()) {
            wrapper.eq(PolicyRuleConfig::getCandidateType, candidateType.trim());
        }
        if (batchCode != null && !batchCode.isBlank()) {
            wrapper.eq(PolicyRuleConfig::getBatchCode, policyRuleService.normalizeBatchCode(batchCode));
        }
        return Result.ok(mapper.selectList(wrapper));
    }

    @PostMapping
    public Result<PolicyRuleConfig> save(@RequestBody PolicyRuleConfig req) {
        if (req == null) {
            return Result.fail(400, "政策配置不能为空");
        }
        req.setProvince(provincePolicyService.normalizeProvinceCode(req.getProvince()));
        req.setBatchCode(policyRuleService.normalizeBatchCode(req.getBatchCode()));
        if (req.getCandidateType() == null || req.getCandidateType().isBlank()) {
            req.setCandidateType(PolicyRuleService.DEFAULT_CANDIDATE_TYPE);
        }
        if (req.getYear() == null || req.getYear() <= 0) {
            return Result.fail(400, "年份不能为空");
        }
        if (req.getMaxVolunteerCount() == null || req.getMaxVolunteerCount() <= 0) {
            return Result.fail(400, "最大志愿数量必须大于0");
        }
        if (req.getEnabled() == null) {
            req.setEnabled(1);
        }
        if (req.getPolicyStatus() == null || req.getPolicyStatus().isBlank()) {
            req.setPolicyStatus("pending_confirm");
        }
        req.setUpdatedAt(LocalDateTime.now());

        if (req.getId() != null && req.getId() > 0) {
            mapper.updateById(req);
            return Result.ok(mapper.selectById(req.getId()));
        }

        PolicyRuleConfig existing = mapper.selectOne(new LambdaQueryWrapper<PolicyRuleConfig>()
                .eq(PolicyRuleConfig::getProvince, req.getProvince())
                .eq(PolicyRuleConfig::getYear, req.getYear())
                .eq(PolicyRuleConfig::getCandidateType, req.getCandidateType())
                .eq(PolicyRuleConfig::getBatchCode, req.getBatchCode())
                .last("LIMIT 1"));
        if (existing != null) {
            req.setId(existing.getId());
            mapper.updateById(req);
            return Result.ok(mapper.selectById(existing.getId()));
        }
        req.setCreatedAt(LocalDateTime.now());
        mapper.insert(req);
        return Result.ok(req);
    }

    @PostMapping("/{id}/enabled")
    public Result<Void> enabled(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean enabled) {
        mapper.update(null, new LambdaUpdateWrapper<PolicyRuleConfig>()
                .eq(PolicyRuleConfig::getId, id)
                .set(PolicyRuleConfig::getEnabled, enabled ? 1 : 0)
                .set(PolicyRuleConfig::getUpdatedAt, LocalDateTime.now()));
        return Result.ok();
    }
}
