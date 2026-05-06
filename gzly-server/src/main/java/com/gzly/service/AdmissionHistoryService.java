package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.entity.AdmissionHistory;
import com.gzly.mapper.AdmissionHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 历史录取数据查询服务，供位次预测引擎使用。
 */
@Service
@RequiredArgsConstructor
public class AdmissionHistoryService {

    private final AdmissionHistoryMapper admissionHistoryMapper;

    public List<AdmissionHistory> findRecentYears(String province,
                                                  String batchCode,
                                                  String subjectType,
                                                  String schoolCode,
                                                  String majorCode,
                                                  int yearLookback) {
        if (province == null || schoolCode == null || majorCode == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AdmissionHistory> qw = new LambdaQueryWrapper<>();
        qw.eq(AdmissionHistory::getProvince, province)
          .eq(AdmissionHistory::getSchoolCode, schoolCode)
          .eq(AdmissionHistory::getMajorCode, majorCode);
        if (batchCode != null && !batchCode.isBlank()) {
            qw.eq(AdmissionHistory::getBatchCode, batchCode);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(AdmissionHistory::getSubjectType, subjectType);
        }
        qw.orderByDesc(AdmissionHistory::getYear);
        if (yearLookback > 0) {
            qw.last("LIMIT " + yearLookback);
        }
        return admissionHistoryMapper.selectList(qw);
    }

    public List<AdmissionHistory> findBySchool(String province,
                                               String batchCode,
                                               String subjectType,
                                               String schoolCode) {
        LambdaQueryWrapper<AdmissionHistory> qw = new LambdaQueryWrapper<>();
        qw.eq(AdmissionHistory::getProvince, province)
          .eq(AdmissionHistory::getSchoolCode, schoolCode);
        if (batchCode != null && !batchCode.isBlank()) {
            qw.eq(AdmissionHistory::getBatchCode, batchCode);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(AdmissionHistory::getSubjectType, subjectType);
        }
        qw.orderByDesc(AdmissionHistory::getYear);
        return admissionHistoryMapper.selectList(qw);
    }

    public long count() {
        return admissionHistoryMapper.selectCount(null);
    }
}
