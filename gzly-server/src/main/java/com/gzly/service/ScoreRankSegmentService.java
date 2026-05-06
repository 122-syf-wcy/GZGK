package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.entity.ScoreRankSegment;
import com.gzly.mapper.ScoreRankSegmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 一分一段查询服务，用于把分数换算到位次区间，或反向给出位次推算。
 * 与现有 ScoreRankGz 表并存：旧表只覆盖贵州历史；新表 score_rank_segment 服务多省扩展。
 */
@Service
@RequiredArgsConstructor
public class ScoreRankSegmentService {

    private final ScoreRankSegmentMapper scoreRankSegmentMapper;

    public ScoreRankSegment findByScore(String province,
                                        Integer year,
                                        String candidateType,
                                        String subjectType,
                                        Integer score) {
        if (province == null || year == null || score == null) {
            return null;
        }
        LambdaQueryWrapper<ScoreRankSegment> qw = new LambdaQueryWrapper<>();
        qw.eq(ScoreRankSegment::getProvince, province)
          .eq(ScoreRankSegment::getYear, year)
          .eq(ScoreRankSegment::getScore, score);
        if (candidateType != null && !candidateType.isBlank()) {
            qw.eq(ScoreRankSegment::getCandidateType, candidateType);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(ScoreRankSegment::getSubjectType, subjectType);
        }
        qw.last("LIMIT 1");
        return scoreRankSegmentMapper.selectOne(qw);
    }

    public List<ScoreRankSegment> findTable(String province,
                                            Integer year,
                                            String candidateType,
                                            String subjectType) {
        if (province == null || year == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ScoreRankSegment> qw = new LambdaQueryWrapper<>();
        qw.eq(ScoreRankSegment::getProvince, province)
          .eq(ScoreRankSegment::getYear, year);
        if (candidateType != null && !candidateType.isBlank()) {
            qw.eq(ScoreRankSegment::getCandidateType, candidateType);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(ScoreRankSegment::getSubjectType, subjectType);
        }
        qw.orderByDesc(ScoreRankSegment::getScore);
        return scoreRankSegmentMapper.selectList(qw);
    }

    public long count(String province, Integer year, String subjectType) {
        LambdaQueryWrapper<ScoreRankSegment> qw = new LambdaQueryWrapper<>();
        if (province != null) {
            qw.eq(ScoreRankSegment::getProvince, province);
        }
        if (year != null) {
            qw.eq(ScoreRankSegment::getYear, year);
        }
        if (subjectType != null && !subjectType.isBlank()) {
            qw.eq(ScoreRankSegment::getSubjectType, subjectType);
        }
        return scoreRankSegmentMapper.selectCount(qw);
    }
}
