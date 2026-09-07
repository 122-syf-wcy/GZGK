package com.gzly.algorithm.provider;

import com.gzly.algorithm.core.CandidateProvider;
import com.gzly.algorithm.core.CandidateQuery;
import com.gzly.algorithm.core.VolunteerUnitType;
import com.gzly.service.VolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「专业（类）+ 院校」候选提供者（贵州本科批 96；河南提前批 64 数据就绪后复用）。
 *
 * <p>阶段 1 绞杀者过渡：委派 {@link VolunteerService#fetchCandidates}，与主链路走同一段
 * 过滤/去重/排序代码，保证行为一致；阶段 2 将 pickGradient 实现体物理迁入本类。</p>
 */
@Component
@RequiredArgsConstructor
public class MajorPlusSchoolProvider implements CandidateProvider {

    private final VolunteerService volunteerService;

    @Override
    public VolunteerUnitType supports() {
        return VolunteerUnitType.MAJOR_PLUS_SCHOOL;
    }

    @Override
    public List<VolunteerService.VolunteerItem> fetch(CandidateQuery query) {
        if (query == null || query.getRequest() == null) {
            return List.of();
        }
        return volunteerService.fetchCandidates(query.getRequest(), query.getGradient(),
                query.getRankLow(), query.getRankHigh(), query.getLimit());
    }
}
