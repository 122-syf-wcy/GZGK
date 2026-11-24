package com.gzgk.core.policy;

import com.gzgk.core.policy.ProvincePolicyModels.ProvincePolicyView;
import com.gzgk.core.scoreline.ScoreLineService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProvincePolicyService {

    private final ScoreLineService scoreLineService;
    private final Map<String, ProvincePolicyView> policyMap = new LinkedHashMap<>();

    public ProvincePolicyService(ScoreLineService scoreLineService) {
        this.scoreLineService = scoreLineService;
        initDefaults();
    }

    private void initDefaults() {
        register("GZ", "贵州", "3+1+2", "RAW_750", "MAJOR_96", "专业(类)+院校平行志愿", 96, 0, false, 0.06,
                "贵州省招生考试院", "https://zsksy.guizhou.gov.cn/", Map.of("sprint", 18, "steady", 36, "safe", 28, "pad", 14));
        register("SC", "四川", "3+1+2", "RAW_750", "PROFESSIONAL_GROUP_45", "院校专业组平行志愿", 45, 6, true, 0.06,
                "四川省教育考试院", "https://www.sceea.cn/", Map.of("sprint", 10, "steady", 18, "safe", 12, "pad", 5));
        register("HB", "湖北", "3+1+2", "RAW_750", "PROFESSIONAL_GROUP_45", "院校专业组平行志愿", 45, 6, true, 0.06,
                "湖北省教育考试院", "http://www.hbea.edu.cn/", Map.of("sprint", 15, "steady", 15, "safe", 11, "pad", 4));
        register("AH", "安徽", "3+1+2", "RAW_750", "PROFESSIONAL_GROUP_45", "院校专业组平行志愿", 45, 6, true, 0.06,
                "安徽省教育招生考试院", "https://www.ahzsks.cn/", Map.of("sprint", 10, "steady", 18, "safe", 12, "pad", 5));
        register("GX", "广西", "3+1+2", "RAW_750", "PROFESSIONAL_GROUP_45", "院校专业组平行志愿", 40, 20, true, 0.06,
                "广西壮族自治区招生考试院", "https://www.gxeea.cn/", Map.of("sprint", 9, "steady", 16, "safe", 11, "pad", 4));
        register("HI", "海南", "3+3", "STANDARD_900", "PROFESSIONAL_GROUP_45", "院校专业组平行志愿", 30, 6, true, 0.07,
                "海南省考试局", "http://ea.hainan.gov.cn/", Map.of("sprint", 10, "steady", 10, "safe", 7, "pad", 3));
        register("YN", "云南", "3+1+2", "RAW_750", "PROFESSIONAL_GROUP_45", "院校专业组平行志愿", 40, 10, true, 0.06,
                "云南省招生考试院", "https://www.ynzs.cn/", Map.of("sprint", 8, "steady", 16, "safe", 11, "pad", 5));
        register("HA", "河南", "3+1+2", "RAW_750", "PROFESSIONAL_GROUP_45", "院校专业组平行志愿", 48, 6, true, 0.08,
                "河南省教育考试院", "http://www.haeea.cn/", Map.of("sprint", 10, "steady", 18, "safe", 14, "pad", 6));
    }

    private void register(String code, String name, String subjMode, String scoreSys, String unitType,
                          String modeLabel, int maxCount, int majorPerGroup, boolean adj, double sigmaMin,
                          String srcName, String srcUrl, Map<String, Integer> grad) {
        policyMap.put(code, new ProvincePolicyView(
                code, name, subjMode, scoreSys, unitType, modeLabel, maxCount, majorPerGroup,
                adj, sigmaMin, "ESTIMATE", srcName, srcUrl, grad));
    }

    public List<ProvincePolicyView> listAll() {
        return policyMap.values().stream().map(this::withLiveReadiness).toList();
    }

    public ProvincePolicyView getPolicy(String provinceCode) {
        String code = provinceCode == null ? "GZ" : provinceCode.trim().toUpperCase(Locale.ROOT);
        ProvincePolicyView base = policyMap.getOrDefault(code, policyMap.get("GZ"));
        return withLiveReadiness(base);
    }

    private ProvincePolicyView withLiveReadiness(ProvincePolicyView base) {
        // Real FULL readiness upgrade when official score-rank & admission lines exist!
        String readiness = scoreLineService.hasOfficialDataForProvince(base.provinceCode()) ? "FULL" : "ESTIMATE";
        return new ProvincePolicyView(
                base.provinceCode(), base.provinceName(), base.subjectMode(), base.scoreSystem(),
                base.volunteerUnitType(), base.volunteerModeLabel(), base.maxVolunteerCount(),
                base.majorPerGroupCount(), base.hasAdjustment(), base.sigmaMin(), readiness,
                base.officialSourceName(), base.officialSourceUrl(), base.defaultGradientCounts()
        );
    }
}
