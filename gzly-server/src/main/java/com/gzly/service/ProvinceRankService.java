package com.gzly.service;

import com.gzly.entity.DataScoreRank;
import com.gzly.mapper.DataScoreRankMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvinceRankService {

    private final ProvincePolicyService provincePolicyService;
    private final AlgorithmService algorithmService;
    private final DataScoreRankMapper dataScoreRankMapper;

    public AlgorithmService.RankEstimate estimateRank(String provinceCode, int score,
                                                       String subjectType, Integer requestedYear) {
        String code = provincePolicyService.normalizeProvinceCode(provinceCode);
        if (ProvincePolicyService.GZ.equals(code)) {
            return algorithmService.estimateRank(score, subjectType, requestedYear);
        }
        return estimateGeneric(code, score, subjectType, requestedYear);
    }

    private AlgorithmService.RankEstimate estimateGeneric(String provinceCode, int score,
                                                          String subjectType, Integer requestedYear) {
        ProvincePolicyService.ProvincePolicy policy = provincePolicyService.getPolicy(provinceCode);
        AlgorithmService.RankEstimate result = new AlgorithmService.RankEstimate();
        result.setScore(score);
        result.setSubjectType(subjectType);
        try {
            Integer queryYear = requestedYear;
            if (queryYear == null) {
                queryYear = dataScoreRankMapper.selectLatestYear(provinceCode, subjectType);
            }
            if (queryYear == null) {
                result.setConfidence("数据不足");
                result.setNote(String.format("未导入%s官方一分一段表（%s），系统不提供分数推位次结果。",
                        policy.getProvinceName(), subjectType));
                return result;
            }
            DataScoreRank line = dataScoreRankMapper.selectNearestAtOrBelow(provinceCode, queryYear, subjectType, score);
            if (line == null) {
                result.setReferenceYear(queryYear);
                result.setConfidence("数据不足");
                result.setNote(String.format(
                        "未导入%d年%s%s官方一分一段表或该分数低于已导入最低分段，系统不做跨年份、跨科类换算。",
                        queryYear, policy.getProvinceName(), subjectType));
                return result;
            }
            result.setEstimatedRank(line.getRankHigh());
            result.setRankLow(line.getRankLow());
            result.setRankHigh(line.getRankHigh());
            result.setDataPoints(1);
            result.setReferenceYear(line.getYear());
            result.setConfidence("官方");
            result.setSource(line.getSourceName());
            result.setSourceUrl(line.getSourceUrl());
            result.setSourcePageUrl(line.getSourcePageUrl());
            result.setParseMethod(line.getParseMethod());
            result.setNote(String.format(
                    "%d年%s%s官方一分一段表：%s分同分位次区间约%d-%d名。",
                    line.getYear(), policy.getProvinceName(), subjectType, line.getScoreLabel(),
                    line.getRankLow(), line.getRankHigh()));
            return result;
        } catch (Exception e) {
            log.warn("省份官方一分一段表查询失败: provinceCode={}, score={}, subjectType={}",
                    provinceCode, score, subjectType, e);
            result.setConfidence("数据不足");
            result.setNote(policy.getProvinceName() + "官方一分一段表暂不可用，请以考试院原表手动核对。");
            return result;
        }
    }
}
