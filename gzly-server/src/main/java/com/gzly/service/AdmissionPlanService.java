package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.entity.AdmissionPlan;
import com.gzly.mapper.AdmissionPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 当年招生计划候选池查询服务。供推荐主流程在硬规则过滤前构建候选池使用。
 * 表 admission_plan 当前为标准化扩展表；旧路径仍走 data_score_line_gz / data_major_score。
 */
@Service
@RequiredArgsConstructor
public class AdmissionPlanService {

    private final AdmissionPlanMapper admissionPlanMapper;

    public List<AdmissionPlan> findCandidates(String province,
                                              Integer year,
                                              String batchCode,
                                              String candidateType,
                                              String subjectType) {
        if (province == null || year == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AdmissionPlan> qw = new LambdaQueryWrapper<>();
        qw.eq(AdmissionPlan::getProvince, province)
          .eq(AdmissionPlan::getYear, year);
        if (batchCode != null && !batchCode.isBlank()) {
            qw.eq(AdmissionPlan::getBatchCode, batchCode);
        }
        if (candidateType != null && !candidateType.isBlank()) {
            qw.eq(AdmissionPlan::getCandidateType, candidateType);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(AdmissionPlan::getSubjectType, subjectType);
        }
        return admissionPlanMapper.selectList(qw);
    }

    public AdmissionPlan findOne(String province,
                                 Integer year,
                                 String batchCode,
                                 String candidateType,
                                 String subjectType,
                                 String schoolCode,
                                 String majorCode) {
        LambdaQueryWrapper<AdmissionPlan> qw = new LambdaQueryWrapper<>();
        qw.eq(AdmissionPlan::getProvince, province)
          .eq(AdmissionPlan::getYear, year)
          .eq(AdmissionPlan::getBatchCode, batchCode)
          .eq(AdmissionPlan::getCandidateType, candidateType)
          .eq(AdmissionPlan::getSubjectType, subjectType)
          .eq(AdmissionPlan::getSchoolCode, schoolCode)
          .eq(AdmissionPlan::getMajorCode, majorCode)
          .last("LIMIT 1");
        return admissionPlanMapper.selectOne(qw);
    }

    public long count(String province, Integer year, String batchCode, String subjectType) {
        LambdaQueryWrapper<AdmissionPlan> qw = new LambdaQueryWrapper<>();
        qw.eq(AdmissionPlan::getProvince, province)
          .eq(AdmissionPlan::getYear, year);
        if (batchCode != null && !batchCode.isBlank()) {
            qw.eq(AdmissionPlan::getBatchCode, batchCode);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(AdmissionPlan::getSubjectType, subjectType);
        }
        return admissionPlanMapper.selectCount(qw);
    }
}
