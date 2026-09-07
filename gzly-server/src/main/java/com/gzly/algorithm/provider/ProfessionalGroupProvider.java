package com.gzly.algorithm.provider;

import com.gzly.algorithm.core.CandidateProvider;
import com.gzly.algorithm.core.CandidateQuery;
import com.gzly.algorithm.core.VolunteerUnitType;
import com.gzly.service.ProfessionalGroupVolunteerService;
import com.gzly.service.VolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「院校专业组」候选提供者（川/鄂/皖/桂/琼/滇/豫本科批）。
 *
 * <p>阶段 1 绞杀者过渡：委派 {@link ProfessionalGroupVolunteerService#fetchCandidates}，
 * 与该链路走同一段查询/去重代码；阶段 2 并线时本类接上共享算法层
 * （机会指数/梯度/排序/诊断，见 docs/MULTI_PROVINCE_ALGORITHM_REFACTOR.md 阶段 2）。</p>
 */
@Component
@RequiredArgsConstructor
public class ProfessionalGroupProvider implements CandidateProvider {

    private final ProfessionalGroupVolunteerService professionalGroupVolunteerService;

    @Override
    public VolunteerUnitType supports() {
        return VolunteerUnitType.PROFESSIONAL_GROUP;
    }

    @Override
    public List<VolunteerService.VolunteerItem> fetch(CandidateQuery query) {
        if (query == null || query.getRequest() == null) {
            return List.of();
        }
        return professionalGroupVolunteerService.fetchCandidates(query.getRequest(), query.getGradient(),
                query.getRankLow(), query.getRankHigh(), query.getLimit());
    }
}
