package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.common.Result;
import com.gzly.entity.SpecialAdmissionPolicy;
import com.gzly.mapper.SpecialAdmissionPolicyMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/special-admissions")
@RequiredArgsConstructor
public class SpecialAdmissionController {

    private final SpecialAdmissionPolicyMapper policyMapper;

    @GetMapping("/categories")
    public Result<List<CategoryView>> categories(@RequestParam(defaultValue = "2026") Integer year) {
        List<SpecialAdmissionPolicy> policies = policyMapper.selectList(baseQuery(year));
        Map<String, CategoryView> categoryMap = new LinkedHashMap<>();
        for (SpecialAdmissionPolicy policy : policies) {
            CategoryView view = categoryMap.computeIfAbsent(policy.getCategory(), key -> {
                CategoryView category = new CategoryView();
                category.setCategory(policy.getCategory());
                category.setCategoryName(policy.getCategoryName());
                category.setCount(0);
                return category;
            });
            view.setCount(view.getCount() + 1);
        }
        return Result.ok(new ArrayList<>(categoryMap.values()));
    }

    @GetMapping("/policies")
    public Result<List<PolicyView>> policies(
            @RequestParam(defaultValue = "2026") Integer year,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "100") Integer limit) {
        limit = Math.min(Math.max(limit, 1), 100);
        LambdaQueryWrapper<SpecialAdmissionPolicy> query = baseQuery(year);
        if (category != null && !category.isBlank()) {
            query.eq(SpecialAdmissionPolicy::getCategory, category.trim());
        }
        query.last("LIMIT " + limit);
        return Result.ok(policyMapper.selectList(query).stream().map(this::toView).toList());
    }

    @GetMapping("/latest")
    public Result<List<PolicyView>> latest(
            @RequestParam(defaultValue = "2026") Integer year,
            @RequestParam(defaultValue = "6") Integer limit) {
        limit = Math.min(Math.max(limit, 1), 20);
        LambdaQueryWrapper<SpecialAdmissionPolicy> query = baseQuery(year)
                .last("LIMIT " + limit);
        return Result.ok(policyMapper.selectList(query).stream().map(this::toView).toList());
    }

    private LambdaQueryWrapper<SpecialAdmissionPolicy> baseQuery(Integer year) {
        return new LambdaQueryWrapper<SpecialAdmissionPolicy>()
                .eq(SpecialAdmissionPolicy::getStatus, 1)
                .eq(SpecialAdmissionPolicy::getYear, year)
                .orderByDesc(SpecialAdmissionPolicy::getSortOrder)
                .orderByDesc(SpecialAdmissionPolicy::getPublishedAt)
                .orderByDesc(SpecialAdmissionPolicy::getId);
    }

    private PolicyView toView(SpecialAdmissionPolicy policy) {
        PolicyView view = new PolicyView();
        view.setId(policy.getId());
        view.setYear(policy.getYear());
        view.setCategory(policy.getCategory());
        view.setCategoryName(policy.getCategoryName());
        view.setTitle(policy.getTitle());
        view.setSummary(policy.getSummary());
        view.setContentMd(policy.getContentMd());
        view.setOfficialUrl(policy.getOfficialUrl());
        view.setSourceName(policy.getSourceName());
        view.setSourceType(policy.getSourceType());
        view.setApplyStart(policy.getApplyStart());
        view.setApplyEnd(policy.getApplyEnd());
        view.setExamTime(policy.getExamTime());
        view.setTargetStudents(policy.getTargetStudents());
        view.setRequirements(policy.getRequirements());
        view.setPublishedAt(policy.getPublishedAt());
        return view;
    }

    @Data
    public static class CategoryView {
        private String category;
        private String categoryName;
        private Integer count;
    }

    @Data
    public static class PolicyView {
        private Long id;
        private Integer year;
        private String category;
        private String categoryName;
        private String title;
        private String summary;
        private String contentMd;
        private String officialUrl;
        private String sourceName;
        private String sourceType;
        private LocalDate applyStart;
        private LocalDate applyEnd;
        private String examTime;
        private String targetStudents;
        private String requirements;
        private LocalDateTime publishedAt;
    }
}
