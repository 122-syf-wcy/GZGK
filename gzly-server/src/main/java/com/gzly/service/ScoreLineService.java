package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.PageResult;
import com.gzly.entity.DataAdmissionGroupLine;
import com.gzly.entity.MajorScoreGz;
import com.gzly.entity.ScoreRankGz;
import com.gzly.entity.ScoreLineGz;
import com.gzly.entity.University;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import com.gzly.mapper.ScoreLineGzMapper;
import com.gzly.mapper.UniversityMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScoreLineService {

    private final ScoreLineGzMapper scoreLineGzMapper;
    private final MajorScoreGzMapper majorScoreGzMapper;
    private final ScoreRankGzMapper scoreRankGzMapper;
    private final UniversityMapper universityMapper;
    private final DataAdmissionGroupLineMapper dataAdmissionGroupLineMapper;
    private final ProvincePolicyService provincePolicyService;

    @Data
    public static class ScoreLineView {
        private Long id;
        private String provinceCode;
        private String schoolId;
        private String universityName;
        private String groupCode;
        private String groupName;
        private String majorName;
        private String majorId;
        private Integer year;
        private String subjectType;
        private Integer minScore;
        private Integer maxScore;
        private Integer avgScore;
        private Integer minRank;
        private Integer planCount;
        private String batch;
        private String resubjectRequirement;
        private String dataSourceType;
        private String confidenceLabel;
        private String rankSourceType; // original / score_rank_converted / missing
        private String rankSourceNote;
        private Integer rankLow;
        private Integer rankHigh;
        private String rankSourceUrl;
        private String rankSourcePageUrl;
        private String sourceUrl;
        private String sourcePageUrl;
    }

    @Data
    public static class SchoolScoreSummary {
        private Long id;
        private String provinceCode;
        private String volunteerUnitType;
        private String schoolId;
        private String universityName;
        private String groupCode;
        private String groupName;
        private String subjectType;
        private Integer latestYear;
        private Integer minScore;
        private Integer minRank;
        private String batch;
        private Integer availableYearCount;
        private Integer firstYear;
        private Integer lastYear;
        private String dataSourceType;
        private String confidenceLabel;
    }

    @Cacheable(value = "scoreLines",
               key = "#year + '_' + #subjectType + '_' + #universityName + '_' + #majorName + '_' + #page + '_' + #pageSize")
    public PageResult<ScoreLineView> list(Integer year, String subjectType, String universityName,
                                         String majorName, int page, int pageSize) {
        return list("GZ", year, subjectType, universityName, majorName, page, pageSize);
    }

    @Cacheable(value = "scoreLines",
               key = "#provinceCode + '_' + #year + '_' + #subjectType + '_' + #universityName + '_' + #majorName + '_' + #page + '_' + #pageSize")
    public PageResult<ScoreLineView> list(String provinceCode, Integer year, String subjectType, String universityName,
                                         String majorName, int page, int pageSize) {
        String normalizedProvinceCode = provincePolicyService.normalizeProvinceCode(provinceCode);
        if (provincePolicyService.isProfessionalGroupProvince(normalizedProvinceCode)) {
            return listProfessionalGroups(normalizedProvinceCode, year, subjectType, universityName, page, pageSize);
        }
        LambdaQueryWrapper<MajorScoreGz> majorWrapper = new LambdaQueryWrapper<>();
        if (year != null) {
            majorWrapper.eq(MajorScoreGz::getYear, year);
        }
        if (StringUtils.isNotBlank(subjectType)) {
            majorWrapper.eq(MajorScoreGz::getSubjectType, subjectType);
        }
        if (StringUtils.isNotBlank(universityName)) {
            majorWrapper.like(MajorScoreGz::getUniversityName, universityName);
        }
        if (StringUtils.isNotBlank(majorName)) {
            majorWrapper.like(MajorScoreGz::getMajorName, majorName);
        }
        majorWrapper.orderByDesc(MajorScoreGz::getYear)
                .orderByAsc(MajorScoreGz::getMinRank)
                .orderByAsc(MajorScoreGz::getUniversityName);

        Page<MajorScoreGz> majorPage = majorScoreGzMapper.selectPage(new Page<>(page, pageSize), majorWrapper);
        if (majorPage.getTotal() > 0) {
            return PageResult.of(
                    majorPage.getRecords().stream().map(this::toView).toList(),
                    majorPage.getTotal(),
                    page,
                    pageSize);
        }

        LambdaQueryWrapper<ScoreLineGz> wrapper = new LambdaQueryWrapper<>();

        if (year != null) {
            wrapper.eq(ScoreLineGz::getYear, year);
        }
        if (StringUtils.isNotBlank(subjectType)) {
            wrapper.eq(ScoreLineGz::getSubjectType, subjectType);
        }
        if (StringUtils.isNotBlank(universityName)) {
            wrapper.like(ScoreLineGz::getUniversityName, universityName);
        }
        if (StringUtils.isNotBlank(majorName)) {
            wrapper.like(ScoreLineGz::getMajorName, majorName);
        }
        wrapper.orderByDesc(ScoreLineGz::getYear).orderByAsc(ScoreLineGz::getMinRank);

        Page<ScoreLineGz> p = scoreLineGzMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResult.of(p.getRecords().stream().map(this::toView).toList(), p.getTotal(), page, pageSize);
    }

    /**
     * 面向公开查询页的院校级聚合列表。每所学校只展示一张卡片，点击后再查看历年院校分。
     */
    public PageResult<SchoolScoreSummary> listSchoolSummaries(Integer year, String subjectType,
                                                              String universityName,
                                                              int page, int pageSize) {
        return listSchoolSummaries("GZ", year, subjectType, universityName, page, pageSize);
    }

    /**
     * 多省院校/院校专业组聚合列表。
     */
    public PageResult<SchoolScoreSummary> listSchoolSummaries(String provinceCode, Integer year, String subjectType,
                                                              String universityName,
                                                              int page, int pageSize) {
        String normalizedProvinceCode = provincePolicyService.normalizeProvinceCode(provinceCode);
        if (provincePolicyService.isProfessionalGroupProvince(normalizedProvinceCode)) {
            return listProfessionalGroupSummaries(normalizedProvinceCode, year, subjectType, universityName, page, pageSize);
        }
        LambdaQueryWrapper<ScoreLineGz> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ScoreLineGz::getId,
                        ScoreLineGz::getSchoolId,
                        ScoreLineGz::getUniversityName,
                        ScoreLineGz::getYear,
                        ScoreLineGz::getSubjectType,
                        ScoreLineGz::getMinScore,
                        ScoreLineGz::getMinRank,
                        ScoreLineGz::getBatch)
                .isNotNull(ScoreLineGz::getSchoolId)
                .ne(ScoreLineGz::getSchoolId, "")
                .isNotNull(ScoreLineGz::getMinScore)
                .gt(ScoreLineGz::getMinScore, 0)
                .isNotNull(ScoreLineGz::getMinRank)
                .gt(ScoreLineGz::getMinRank, 0);
        if (year != null) {
            wrapper.eq(ScoreLineGz::getYear, year);
        }
        if (StringUtils.isNotBlank(subjectType)) {
            wrapper.eq(ScoreLineGz::getSubjectType, subjectType);
        }
        if (StringUtils.isNotBlank(universityName)) {
            wrapper.like(ScoreLineGz::getUniversityName, universityName.trim());
        }
        wrapper.orderByAsc(ScoreLineGz::getMinRank)
                .orderByDesc(ScoreLineGz::getMinScore)
                .orderByAsc(ScoreLineGz::getUniversityName);

        Map<String, ScoreLineGz> bestBySchool = new LinkedHashMap<>();
        for (ScoreLineGz line : scoreLineGzMapper.selectList(wrapper)) {
            if (line == null || StringUtils.isBlank(line.getSchoolId())) {
                continue;
            }
            String key = line.getSchoolId() + "_" + line.getSubjectType();
            bestBySchool.putIfAbsent(key, line);
        }

        List<ScoreLineGz> rows = new ArrayList<>(bestBySchool.values());
        int total = rows.size();
        int from = Math.min(Math.max((page - 1) * pageSize, 0), total);
        int to = Math.min(from + pageSize, total);
        List<ScoreLineGz> pageRows = rows.subList(from, to);
        Map<String, List<ScoreLineGz>> historyMap = loadSchoolHistoryMap(pageRows, subjectType);

        List<SchoolScoreSummary> items = pageRows.stream()
                .map(line -> toSchoolSummary(line, historyMap.getOrDefault(line.getSchoolId(), List.of())))
                .toList();
        return PageResult.of(items, total, page, pageSize);
    }

    /**
     * 查询某所学校的历年院校级投档线，按年份倒序且每年只保留最低位次最靠前的一条记录。
     */
    public List<ScoreLineView> findSchoolHistory(String schoolId, String subjectType, int maxRecords) {
        return findSchoolHistory("GZ", schoolId, null, subjectType, maxRecords);
    }

    public List<ScoreLineView> findSchoolHistory(String provinceCode, String schoolId, String groupCode,
                                                 String subjectType, int maxRecords) {
        String normalizedProvinceCode = provincePolicyService.normalizeProvinceCode(provinceCode);
        if (provincePolicyService.isProfessionalGroupProvince(normalizedProvinceCode)) {
            return findProfessionalGroupHistory(normalizedProvinceCode, schoolId, groupCode, subjectType, maxRecords);
        }
        if (StringUtils.isBlank(schoolId)) {
            return List.of();
        }
        int limit = Math.min(Math.max(maxRecords, 1), 20);
        LambdaQueryWrapper<ScoreLineGz> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScoreLineGz::getSchoolId, schoolId)
                .isNotNull(ScoreLineGz::getMinScore)
                .gt(ScoreLineGz::getMinScore, 0)
                .orderByDesc(ScoreLineGz::getYear)
                .orderByAsc(ScoreLineGz::getMinRank);
        if (StringUtils.isNotBlank(subjectType)) {
            wrapper.eq(ScoreLineGz::getSubjectType, subjectType);
        }

        return scoreLineGzMapper.selectList(wrapper).stream()
                .map(this::toView)
                .filter(Objects::nonNull)
                .collect(LinkedHashMap<Integer, ScoreLineView>::new,
                        this::putPreferredHistoryView,
                        Map::putAll)
                .values()
                .stream()
                .limit(limit)
                .toList();
    }

    @Cacheable(value = "years", key = "'all'")
    public List<Integer> getAvailableYears() {
        return getAvailableYears("GZ");
    }

    public List<Integer> getAvailableYears(String provinceCode) {
        String normalizedProvinceCode = provincePolicyService.normalizeProvinceCode(provinceCode);
        if (provincePolicyService.isProfessionalGroupProvince(normalizedProvinceCode)) {
            return dataAdmissionGroupLineMapper.selectDistinctYears(normalizedProvinceCode).stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
        List<Integer> years = new ArrayList<>();
        years.addAll(majorScoreGzMapper.selectDistinctYears());
        years.addAll(scoreLineGzMapper.selectDistinctYears());
        return years.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * 96志愿引擎的核心查询 — 按位次范围查询候选志愿（院校级）
     * 同时查询新高考(物理类/历史类)和旧高考(理科/文科)的数据，覆盖5年
     */
    @Cacheable(value = "candidateScoreLines",
            key = "#subjectType + '_' + #rankLow + '_' + #rankHigh + '_' + (#resubjects == null ? 'none' : #resubjects.toString())",
            unless = "#result == null || #result.isEmpty()")
    public List<ScoreLineGz> findCandidates(String subjectType, int rankLow, int rankHigh,
                                             List<String> resubjects) {
        String legacyType = "物理类".equals(subjectType) ? "理科" : "文科";

        LambdaQueryWrapper<ScoreLineGz> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(ScoreLineGz::getSubjectType, subjectType)
                          .or().eq(ScoreLineGz::getSubjectType, legacyType))
               .between(ScoreLineGz::getMinRank, rankLow, rankHigh)
               .isNotNull(ScoreLineGz::getMinRank)
               .gt(ScoreLineGz::getMinRank, 0)
               .ge(ScoreLineGz::getYear, 2021)
               .orderByDesc(ScoreLineGz::getYear)
               .orderByAsc(ScoreLineGz::getMinRank);

        return scoreLineGzMapper.selectList(wrapper);
    }

    /**
     * 专业级查询 — 按位次范围查询 data_major_score_gz 表
     */
    @Cacheable(value = "candidateMajorScores",
            key = "#subjectType + '_' + #rankLow + '_' + #rankHigh + '_' + (#resubjects == null ? 'none' : #resubjects.toString())",
            unless = "#result == null || #result.isEmpty()")
    public List<MajorScoreGz> findMajorCandidates(String subjectType, int rankLow, int rankHigh,
                                                  List<String> resubjects) {
        String legacyType = "物理类".equals(subjectType) ? "理科" : "文科";

        LambdaQueryWrapper<MajorScoreGz> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(MajorScoreGz::getSubjectType, subjectType)
                          .or().eq(MajorScoreGz::getSubjectType, legacyType))
               .between(MajorScoreGz::getMinRank, rankLow, rankHigh)
               .isNotNull(MajorScoreGz::getMinRank)
               .gt(MajorScoreGz::getMinRank, 0)
               .ge(MajorScoreGz::getYear, 2021)
               .orderByDesc(MajorScoreGz::getYear)
               .orderByAsc(MajorScoreGz::getMinRank);

        return majorScoreGzMapper.selectList(wrapper);
    }

    /**
     * 查询某个推荐项最近三次可用录取记录。专业级优先；无专业级记录时回退院校级记录。
     */
    @Cacheable(value = "recentScoreHistory",
            key = "#schoolId + '_' + #majorName + '_' + #subjectType + '_' + #maxRecords",
            unless = "#result == null || #result.isEmpty()")
    public List<ScoreLineView> findRecentHistory(String schoolId, String majorName,
                                                 String subjectType, int maxRecords) {
        if (StringUtils.isBlank(schoolId) || StringUtils.isBlank(subjectType)) {
            return List.of();
        }
        int limit = Math.max(1, maxRecords);
        String legacyType = "物理类".equals(subjectType) ? "理科" : "文科";
        String majorCore = majorLookupCore(majorName);

        LambdaQueryWrapper<MajorScoreGz> majorWrapper = new LambdaQueryWrapper<>();
        majorWrapper.eq(MajorScoreGz::getSchoolId, schoolId)
                .and(w -> w.eq(MajorScoreGz::getSubjectType, subjectType)
                        .or().eq(MajorScoreGz::getSubjectType, legacyType))
                .isNotNull(MajorScoreGz::getMinScore)
                .gt(MajorScoreGz::getMinScore, 0)
                .ge(MajorScoreGz::getYear, 2021)
                .orderByDesc(MajorScoreGz::getYear)
                .orderByAsc(MajorScoreGz::getMinRank);
        if (StringUtils.isNotBlank(majorCore)) {
            majorWrapper.like(MajorScoreGz::getMajorName, majorCore);
        }
        List<ScoreLineView> majorViews = majorScoreGzMapper.selectList(majorWrapper).stream()
                .map(this::toView)
                .filter(Objects::nonNull)
                .filter(view -> StringUtils.isBlank(majorCore)
                        || majorLookupCore(view.getMajorName()).contains(majorCore)
                        || majorCore.contains(majorLookupCore(view.getMajorName())))
                .collect(LinkedHashMap<Integer, ScoreLineView>::new,
                        this::putPreferredHistoryView,
                        Map::putAll)
                .values()
                .stream()
                .limit(limit)
                .toList();
        if (!majorViews.isEmpty()) {
            return majorViews;
        }

        LambdaQueryWrapper<ScoreLineGz> schoolWrapper = new LambdaQueryWrapper<>();
        schoolWrapper.eq(ScoreLineGz::getSchoolId, schoolId)
                .and(w -> w.eq(ScoreLineGz::getSubjectType, subjectType)
                        .or().eq(ScoreLineGz::getSubjectType, legacyType))
                .isNotNull(ScoreLineGz::getMinScore)
                .gt(ScoreLineGz::getMinScore, 0)
                .ge(ScoreLineGz::getYear, 2021)
                .orderByDesc(ScoreLineGz::getYear)
                .orderByAsc(ScoreLineGz::getMinRank);

        return scoreLineGzMapper.selectList(schoolWrapper).stream()
                .map(this::toView)
                .filter(Objects::nonNull)
                .collect(LinkedHashMap<Integer, ScoreLineView>::new,
                        this::putPreferredHistoryView,
                        Map::putAll)
                .values()
                .stream()
                .limit(limit)
                .toList();
    }

    /**
     * 根据schoolId查询大学信息（用于意向地区匹配）
     */
    @Cacheable(value = "universityBySchoolId", key = "#schoolId", unless = "#result == null")
    public University getUniversityById(String schoolId) {
        return universityMapper.selectOne(
                new LambdaQueryWrapper<University>().eq(University::getSchoolId, schoolId).last("LIMIT 1"));
    }

    /**
     * 热门专业TOP N — 基于当前科类最新年份真实录取数据统计
     * heat = 院校数归一化(0-100)，值越高越热门
     */
    @Cacheable(value = "hotMajors", key = "#subjectType + '_' + #limit")
    public List<Map<String, Object>> getHotMajors(String subjectType, int limit) {
        List<Map<String, Object>> raw = majorScoreGzMapper.selectHotMajors(subjectType, limit);
        if (raw == null || raw.isEmpty()) return List.of();

        // 第一条是最多院校开设的，用它做基准算热度
        long maxCount = ((Number) raw.get(0).get("schoolCount")).longValue();

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> row = raw.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", i + 1);
            item.put("name", row.get("name"));
            item.put("schoolCount", ((Number) row.get("schoolCount")).intValue());
            item.put("avgScore", ((Number) row.get("avgScore")).intValue());
            item.put("avgRank", ((Number) row.get("avgRank")).intValue());
            // 热度分: 院校数占最热专业的百分比
            int heat = (int) Math.round(((Number) row.get("schoolCount")).doubleValue() / maxCount * 100);
            item.put("heat", heat);
            result.add(item);
        }
        return result;
    }

    private ScoreLineView toView(MajorScoreGz m) {
        ScoreLineView view = new ScoreLineView();
        view.setId(m.getId());
        view.setProvinceCode(ProvincePolicyService.GZ);
        view.setSchoolId(m.getSchoolId());
        view.setUniversityName(m.getUniversityName());
        view.setMajorName(m.getMajorName());
        view.setMajorId(m.getMajorId());
        view.setYear(m.getYear());
        view.setSubjectType(m.getSubjectType());
        view.setMinScore(m.getMinScore());
        view.setMaxScore(m.getMaxScore());
        view.setAvgScore(m.getAvgScore());
        view.setMinRank(m.getMinRank());
        view.setPlanCount(m.getPlanCount());
        view.setBatch(m.getBatch());
        view.setResubjectRequirement(m.getResubjectRequirement());
        view.setDataSourceType("专业级");
        view.setConfidenceLabel(m.getYear() != null && m.getYear() >= 2024 ? "高可信" : "中可信");
        applyRankSource(view);
        return view;
    }

    private ScoreLineView toView(ScoreLineGz s) {
        ScoreLineView view = new ScoreLineView();
        view.setId(s.getId());
        view.setProvinceCode(ProvincePolicyService.GZ);
        view.setSchoolId(s.getSchoolId());
        view.setUniversityName(s.getUniversityName());
        view.setMajorName(s.getMajorName());
        view.setMajorId(s.getMajorId());
        view.setYear(s.getYear());
        view.setSubjectType(s.getSubjectType());
        view.setMinScore(s.getMinScore());
        view.setMaxScore(s.getMaxScore());
        view.setAvgScore(s.getAvgScore());
        view.setMinRank(s.getMinRank());
        view.setPlanCount(s.getPlanCount());
        view.setBatch(s.getBatch());
        view.setResubjectRequirement(s.getResubjectRequirement());
        view.setDataSourceType("院校级");
        view.setConfidenceLabel(s.getYear() != null && s.getYear() >= 2024 ? "中可信" : "需复核");
        applyRankSource(view);
        return view;
    }

    private ScoreLineView toView(DataAdmissionGroupLine line) {
        ScoreLineView view = new ScoreLineView();
        view.setId(line.getId());
        view.setProvinceCode(line.getProvinceCode());
        view.setSchoolId(line.getSchoolId());
        view.setUniversityName(line.getUniversityName());
        view.setGroupCode(line.getGroupCode());
        view.setGroupName(line.getGroupName());
        view.setMajorName(StringUtils.isNotBlank(line.getGroupName()) ? line.getGroupName() : line.getGroupCode());
        view.setYear(line.getYear());
        view.setSubjectType(line.getSubjectType());
        view.setMinScore(line.getMinScore());
        view.setMinRank(line.getMinRank());
        view.setPlanCount(line.getPlanCount());
        view.setBatch(line.getBatch());
        view.setResubjectRequirement(line.getResubjectRequirement());
        view.setDataSourceType("院校专业组");
        view.setConfidenceLabel(line.getYear() != null && line.getYear() >= 2025 ? "中可信" : "需复核");
        view.setSourceUrl(line.getSourceUrl());
        view.setSourcePageUrl(line.getSourcePageUrl());
        if (line.getMinRank() != null && line.getMinRank() > 0) {
            String rankSourceType = StringUtils.isNotBlank(line.getRankSourceType()) ? line.getRankSourceType() : "original";
            view.setRankSourceType(rankSourceType);
            view.setRankSourceNote(StringUtils.isNotBlank(line.getRankSourceNote())
                    ? line.getRankSourceNote()
                    : "original".equals(rankSourceType)
                    ? "院校专业组原始调档位次，来源于官方/学校官网公开数据。"
                    : "该位次由同年同科类官方一分一段表按最低分换算，需结合来源复核。");
            view.setRankLow(line.getMinRank());
            view.setRankHigh(line.getMinRank());
            view.setRankSourceUrl(StringUtils.isNotBlank(line.getRankSourceUrl()) ? line.getRankSourceUrl() : line.getSourceUrl());
            view.setRankSourcePageUrl(StringUtils.isNotBlank(line.getRankSourcePageUrl()) ? line.getRankSourcePageUrl() : line.getSourcePageUrl());
        } else {
            markMissingRank(view);
        }
        return view;
    }

    private void applyRankSource(ScoreLineView view) {
        if (view == null) {
            return;
        }
        Integer minRank = view.getMinRank();
        if (minRank != null && minRank > 0) {
            view.setRankSourceType("original");
            view.setRankSourceNote("原始录取位次，来源于录取分数线数据。");
            view.setRankLow(minRank);
            view.setRankHigh(minRank);
            return;
        }
        if (view.getYear() == null || view.getMinScore() == null || view.getMinScore() <= 0
                || StringUtils.isBlank(view.getSubjectType())) {
            markMissingRank(view);
            return;
        }
        try {
            ScoreRankGz rankLine = scoreRankGzMapper.selectNearestAtOrBelow(
                    view.getYear(), view.getSubjectType(), view.getMinScore());
            if (rankLine == null) {
                markMissingRank(view);
                return;
            }
            view.setMinRank(rankLine.getRankHigh());
            view.setRankLow(rankLine.getRankLow());
            view.setRankHigh(rankLine.getRankHigh());
            view.setRankSourceType("score_rank_converted");
            view.setRankSourceUrl(rankLine.getSourceUrl());
            view.setRankSourcePageUrl(rankLine.getSourcePageUrl());
            view.setRankSourceNote(String.format(
                    "%d年%s官方一分一段表按最低分%s换算，区间约%d-%d名；该值不是原始投档位次，需结合官方录取线复核。",
                    rankLine.getYear(), rankLine.getSubjectType(), rankLine.getScoreLabel(),
                    rankLine.getRankLow(), rankLine.getRankHigh()));
        } catch (Exception e) {
            markMissingRank(view);
        }
    }

    private void markMissingRank(ScoreLineView view) {
        view.setRankSourceType("missing");
        view.setRankSourceNote("该记录缺少原始录取位次，且未命中同年同科类官方一分一段表，需人工复核。");
    }

    private void putPreferredHistoryView(Map<Integer, ScoreLineView> map, ScoreLineView view) {
        if (view == null || view.getYear() == null) {
            return;
        }
        ScoreLineView existing = map.get(view.getYear());
        if (existing == null || isBetterHistoryView(view, existing)) {
            map.put(view.getYear(), view);
        }
    }

    private boolean isBetterHistoryView(ScoreLineView candidate, ScoreLineView existing) {
        int candidateRankScore = rankSourcePriority(candidate.getRankSourceType());
        int existingRankScore = rankSourcePriority(existing.getRankSourceType());
        if (candidateRankScore != existingRankScore) {
            return candidateRankScore > existingRankScore;
        }
        Integer candidateRank = candidate.getMinRank();
        Integer existingRank = existing.getMinRank();
        if (candidateRank == null || candidateRank <= 0) {
            return false;
        }
        if (existingRank == null || existingRank <= 0) {
            return true;
        }
        return candidateRank < existingRank;
    }

    private int rankSourcePriority(String type) {
        if ("original".equals(type)) {
            return 3;
        }
        if ("score_rank_converted".equals(type)) {
            return 2;
        }
        return 1;
    }

    private Map<String, List<ScoreLineGz>> loadSchoolHistoryMap(List<ScoreLineGz> pageRows, String subjectType) {
        if (pageRows == null || pageRows.isEmpty()) {
            return Map.of();
        }
        Set<String> schoolIds = new HashSet<>();
        for (ScoreLineGz row : pageRows) {
            if (row != null && StringUtils.isNotBlank(row.getSchoolId())) {
                schoolIds.add(row.getSchoolId());
            }
        }
        if (schoolIds.isEmpty()) {
            return Map.of();
        }

        LambdaQueryWrapper<ScoreLineGz> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ScoreLineGz::getId,
                        ScoreLineGz::getSchoolId,
                        ScoreLineGz::getUniversityName,
                        ScoreLineGz::getYear,
                        ScoreLineGz::getSubjectType,
                        ScoreLineGz::getMinScore,
                        ScoreLineGz::getMinRank,
                        ScoreLineGz::getBatch)
                .in(ScoreLineGz::getSchoolId, schoolIds)
                .isNotNull(ScoreLineGz::getMinScore)
                .gt(ScoreLineGz::getMinScore, 0)
                .isNotNull(ScoreLineGz::getMinRank)
                .gt(ScoreLineGz::getMinRank, 0)
                .orderByDesc(ScoreLineGz::getYear)
                .orderByAsc(ScoreLineGz::getMinRank);
        if (StringUtils.isNotBlank(subjectType)) {
            wrapper.eq(ScoreLineGz::getSubjectType, subjectType);
        }
        return scoreLineGzMapper.selectList(wrapper).stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                        ScoreLineGz::getSchoolId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    private SchoolScoreSummary toSchoolSummary(ScoreLineGz line, List<ScoreLineGz> history) {
        SchoolScoreSummary summary = new SchoolScoreSummary();
        summary.setId(line.getId());
        summary.setProvinceCode(ProvincePolicyService.GZ);
        summary.setVolunteerUnitType(ProvincePolicyService.UNIT_MAJOR_96);
        summary.setSchoolId(line.getSchoolId());
        summary.setUniversityName(line.getUniversityName());
        summary.setSubjectType(line.getSubjectType());
        summary.setLatestYear(line.getYear());
        summary.setMinScore(line.getMinScore());
        summary.setMinRank(line.getMinRank());
        summary.setBatch(line.getBatch());
        summary.setDataSourceType("院校级");
        summary.setConfidenceLabel(line.getYear() != null && line.getYear() >= 2024 ? "中可信" : "需复核");

        List<Integer> years = history.stream()
                .map(ScoreLineGz::getYear)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        summary.setAvailableYearCount(years.size());
        summary.setFirstYear(years.isEmpty() ? line.getYear() : years.get(0));
        summary.setLastYear(years.isEmpty() ? line.getYear() : years.get(years.size() - 1));
        return summary;
    }

    private PageResult<ScoreLineView> listProfessionalGroups(String provinceCode, Integer year, String subjectType,
                                                             String universityName, int page, int pageSize) {
        LambdaQueryWrapper<DataAdmissionGroupLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataAdmissionGroupLine::getProvinceCode, provinceCode)
                .isNotNull(DataAdmissionGroupLine::getMinScore)
                .gt(DataAdmissionGroupLine::getMinScore, 0)
                .orderByDesc(DataAdmissionGroupLine::getYear)
                .orderByAsc(DataAdmissionGroupLine::getMinRank);
        if (year != null) {
            wrapper.eq(DataAdmissionGroupLine::getYear, year);
        }
        if (StringUtils.isNotBlank(subjectType)) {
            wrapper.eq(DataAdmissionGroupLine::getSubjectType, subjectType);
        }
        if (StringUtils.isNotBlank(universityName)) {
            wrapper.like(DataAdmissionGroupLine::getUniversityName, universityName.trim());
        }
        Page<DataAdmissionGroupLine> p = dataAdmissionGroupLineMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResult.of(p.getRecords().stream().map(this::toView).toList(), p.getTotal(), page, pageSize);
    }

    private PageResult<SchoolScoreSummary> listProfessionalGroupSummaries(String provinceCode, Integer year,
                                                                          String subjectType, String universityName,
                                                                          int page, int pageSize) {
        LambdaQueryWrapper<DataAdmissionGroupLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataAdmissionGroupLine::getProvinceCode, provinceCode)
                .isNotNull(DataAdmissionGroupLine::getSchoolId)
                .ne(DataAdmissionGroupLine::getSchoolId, "")
                .isNotNull(DataAdmissionGroupLine::getMinScore)
                .gt(DataAdmissionGroupLine::getMinScore, 0)
                .orderByAsc(DataAdmissionGroupLine::getMinRank)
                .orderByDesc(DataAdmissionGroupLine::getMinScore)
                .orderByAsc(DataAdmissionGroupLine::getUniversityName)
                .orderByAsc(DataAdmissionGroupLine::getGroupCode);
        if (year != null) {
            wrapper.eq(DataAdmissionGroupLine::getYear, year);
        }
        if (StringUtils.isNotBlank(subjectType)) {
            wrapper.eq(DataAdmissionGroupLine::getSubjectType, subjectType);
        }
        if (StringUtils.isNotBlank(universityName)) {
            wrapper.like(DataAdmissionGroupLine::getUniversityName, universityName.trim());
        }
        Map<String, DataAdmissionGroupLine> bestByGroup = new LinkedHashMap<>();
        for (DataAdmissionGroupLine line : dataAdmissionGroupLineMapper.selectList(wrapper)) {
            if (line == null || StringUtils.isBlank(line.getSchoolId()) || StringUtils.isBlank(line.getGroupCode())) {
                continue;
            }
            String key = line.getSchoolId() + "_" + line.getGroupCode() + "_" + line.getSubjectType();
            bestByGroup.putIfAbsent(key, line);
        }
        List<DataAdmissionGroupLine> rows = new ArrayList<>(bestByGroup.values());
        int total = rows.size();
        int from = Math.min(Math.max((page - 1) * pageSize, 0), total);
        int to = Math.min(from + pageSize, total);
        List<SchoolScoreSummary> items = rows.subList(from, to).stream()
                .map(this::toProfessionalGroupSummary)
                .toList();
        return PageResult.of(items, total, page, pageSize);
    }

    private List<ScoreLineView> findProfessionalGroupHistory(String provinceCode, String schoolId, String groupCode,
                                                             String subjectType, int maxRecords) {
        if (StringUtils.isBlank(schoolId)) {
            return List.of();
        }
        LambdaQueryWrapper<DataAdmissionGroupLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataAdmissionGroupLine::getProvinceCode, provinceCode)
                .eq(DataAdmissionGroupLine::getSchoolId, schoolId)
                .isNotNull(DataAdmissionGroupLine::getMinScore)
                .gt(DataAdmissionGroupLine::getMinScore, 0)
                .orderByDesc(DataAdmissionGroupLine::getYear)
                .orderByAsc(DataAdmissionGroupLine::getMinRank);
        if (StringUtils.isNotBlank(groupCode)) {
            wrapper.eq(DataAdmissionGroupLine::getGroupCode, groupCode);
        }
        if (StringUtils.isNotBlank(subjectType)) {
            wrapper.eq(DataAdmissionGroupLine::getSubjectType, subjectType);
        }
        int limit = Math.min(Math.max(maxRecords, 1), 20);
        return dataAdmissionGroupLineMapper.selectList(wrapper).stream()
                .map(this::toView)
                .filter(Objects::nonNull)
                .collect(LinkedHashMap<String, ScoreLineView>::new,
                        (map, view) -> {
                            String key = safeKey(view.getYear()) + "_" + safeKey(view.getGroupCode());
                            map.putIfAbsent(key, view);
                        },
                        Map::putAll)
                .values()
                .stream()
                .limit(limit)
                .toList();
    }

    private SchoolScoreSummary toProfessionalGroupSummary(DataAdmissionGroupLine line) {
        SchoolScoreSummary summary = new SchoolScoreSummary();
        summary.setId(line.getId());
        summary.setProvinceCode(line.getProvinceCode());
        summary.setVolunteerUnitType(ProvincePolicyService.UNIT_PROFESSIONAL_GROUP_45);
        summary.setSchoolId(line.getSchoolId());
        summary.setUniversityName(line.getUniversityName());
        summary.setGroupCode(line.getGroupCode());
        summary.setGroupName(line.getGroupName());
        summary.setSubjectType(line.getSubjectType());
        summary.setLatestYear(line.getYear());
        summary.setMinScore(line.getMinScore());
        summary.setMinRank(line.getMinRank());
        summary.setBatch(line.getBatch());
        summary.setAvailableYearCount(1);
        summary.setFirstYear(line.getYear());
        summary.setLastYear(line.getYear());
        summary.setDataSourceType("院校专业组");
        summary.setConfidenceLabel(line.getYear() != null && line.getYear() >= 2025 ? "中可信" : "需复核");
        return summary;
    }

    private String safeKey(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String majorLookupCore(String majorName) {
        String text = majorName == null ? "" : majorName.trim();
        if (text.isEmpty()) {
            return "";
        }
        text = text.replace('(', '（').replace(')', '）');
        int bracket = text.indexOf('（');
        if (bracket > 0) {
            text = text.substring(0, bracket);
        }
        int note = text.indexOf('[');
        if (note > 0) {
            text = text.substring(0, note);
        }
        return text.replaceAll("\\s+", "");
    }
}
