package com.gzly.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NextProvinceBatchSupportService {

    private final AdmissionYearService admissionYearService;
    private final DataYearReadinessService dataYearReadinessService;

    public BatchSupportService.BatchSupportResponse supportMatrix(String provinceCode, Integer year) {
        return supportMatrix(provinceCode, year, false);
    }

    public BatchSupportService.BatchSupportResponse supportMatrix(String provinceCode, Integer year,
                                                                  boolean publicYearLocked) {
        String normalizedProvince = provinceCode == null || provinceCode.isBlank()
                ? NextProvincePolicyRegistry.GX
                : provinceCode.trim().toUpperCase(Locale.ROOT);
        NextProvincePolicyRegistry.Profile profile = NextProvincePolicyRegistry.require(normalizedProvince);
        int resolvedYear = year == null || year <= 0 ? admissionYearService.getActiveAdmissionYear() : year;

        BatchSupportService.DataReadiness readiness = conservativeReadiness(profile, resolvedYear);
        List<BatchSupportService.BatchSupportItem> items = profile.batches().stream()
                .map(batch -> buildItem(profile, batch, readiness))
                .toList();

        BatchSupportService.BatchSupportResponse response = new BatchSupportService.BatchSupportResponse();
        response.setProvinceCode(profile.provinceCode());
        response.setYear(resolvedYear);
        response.setActiveAdmissionYear(admissionYearService.getActiveAdmissionYear());
        response.setLatestOfficialDataYear(admissionYearService.getLatestOfficialDataYear());
        response.setHistoryYears(admissionYearService.resolveHistoryYears(admissionYearService.getActiveAdmissionYear()));
        response.setTrainingYears(admissionYearService.resolveTrainingYears());
        response.setTargetYear(admissionYearService.getTargetYear());
        response.setFutureImportYear(admissionYearService.getFutureImportYear());
        response.setPublicYearLocked(publicYearLocked);
        response.setDataReadiness(readiness);
        response.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        response.setOfficialDataReady(false);
        response.setEstimateMode(true);
        response.setDataSourceYears(response.getTrainingYears());
        response.setItems(items);
        response.setSummary(summaryFor(items));
        return response;
    }

    private BatchSupportService.DataReadiness conservativeReadiness(NextProvincePolicyRegistry.Profile profile,
                                                                    int year) {
        BatchSupportService.DataReadiness base = null;
        try {
            base = dataYearReadinessService == null
                    ? null
                    : dataYearReadinessService.getOrDefaultReadiness(profile.provinceCode(), year);
        } catch (Exception ignored) {
        }
        BatchSupportService.DataReadiness readiness = new BatchSupportService.DataReadiness();
        readiness.setProvinceCode(profile.provinceCode());
        readiness.setYear(year);
        readiness.setPolicyReady(false);
        readiness.setScoreSegmentReady(false);
        readiness.setAdmissionPlanReady(false);
        readiness.setMajorRequirementReady(false);
        readiness.setMajorMetaReady(false);
        readiness.setMlTrainingReady(false);
        readiness.setHistoricalTrainingReady(base == null || base.isHistoricalTrainingReady());
        readiness.setRecommendationPhase(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        readiness.setLatestImportBatchId("");
        readiness.setLastCheckedAt(base == null ? "" : base.getLastCheckedAt());
        readiness.setRemarks(profile.dataStatusDetail());
        return readiness;
    }

    private BatchSupportService.BatchSupportItem buildItem(NextProvincePolicyRegistry.Profile profile,
                                                           NextProvincePolicyRegistry.BatchProfile batch,
                                                           BatchSupportService.DataReadiness readiness) {
        BatchSupportService.BatchSupportItem item = new BatchSupportService.BatchSupportItem();
        item.setBatchCode(batch.batchCode());
        item.setBatchName(batch.batchName());
        item.setCandidateType(batch.candidateType());
        item.setCategory(batch.category());
        item.setSupportLevel(batch.supportLevel());
        item.setRecommendMode(batch.recommendMode());
        item.setEngine(batch.engineName());
        item.setEngineName(batch.engineName());
        item.setTargetCount(batch.targetCount());
        item.setMaxVolunteerCount(batch.targetCount());
        item.setMajorPerSchoolCount(batch.majorPerSchoolCount());
        item.setHasAdjustment(batch.hasAdjustment());
        item.setVolunteerMode(batch.volunteerMode());
        item.setPolicyConfigured(true);
        item.setPolicyStatus("registry_only");
        item.setScoreLineCount(0);
        item.setMajorScoreCount(0);
        item.setHistoricalScoreLineCount(batch.ordinaryEstimate() ? 1L : 0L);
        item.setHistoricalMajorScoreCount(0L);
        item.setPlanCount(0);
        item.setRequirementCount(0);
        item.setDataStatus(dataStatus(profile, batch, readiness));
        item.setMissingData(batch.missingData());
        item.setSupportNote(batch.supportNote());
        item.setSupportReason(batch.ordinaryEstimate()
                ? "当前为 PRE_OFFICIAL_DATA 历史估算能力，基于 2024/2025 数据窗口展示趋势和缺口，不开放完整推荐。"
                : batch.supportNote());
        List<String> warnings = new ArrayList<>();
        warnings.add(AdmissionYearService.PRE_OFFICIAL_DATA_WARNING);
        warnings.add(batch.ordinaryEstimate() ? profile.supportNoteForBatch(batch.batchCode()) : profile.dataStatusDetail());
        warnings.add(batch.supportNote());
        if (NextProvincePolicyRegistry.HI.equals(profile.provinceCode())) {
            warnings.add("海南按 3+3 selectedSubjects 匹配 requiredSubjects，不按物理/历史分轨。");
        }
        if (NextProvincePolicyRegistry.YN.equals(profile.provinceCode())
                || NextProvincePolicyRegistry.HA.equals(profile.provinceCode())) {
            warnings.add(profile.provinceName() + "为首年新高考窗口，历史数据不足，2024 旧文理不得硬映射为物理/历史。");
        }
        item.setWarnings(warnings.stream().distinct().toList());
        return item;
    }

    private BatchSupportService.DataStatus dataStatus(NextProvincePolicyRegistry.Profile profile,
                                                      NextProvincePolicyRegistry.BatchProfile batch,
                                                      BatchSupportService.DataReadiness readiness) {
        BatchSupportService.DataStatus status = new BatchSupportService.DataStatus();
        status.setPolicyCount(1);
        status.setScoreLineCount(0);
        status.setMajorScoreCount(0);
        status.setHistoryCount(batch.ordinaryEstimate() ? 1L : 0L);
        status.setPlanCount(0);
        status.setRequirementCount(0);
        status.setReady(false);
        status.setStatus(AdmissionYearService.PHASE_PRE_OFFICIAL_DATA);
        status.setDetail(batch.ordinaryEstimate()
                ? profile.provinceName() + "普通主批可展示历史估算能力；2026 官方数据待发布。"
                : profile.dataStatusDetail());
        return status;
    }

    private Map<String, Long> summaryFor(List<BatchSupportService.BatchSupportItem> items) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (String level : List.of("FULL_RECOMMEND", "TRIAL_RECOMMEND", "QUERY_ONLY", "UNSUPPORTED")) {
            summary.put(level, items.stream().filter(item -> level.equals(item.getSupportLevel())).count());
        }
        return summary;
    }
}
