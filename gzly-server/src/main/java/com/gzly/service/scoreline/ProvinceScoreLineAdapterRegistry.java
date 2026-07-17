package com.gzly.service.scoreline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.PageResult;
import com.gzly.entity.BatchLineGz;
import com.gzly.entity.DataAdmissionGroupLine;
import com.gzly.entity.DataScoreRank;
import com.gzly.entity.MajorScoreGz;
import com.gzly.entity.ScoreRankGz;
import com.gzly.entity.ScoreLineGz;
import com.gzly.mapper.BatchLineGzMapper;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.MajorScoreGzMapper;
import com.gzly.mapper.ScoreLineGzMapper;
import com.gzly.mapper.ScoreRankGzMapper;
import com.gzly.service.ProvincePolicyService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.gzly.service.scoreline.ScoreLineModels.TYPE_ADMISSION_LINE;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_ART_SPORT;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_CONTROL_LINE;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_MAJOR_GROUP_LINE;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_MAJOR_SCORE;
import static com.gzly.service.scoreline.ScoreLineModels.TYPE_SCORE_RANK;

@Service
public class ProvinceScoreLineAdapterRegistry {

    private final ProvincePolicyService provincePolicyService;
    private final Map<String, ProvinceScoreLineAdapter> adapters;

    public ProvinceScoreLineAdapterRegistry(ProvincePolicyService provincePolicyService,
                                            ScoreLineGzMapper scoreLineGzMapper,
                                            MajorScoreGzMapper majorScoreGzMapper,
                                            ScoreRankGzMapper scoreRankGzMapper,
                                            DataAdmissionGroupLineMapper groupLineMapper,
                                            DataScoreRankMapper dataScoreRankMapper,
                                            BatchLineGzMapper batchLineGzMapper) {
        this.provincePolicyService = provincePolicyService;
        Map<String, ProvinceScoreLineAdapter> map = new LinkedHashMap<>();
        put(map, new GuizhouAdapter(provincePolicyService, scoreLineGzMapper, majorScoreGzMapper, scoreRankGzMapper, batchLineGzMapper));
        put(map, new ProfessionalGroupAdapter(provincePolicyService, groupLineMapper, dataScoreRankMapper,
                "SC", "四川", "四川省教育考试院", "https://www.sceea.cn/", "FIRST_YEAR_312",
                "2025 首年新高考，旧文理数据只作历史参考，不进入新高考主查询。"));
        put(map, new ProfessionalGroupAdapter(provincePolicyService, groupLineMapper, dataScoreRankMapper,
                "AH", "安徽", "安徽省教育招生考试院", "https://www.ahzsks.cn/", "PROFESSIONAL_GROUP_312",
                "安徽按 3+1+2 院校专业组和物理/历史口径查询，不跨省回退。"));
        put(map, new ProfessionalGroupAdapter(provincePolicyService, groupLineMapper, dataScoreRankMapper,
                "HB", "湖北", "湖北省教育考试院", "https://www.hbea.edu.cn/", "PROFESSIONAL_GROUP_312",
                "湖北 A00306 清华大学历史类 674 分位次仍需人工官方口径确认；查询时不伪造最低位次。"));
        put(map, new ProfessionalGroupAdapter(provincePolicyService, groupLineMapper, dataScoreRankMapper,
                "GX", "广西", "广西壮族自治区招生考试院", "https://www.gxeea.cn/", "PROFESSIONAL_GROUP_312",
                "广西按 3+1+2 院校专业组和物理/历史口径查询，2026 官方数据待发布。"));
        put(map, new HainanAdapter(provincePolicyService, groupLineMapper, dataScoreRankMapper));
        put(map, new ProfessionalGroupAdapter(provincePolicyService, groupLineMapper, dataScoreRankMapper,
                "YN", "云南", "云南省招生考试院", "https://www.ynzs.cn/", "FIRST_YEAR_312",
                "云南 2025 首年新高考，2024 旧文理数据只能弱参考，不进入主查询逻辑。"));
        put(map, new ProfessionalGroupAdapter(provincePolicyService, groupLineMapper, dataScoreRankMapper,
                "HA", "河南", "河南省教育考试院", "https://www.haeea.cn/", "FIRST_YEAR_312",
                "河南 2025 首年新高考，大省同分密度和位次波动风险较高，旧文理数据不直接映射。"));
        put(map, new LevelOneMissingAdapter(provincePolicyService,
                "CQ", "重庆", "重庆市教育考试院", "https://www.cqksy.cn/",
                "重庆当前地区正在接入历史数据，暂不开放完整志愿表生成，可先使用 AI 志愿问答和方向参考。"));
        put(map, new LevelOneMissingAdapter(provincePolicyService,
                "GS", "甘肃", "甘肃省教育考试院", "https://www.ganseea.cn/",
                "甘肃当前地区正在接入历史数据，暂不开放完整志愿表生成，可先使用 AI 志愿问答和方向参考。"));
        put(map, new LevelOneMissingAdapter(provincePolicyService,
                "XJ", "新疆", "新疆维吾尔自治区教育考试院", "https://www.xjzk.gov.cn/",
                "新疆当前地区正在接入历史数据，暂不开放完整志愿表生成，可先使用 AI 志愿问答和方向参考。"));
        this.adapters = Map.copyOf(map);
    }

    public ProvinceScoreLineAdapter getAdapter(String provinceCode) {
        String normalized = provincePolicyService.normalizeProvinceCode(provinceCode);
        return Objects.requireNonNull(adapters.get(normalized), "unsupported province");
    }

    private void put(Map<String, ProvinceScoreLineAdapter> map, ProvinceScoreLineAdapter adapter) {
        map.put(adapter.provinceCode(), adapter);
    }

    private abstract static class BaseAdapter implements ProvinceScoreLineAdapter {
        protected final ProvincePolicyService provincePolicyService;
        protected final String provinceCode;
        protected final String provinceName;
        protected final String officialSourceName;
        protected final String officialSourceUrl;
        protected final String policyMode;
        protected final String subjectMode;
        protected final String notice;
        protected final List<String> subjectOptions;
        protected final List<String> selectedSubjectOptions;

        BaseAdapter(ProvincePolicyService provincePolicyService,
                    String provinceCode,
                    String provinceName,
                    String officialSourceName,
                    String officialSourceUrl,
                    String policyMode,
                    String subjectMode,
                    List<String> subjectOptions,
                    List<String> selectedSubjectOptions,
                    String notice) {
            this.provincePolicyService = provincePolicyService;
            this.provinceCode = provinceCode;
            this.provinceName = provinceName;
            this.officialSourceName = officialSourceName;
            this.officialSourceUrl = officialSourceUrl;
            this.policyMode = policyMode;
            this.subjectMode = subjectMode;
            this.subjectOptions = List.copyOf(subjectOptions);
            this.selectedSubjectOptions = List.copyOf(selectedSubjectOptions);
            this.notice = notice;
        }

        @Override
        public String provinceCode() {
            return provinceCode;
        }

        @Override
        public ScoreLineModels.Capability capability() {
            List<Integer> years = listAvailableYears();
            ScoreLineModels.Capability capability = new ScoreLineModels.Capability();
            capability.setProvinceCode(provinceCode);
            capability.setProvinceName(provinceName);
            capability.setOfficialSourceName(officialSourceName);
            capability.setOfficialSourceUrl(officialSourceUrl);
            capability.setPolicyMode(policyMode);
            capability.setSubjectMode(subjectMode);
            capability.setTargetYear(2026);
            capability.setLatestOfficialDataYear(years.stream().max(Integer::compareTo).orElse(2025));
            capability.setAvailableYears(years);
            capability.setSubjectOptions(subjectOptions);
            capability.setSelectedSubjectOptions(selectedSubjectOptions);
            capability.setDataStatus("PRE_OFFICIAL_DATA");
            capability.setNotices(buildNotices());
            capability.setMissingReasonByType(buildMissingReasonMap());
            capability.setScoreLineTypes(List.of(
                    typeCapability(TYPE_CONTROL_LINE, "省控线", "批次控制线 / 特殊类型控制线", hasControlLine(), controlLineSourceTables()),
                    typeCapability(TYPE_SCORE_RANK, "一分一段", "按本省官方一分一段口径展示分数、同分人数和累计位次", hasScoreRank(), scoreRankSourceTables()),
                    typeCapability(TYPE_ADMISSION_LINE, "院校投档线", "院校级或院校专业组投档线，按本省志愿单位展示", hasAdmissionLine(), admissionSourceTables()),
                    typeCapability(TYPE_MAJOR_GROUP_LINE, "专业组投档线", "院校专业组最低分和最低位次", hasMajorGroupLine(), admissionSourceTables()),
                    typeCapability(TYPE_MAJOR_SCORE, "专业录取最低分", "专业或专业类录取最低分", hasMajorScoreLine(), majorScoreSourceTables()),
                    typeCapability(TYPE_ART_SPORT, "艺体综合分", "艺术、体育、专项等非普通批次分数线", hasArtSportLine(), List.of())
            ));
            return capability;
        }

        protected List<String> buildNotices() {
            List<String> notices = new ArrayList<>();
            notices.add("2026 官方招生计划、投档线和一分一段未发布，当前保持 PRE_OFFICIAL_DATA。");
            notices.add("查询只读取已导入且可核验的历史数据，不伪造 2026 数据。");
            notices.add(notice);
            return notices;
        }

        protected Map<String, String> buildMissingReasonMap() {
            Map<String, String> reasons = new LinkedHashMap<>();
            for (String type : List.of(TYPE_CONTROL_LINE, TYPE_SCORE_RANK, TYPE_ADMISSION_LINE,
                    TYPE_MAJOR_GROUP_LINE, TYPE_MAJOR_SCORE, TYPE_ART_SPORT)) {
                reasons.put(type, getMissingDataReason(type));
            }
            return reasons;
        }

        protected ScoreLineModels.TypeCapability typeCapability(String type, String label, String description,
                                                                boolean queryable, List<String> sourceTables) {
            ScoreLineModels.TypeCapability capability = new ScoreLineModels.TypeCapability();
            capability.setType(type);
            capability.setLabel(label);
            capability.setDescription(description);
            capability.setQueryable(queryable);
            capability.setDataStatus(queryable ? "AVAILABLE" : "MISSING");
            capability.setMissingReason(queryable ? "" : getMissingDataReason(type));
            capability.setAvailableYears(queryable ? listAvailableYears() : List.of());
            capability.setSourceTables(sourceTables);
            return capability;
        }

        protected ScoreLineModels.QueryResult emptyResult(ScoreLineModels.Query query, String type, String reason) {
            ScoreLineModels.QueryResult result = baseResult(query, type);
            result.setDataStatus("MISSING");
            result.setMissingReason(reason);
            result.setPageResult(PageResult.of(List.of(), 0, safePage(query), safePageSize(query)));
            return result;
        }

        protected ScoreLineModels.QueryResult baseResult(ScoreLineModels.Query query, String type) {
            ScoreLineModels.QueryResult result = new ScoreLineModels.QueryResult();
            result.setProvinceCode(provinceCode);
            result.setProvinceName(provinceName);
            result.setYear(query.getYear());
            result.setScoreLineType(type);
            result.setSubjectCategory(normalizeSubject(query));
            return result;
        }

        protected int safePage(ScoreLineModels.Query query) {
            return Math.max(query.getPage(), 1);
        }

        protected int safePageSize(ScoreLineModels.Query query) {
            return Math.min(Math.max(query.getPageSize(), 1), 100);
        }

        protected String normalizeSubject(ScoreLineModels.Query query) {
            String subject = firstNotBlank(query.getSubjectCategory(), query.getSubjectType());
            if (StringUtils.isBlank(subject) && !subjectOptions.isEmpty()) {
                return subjectOptions.get(0);
            }
            return subject;
        }

        protected String firstNotBlank(String first, String second) {
            return StringUtils.isNotBlank(first) ? first.trim() : (StringUtils.isNotBlank(second) ? second.trim() : "");
        }

        protected List<Integer> yearsFromAdmissionRows(List<DataAdmissionGroupLine> rows) {
            return rows.stream()
                    .map(DataAdmissionGroupLine::getYear)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }

        protected List<Integer> mergeYears(List<Integer> first, List<Integer> second) {
            List<Integer> merged = new ArrayList<>();
            merged.addAll(first);
            merged.addAll(second);
            return merged.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }

        protected abstract List<Integer> listAvailableYears();

        protected boolean hasControlLine() {
            return false;
        }

        protected boolean hasScoreRank() {
            return false;
        }

        protected boolean hasAdmissionLine() {
            return false;
        }

        protected boolean hasMajorGroupLine() {
            return false;
        }

        protected boolean hasMajorScoreLine() {
            return false;
        }

        protected boolean hasArtSportLine() {
            return false;
        }

        protected List<String> controlLineSourceTables() {
            return List.of();
        }

        protected List<String> scoreRankSourceTables() {
            return List.of();
        }

        protected List<String> admissionSourceTables() {
            return List.of();
        }

        protected List<String> majorScoreSourceTables() {
            return List.of();
        }

        @Override
        public ScoreLineModels.QueryResult queryControlLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_CONTROL_LINE, getMissingDataReason(TYPE_CONTROL_LINE));
        }

        @Override
        public ScoreLineModels.QueryResult queryMajorScoreLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_MAJOR_SCORE, getMissingDataReason(TYPE_MAJOR_SCORE));
        }

        @Override
        public ScoreLineModels.QueryResult queryArtSportLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_ART_SPORT, getMissingDataReason(TYPE_ART_SPORT));
        }
    }

    private static class GuizhouAdapter extends BaseAdapter {
        private final ScoreLineGzMapper scoreLineGzMapper;
        private final MajorScoreGzMapper majorScoreGzMapper;
        private final ScoreRankGzMapper scoreRankGzMapper;
        private final BatchLineGzMapper batchLineGzMapper;

        GuizhouAdapter(ProvincePolicyService provincePolicyService,
                       ScoreLineGzMapper scoreLineGzMapper,
                       MajorScoreGzMapper majorScoreGzMapper,
                       ScoreRankGzMapper scoreRankGzMapper,
                       BatchLineGzMapper batchLineGzMapper) {
            super(provincePolicyService, "GZ", "贵州", "贵州省招生考试院",
                    "https://zsksy.guizhou.gov.cn/", "MAJOR_96", "FIRST_CHOICE_312",
                    List.of("物理类", "历史类"), List.of(),
                    "贵州按“专业（类）+院校”口径展示，院校专业组查询不适用。");
            this.scoreLineGzMapper = scoreLineGzMapper;
            this.majorScoreGzMapper = majorScoreGzMapper;
            this.scoreRankGzMapper = scoreRankGzMapper;
            this.batchLineGzMapper = batchLineGzMapper;
        }

        @Override
        protected List<Integer> listAvailableYears() {
            List<Integer> years = new ArrayList<>();
            years.addAll(scoreLineGzMapper.selectDistinctYears());
            years.addAll(majorScoreGzMapper.selectDistinctYears());
            years.addAll(scoreRankGzMapper.selectList(new LambdaQueryWrapper<ScoreRankGz>()
                            .select(ScoreRankGz::getYear))
                    .stream().map(ScoreRankGz::getYear).toList());
            return years.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }

        @Override
        protected boolean hasScoreRank() {
            return scoreRankGzMapper.selectCount(null) > 0;
        }

        @Override
        protected boolean hasAdmissionLine() {
            return scoreLineGzMapper.selectCount(null) > 0;
        }

        @Override
        protected boolean hasMajorScoreLine() {
            return majorScoreGzMapper.selectCount(null) > 0;
        }

        @Override
        protected List<String> scoreRankSourceTables() {
            return List.of("data_score_rank_gz");
        }

        @Override
        protected List<String> admissionSourceTables() {
            return List.of("data_score_line_gz");
        }

        @Override
        protected List<String> majorScoreSourceTables() {
            return List.of("data_major_score_gz");
        }

        @Override
        protected boolean hasControlLine() {
            return batchLineGzMapper.selectCount(null) > 0;
        }

        @Override
        protected List<String> controlLineSourceTables() {
            return List.of("data_batch_line_gz");
        }

        @Override
        public ScoreLineModels.QueryResult queryControlLine(ScoreLineModels.Query query) {
            String subject = normalizeSubject(query);
            LambdaQueryWrapper<BatchLineGz> wrapper = new LambdaQueryWrapper<>();
            if (query.getYear() != null) {
                wrapper.eq(BatchLineGz::getYear, query.getYear());
            }
            if (StringUtils.isNotBlank(subject)) {
                wrapper.eq(BatchLineGz::getSubjectType, subject);
            }
            wrapper.orderByDesc(BatchLineGz::getYear).orderByDesc(BatchLineGz::getControlScore);
            Page<BatchLineGz> page = batchLineGzMapper.selectPage(new Page<>(safePage(query), safePageSize(query)), wrapper);
            List<ScoreLineModels.Record> items = page.getRecords().stream().map(this::toRecord).toList();
            return pagedResult(query, TYPE_CONTROL_LINE, items, page.getTotal(), subject);
        }

        @Override
        public ScoreLineModels.QueryResult queryScoreRank(ScoreLineModels.Query query) {
            String subject = normalizeSubject(query);
            LambdaQueryWrapper<ScoreRankGz> wrapper = new LambdaQueryWrapper<>();
            if (query.getYear() != null) {
                wrapper.eq(ScoreRankGz::getYear, query.getYear());
            }
            if (StringUtils.isNotBlank(subject)) {
                wrapper.eq(ScoreRankGz::getSubjectType, subject);
            }
            wrapper.orderByDesc(ScoreRankGz::getYear).orderByDesc(ScoreRankGz::getScore);
            Page<ScoreRankGz> page = scoreRankGzMapper.selectPage(new Page<>(safePage(query), safePageSize(query)), wrapper);
            List<ScoreLineModels.Record> items = page.getRecords().stream().map(this::toRecord).toList();
            return pagedResult(query, TYPE_SCORE_RANK, items, page.getTotal(), subject);
        }

        @Override
        public ScoreLineModels.QueryResult queryAdmissionLine(ScoreLineModels.Query query) {
            String subject = normalizeSubject(query);
            LambdaQueryWrapper<ScoreLineGz> wrapper = new LambdaQueryWrapper<>();
            if (query.getYear() != null) {
                wrapper.eq(ScoreLineGz::getYear, query.getYear());
            }
            if (StringUtils.isNotBlank(subject)) {
                wrapper.eq(ScoreLineGz::getSubjectType, subject);
            }
            if (StringUtils.isNotBlank(query.getSchoolName())) {
                wrapper.like(ScoreLineGz::getUniversityName, query.getSchoolName().trim());
            }
            if (StringUtils.isNotBlank(query.getSchoolCode())) {
                wrapper.eq(ScoreLineGz::getSchoolId, query.getSchoolCode().trim());
            }
            wrapper.orderByDesc(ScoreLineGz::getYear).orderByAsc(ScoreLineGz::getMinRank);
            Page<ScoreLineGz> page = scoreLineGzMapper.selectPage(new Page<>(safePage(query), safePageSize(query)), wrapper);
            List<ScoreLineModels.Record> items = page.getRecords().stream().map(this::toRecord).toList();
            return pagedResult(query, TYPE_ADMISSION_LINE, items, page.getTotal(), subject);
        }

        @Override
        public ScoreLineModels.QueryResult queryMajorGroupLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_MAJOR_GROUP_LINE, getMissingDataReason(TYPE_MAJOR_GROUP_LINE));
        }

        @Override
        public ScoreLineModels.QueryResult queryMajorScoreLine(ScoreLineModels.Query query) {
            String subject = normalizeSubject(query);
            LambdaQueryWrapper<MajorScoreGz> wrapper = new LambdaQueryWrapper<>();
            if (query.getYear() != null) {
                wrapper.eq(MajorScoreGz::getYear, query.getYear());
            }
            if (StringUtils.isNotBlank(subject)) {
                wrapper.eq(MajorScoreGz::getSubjectType, subject);
            }
            if (StringUtils.isNotBlank(query.getSchoolName())) {
                wrapper.like(MajorScoreGz::getUniversityName, query.getSchoolName().trim());
            }
            if (StringUtils.isNotBlank(query.getSchoolCode())) {
                wrapper.eq(MajorScoreGz::getSchoolId, query.getSchoolCode().trim());
            }
            if (StringUtils.isNotBlank(query.getMajorName())) {
                wrapper.like(MajorScoreGz::getMajorName, query.getMajorName().trim());
            }
            wrapper.orderByDesc(MajorScoreGz::getYear).orderByAsc(MajorScoreGz::getMinRank);
            Page<MajorScoreGz> page = majorScoreGzMapper.selectPage(new Page<>(safePage(query), safePageSize(query)), wrapper);
            List<ScoreLineModels.Record> items = page.getRecords().stream().map(this::toRecord).toList();
            return pagedResult(query, TYPE_MAJOR_SCORE, items, page.getTotal(), subject);
        }

        @Override
        public String getMissingDataReason(String scoreLineType) {
            if (TYPE_CONTROL_LINE.equals(scoreLineType)) {
                return "贵州该年份/科类暂无已入库的省控线记录（目前已收录普通类 2025 本科线/特控线/专科线）。";
            }
            if (TYPE_MAJOR_GROUP_LINE.equals(scoreLineType)) {
                return "贵州普通类按“专业（类）+院校”投档，不使用院校专业组作为主查询单位。";
            }
            if (TYPE_ART_SPORT.equals(scoreLineType)) {
                return "艺术、体育、专项分数线需按综合分和资格规则单独建模，不能套普通位次模型。";
            }
            return "当前类型暂无可核验结构化数据。";
        }

        private ScoreLineModels.QueryResult pagedResult(ScoreLineModels.Query query, String type,
                                                        List<ScoreLineModels.Record> items, long total, String subject) {
            ScoreLineModels.QueryResult result = baseResult(query, type);
            result.setSubjectCategory(subject);
            result.setDataStatus(total > 0 ? "AVAILABLE" : "MISSING");
            result.setMissingReason(total > 0 ? "" : getMissingDataReason(type));
            result.setPageResult(PageResult.of(items, total, safePage(query), safePageSize(query)));
            return result;
        }

        private ScoreLineModels.Record toRecord(BatchLineGz line) {
            ScoreLineModels.Record record = new ScoreLineModels.Record();
            record.setId(line.getId());
            record.setProvinceCode("GZ");
            record.setProvinceName("贵州");
            record.setYear(line.getYear());
            record.setScoreLineType(TYPE_CONTROL_LINE);
            record.setSubjectCategory(line.getSubjectType());
            record.setBatchCode(line.getBatchCode());
            record.setBatchName(line.getBatchName());
            record.setMinScore(line.getControlScore());
            record.setScore(line.getControlScore());
            record.setSourceUrl(line.getSourceUrl());
            record.setSourcePage(line.getSourcePageUrl());
            record.setSourceFile(line.getSourceFile());
            record.setDataStatus("AVAILABLE");
            return record;
        }

        private ScoreLineModels.Record toRecord(ScoreRankGz rank) {
            ScoreLineModels.Record record = new ScoreLineModels.Record();
            record.setId(rank.getId());
            record.setProvinceCode("GZ");
            record.setProvinceName("贵州");
            record.setYear(rank.getYear());
            record.setScoreLineType(TYPE_SCORE_RANK);
            record.setSubjectCategory(rank.getSubjectType());
            record.setScore(rank.getScore());
            record.setSameScoreCount(rank.getSegmentCount());
            record.setCumulativeCount(rank.getCumulativeCount());
            record.setRankLow(rank.getRankLow());
            record.setRankHigh(rank.getRankHigh());
            record.setSourceFile(rank.getSourceFile());
            record.setSourceUrl(rank.getSourceUrl());
            record.setSourcePage(rank.getSourcePageUrl());
            record.setDataStatus("AVAILABLE");
            return record;
        }

        private ScoreLineModels.Record toRecord(ScoreLineGz line) {
            ScoreLineModels.Record record = new ScoreLineModels.Record();
            record.setId(line.getId());
            record.setProvinceCode("GZ");
            record.setProvinceName("贵州");
            record.setYear(line.getYear());
            record.setScoreLineType(TYPE_ADMISSION_LINE);
            record.setSubjectCategory(line.getSubjectType());
            record.setSchoolCode(line.getSchoolId());
            record.setSchoolName(line.getUniversityName());
            record.setMajorName(line.getMajorName());
            record.setBatchName(line.getBatch());
            record.setMinScore(line.getMinScore());
            record.setMinRank(line.getMinRank());
            record.setRequiredSubjects(line.getResubjectRequirement());
            record.setDataStatus("AVAILABLE");
            return record;
        }

        private ScoreLineModels.Record toRecord(MajorScoreGz line) {
            ScoreLineModels.Record record = new ScoreLineModels.Record();
            record.setId(line.getId());
            record.setProvinceCode("GZ");
            record.setProvinceName("贵州");
            record.setYear(line.getYear());
            record.setScoreLineType(TYPE_MAJOR_SCORE);
            record.setSubjectCategory(line.getSubjectType());
            record.setSchoolCode(line.getSchoolId());
            record.setSchoolName(line.getUniversityName());
            record.setMajorName(line.getMajorName());
            record.setBatchName(line.getBatch());
            record.setMinScore(line.getMinScore());
            record.setMinRank(line.getMinRank());
            record.setPlanCount(line.getPlanCount());
            record.setRequiredSubjects(line.getResubjectRequirement());
            record.setDataStatus("AVAILABLE");
            return record;
        }
    }

    private static class ProfessionalGroupAdapter extends BaseAdapter {
        protected final DataAdmissionGroupLineMapper groupLineMapper;
        protected final DataScoreRankMapper scoreRankMapper;

        ProfessionalGroupAdapter(ProvincePolicyService provincePolicyService,
                                 DataAdmissionGroupLineMapper groupLineMapper,
                                 DataScoreRankMapper scoreRankMapper,
                                 String provinceCode,
                                 String provinceName,
                                 String officialSourceName,
                                 String officialSourceUrl,
                                 String policyMode,
                                 String notice) {
            super(provincePolicyService, provinceCode, provinceName, officialSourceName, officialSourceUrl,
                    policyMode, "FIRST_CHOICE_312", List.of("物理类", "历史类"), List.of(), notice);
            this.groupLineMapper = groupLineMapper;
            this.scoreRankMapper = scoreRankMapper;
        }

        @Override
        protected List<Integer> listAvailableYears() {
            List<Integer> groupYears = groupLineMapper.selectList(new LambdaQueryWrapper<DataAdmissionGroupLine>()
                            .select(DataAdmissionGroupLine::getYear)
                            .eq(DataAdmissionGroupLine::getProvinceCode, provinceCode))
                    .stream().map(DataAdmissionGroupLine::getYear).toList();
            List<Integer> rankYears = scoreRankMapper.selectList(new LambdaQueryWrapper<DataScoreRank>()
                            .select(DataScoreRank::getYear)
                            .eq(DataScoreRank::getProvinceCode, provinceCode))
                    .stream().map(DataScoreRank::getYear).toList();
            return mergeYears(groupYears, rankYears);
        }

        @Override
        protected boolean hasScoreRank() {
            return scoreRankMapper.selectCount(new LambdaQueryWrapper<DataScoreRank>()
                    .eq(DataScoreRank::getProvinceCode, provinceCode)) > 0;
        }

        @Override
        protected boolean hasAdmissionLine() {
            return groupLineMapper.selectCount(new LambdaQueryWrapper<DataAdmissionGroupLine>()
                    .eq(DataAdmissionGroupLine::getProvinceCode, provinceCode)) > 0;
        }

        @Override
        protected boolean hasMajorGroupLine() {
            return hasAdmissionLine();
        }

        @Override
        protected List<String> scoreRankSourceTables() {
            return List.of("data_score_rank");
        }

        @Override
        protected List<String> admissionSourceTables() {
            return List.of("data_admission_group_line");
        }

        @Override
        public ScoreLineModels.QueryResult queryScoreRank(ScoreLineModels.Query query) {
            String subject = normalizeSubject(query);
            LambdaQueryWrapper<DataScoreRank> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DataScoreRank::getProvinceCode, provinceCode);
            if (query.getYear() != null) {
                wrapper.eq(DataScoreRank::getYear, query.getYear());
            }
            if (StringUtils.isNotBlank(subject)) {
                wrapper.eq(DataScoreRank::getSubjectType, subject);
            }
            wrapper.orderByDesc(DataScoreRank::getYear).orderByDesc(DataScoreRank::getScore);
            Page<DataScoreRank> page = scoreRankMapper.selectPage(new Page<>(safePage(query), safePageSize(query)), wrapper);
            List<ScoreLineModels.Record> items = page.getRecords().stream().map(this::toRecord).toList();
            return pagedResult(query, TYPE_SCORE_RANK, items, page.getTotal(), subject);
        }

        @Override
        public ScoreLineModels.QueryResult queryAdmissionLine(ScoreLineModels.Query query) {
            return queryGroupLines(query, TYPE_ADMISSION_LINE);
        }

        @Override
        public ScoreLineModels.QueryResult queryMajorGroupLine(ScoreLineModels.Query query) {
            return queryGroupLines(query, TYPE_MAJOR_GROUP_LINE);
        }

        @Override
        public String getMissingDataReason(String scoreLineType) {
            if (TYPE_CONTROL_LINE.equals(scoreLineType)) {
                return provinceName + "省控线尚未进入结构化 reviewed 数据，需服务器侧补官方源文件后开放。";
            }
            if (TYPE_SCORE_RANK.equals(scoreLineType)) {
                return provinceName + "一分一段表尚无可核验结构化记录或未完成 reviewed。";
            }
            if (TYPE_ADMISSION_LINE.equals(scoreLineType) || TYPE_MAJOR_GROUP_LINE.equals(scoreLineType)) {
                return provinceName + "院校专业组投档线尚无可核验结构化记录，或生产已有数据需按确认策略处理。";
            }
            if (TYPE_MAJOR_SCORE.equals(scoreLineType)) {
                return provinceName + "专业录取最低分尚无统一结构化表，不能从院校专业组线推断。";
            }
            if (TYPE_ART_SPORT.equals(scoreLineType)) {
                return provinceName + "艺术、体育、专项需按综合分/资格规则单独建模，当前只保留查询入口和缺口说明。";
            }
            return provinceName + "当前类型暂无可核验数据。";
        }

        protected ScoreLineModels.QueryResult queryGroupLines(ScoreLineModels.Query query, String type) {
            String subject = normalizeSubject(query);
            LambdaQueryWrapper<DataAdmissionGroupLine> wrapper = groupLineWrapper(query, subject);
            Page<DataAdmissionGroupLine> page = groupLineMapper.selectPage(new Page<>(safePage(query), safePageSize(query)), wrapper);
            List<ScoreLineModels.Record> items = page.getRecords().stream()
                    .map(line -> toRecord(line, type))
                    .toList();
            return pagedResult(query, type, items, page.getTotal(), subject);
        }

        protected LambdaQueryWrapper<DataAdmissionGroupLine> groupLineWrapper(ScoreLineModels.Query query, String subject) {
            LambdaQueryWrapper<DataAdmissionGroupLine> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DataAdmissionGroupLine::getProvinceCode, provinceCode);
            if (query.getYear() != null) {
                wrapper.eq(DataAdmissionGroupLine::getYear, query.getYear());
            }
            if (StringUtils.isNotBlank(subject)) {
                wrapper.eq(DataAdmissionGroupLine::getSubjectType, subject);
            }
            if (StringUtils.isNotBlank(query.getSchoolName())) {
                wrapper.like(DataAdmissionGroupLine::getUniversityName, query.getSchoolName().trim());
            }
            if (StringUtils.isNotBlank(query.getSchoolCode())) {
                wrapper.eq(DataAdmissionGroupLine::getSchoolId, query.getSchoolCode().trim());
            }
            if (StringUtils.isNotBlank(query.getMajorGroupCode())) {
                wrapper.eq(DataAdmissionGroupLine::getGroupCode, query.getMajorGroupCode().trim());
            }
            wrapper.orderByDesc(DataAdmissionGroupLine::getYear)
                    .orderByAsc(DataAdmissionGroupLine::getMinRank)
                    .orderByAsc(DataAdmissionGroupLine::getUniversityName)
                    .orderByAsc(DataAdmissionGroupLine::getGroupCode);
            return wrapper;
        }

        protected ScoreLineModels.QueryResult pagedResult(ScoreLineModels.Query query, String type,
                                                          List<ScoreLineModels.Record> items, long total, String subject) {
            ScoreLineModels.QueryResult result = baseResult(query, type);
            result.setSubjectCategory(subject);
            result.setDataStatus(total > 0 ? "AVAILABLE" : "MISSING");
            result.setMissingReason(total > 0 ? "" : getMissingDataReason(type));
            result.setPageResult(PageResult.of(items, total, safePage(query), safePageSize(query)));
            return result;
        }

        protected ScoreLineModels.Record toRecord(DataScoreRank rank) {
            ScoreLineModels.Record record = new ScoreLineModels.Record();
            record.setId(rank.getId());
            record.setProvinceCode(rank.getProvinceCode());
            record.setProvinceName(rank.getProvinceName());
            record.setYear(rank.getYear());
            record.setScoreLineType(TYPE_SCORE_RANK);
            record.setSubjectCategory(rank.getSubjectType());
            record.setScore(rank.getScore());
            record.setSameScoreCount(rank.getSegmentCount());
            record.setCumulativeCount(rank.getCumulativeCount());
            record.setRankLow(rank.getRankLow());
            record.setRankHigh(rank.getRankHigh());
            record.setSourceFile(rank.getSourceFile());
            record.setSourceUrl(rank.getSourceUrl());
            record.setSourcePage(rank.getSourcePageUrl());
            record.setDataStatus("AVAILABLE");
            return record;
        }

        protected ScoreLineModels.Record toRecord(DataAdmissionGroupLine line, String type) {
            ScoreLineModels.Record record = new ScoreLineModels.Record();
            record.setId(line.getId());
            record.setProvinceCode(line.getProvinceCode());
            record.setProvinceName(line.getProvinceName());
            record.setYear(line.getYear());
            record.setScoreLineType(type);
            record.setSubjectCategory(line.getSubjectType());
            record.setSchoolCode(line.getSchoolId());
            record.setSchoolName(line.getUniversityName());
            record.setMajorGroupCode(line.getGroupCode());
            record.setMajorGroupName(line.getGroupName());
            record.setMajorName(StringUtils.isNotBlank(line.getGroupName()) ? line.getGroupName() : line.getGroupCode());
            record.setBatchName(line.getBatch());
            record.setMinScore(line.getMinScore());
            record.setMinRank(line.getMinRank());
            record.setPlanCount(line.getPlanCount());
            record.setRequiredSubjects(firstNotBlank(line.getFirstSubjectRequirement(), line.getResubjectRequirement()));
            record.setSourceUrl(line.getSourceUrl());
            record.setSourcePage(line.getSourcePageUrl());
            record.setDataStatus("AVAILABLE");
            return record;
        }
    }

    private static class HainanAdapter extends ProfessionalGroupAdapter {
        HainanAdapter(ProvincePolicyService provincePolicyService,
                      DataAdmissionGroupLineMapper groupLineMapper,
                      DataScoreRankMapper scoreRankMapper) {
            super(provincePolicyService, groupLineMapper, scoreRankMapper, "HI", "海南",
                    "海南省考试局", "https://ea.hainan.gov.cn/", "PROFESSIONAL_GROUP_33",
                    "海南是 3+3 选科，不分物理类/历史类；查询应按 selectedSubjects 与 requiredSubjects 匹配。");
        }

        @Override
        protected List<String> buildNotices() {
            List<String> notices = super.buildNotices();
            List<String> copy = new ArrayList<>(notices);
            copy.add("海南页面不得显示物理/历史核心筛选；如传入物理类或历史类，接口返回空结果和明确原因。");
            return copy;
        }

        @Override
        public ScoreLineModels.Capability capability() {
            ScoreLineModels.Capability capability = super.capability();
            capability.setSubjectMode("SELECTED_SUBJECTS_3_3");
            capability.setSubjectOptions(List.of());
            capability.setSelectedSubjectOptions(List.of("物理", "化学", "生物", "思想政治", "历史", "地理"));
            return capability;
        }

        @Override
        protected String normalizeSubject(ScoreLineModels.Query query) {
            String subject = firstNotBlank(query.getSubjectCategory(), query.getSubjectType());
            if (StringUtils.isBlank(subject)) {
                return "综合改革";
            }
            return subject;
        }

        @Override
        public ScoreLineModels.QueryResult queryScoreRank(ScoreLineModels.Query query) {
            if (usesPhysicsHistory(query)) {
                return emptyResult(query, TYPE_SCORE_RANK, getMissingDataReason(TYPE_SCORE_RANK));
            }
            return super.queryScoreRank(query);
        }

        @Override
        public ScoreLineModels.QueryResult queryAdmissionLine(ScoreLineModels.Query query) {
            if (usesPhysicsHistory(query)) {
                return emptyResult(query, TYPE_ADMISSION_LINE, getMissingDataReason(TYPE_ADMISSION_LINE));
            }
            return queryGroupLines(query, TYPE_ADMISSION_LINE);
        }

        @Override
        public ScoreLineModels.QueryResult queryMajorGroupLine(ScoreLineModels.Query query) {
            if (usesPhysicsHistory(query)) {
                return emptyResult(query, TYPE_MAJOR_GROUP_LINE, getMissingDataReason(TYPE_MAJOR_GROUP_LINE));
            }
            return queryGroupLines(query, TYPE_MAJOR_GROUP_LINE);
        }

        @Override
        protected LambdaQueryWrapper<DataAdmissionGroupLine> groupLineWrapper(ScoreLineModels.Query query, String subject) {
            LambdaQueryWrapper<DataAdmissionGroupLine> wrapper = super.groupLineWrapper(query, "综合改革");
            return wrapper;
        }

        @Override
        protected ScoreLineModels.QueryResult queryGroupLines(ScoreLineModels.Query query, String type) {
            ScoreLineModels.QueryResult result = super.queryGroupLines(query, type);
            if (StringUtils.isBlank(query.getSelectedSubjects()) || result.getPageResult().getItems().isEmpty()) {
                return result;
            }
            List<String> selected = parseSelectedSubjects(query.getSelectedSubjects());
            List<ScoreLineModels.Record> filtered = result.getPageResult().getItems().stream()
                    .filter(record -> matchesSelectedSubjects(record.getRequiredSubjects(), selected))
                    .toList();
            result.setPageResult(PageResult.of(filtered, filtered.size(), safePage(query), safePageSize(query)));
            result.setDataStatus(filtered.isEmpty() ? "MISSING" : "AVAILABLE");
            result.setMissingReason(filtered.isEmpty() ? "海南 3+3 当前选科组合未命中已导入且可核验的 requiredSubjects 记录。" : "");
            return result;
        }

        @Override
        public String getMissingDataReason(String scoreLineType) {
            if (TYPE_SCORE_RANK.equals(scoreLineType)) {
                return "海南 3+3 不分物理/历史；请按综合改革或 selectedSubjects 查询，一分一段/标准分位次需核验官方结构化源。";
            }
            if (TYPE_ADMISSION_LINE.equals(scoreLineType) || TYPE_MAJOR_GROUP_LINE.equals(scoreLineType)) {
                return "海南 3+3 不接受物理类/历史类筛选；需按 selectedSubjects 与院校专业组 requiredSubjects 匹配。";
            }
            if (TYPE_MAJOR_SCORE.equals(scoreLineType)) {
                return "海南专业录取线需按 3+3 选科要求单独建模，不能套物理/历史或普通位次模型。";
            }
            if (TYPE_ART_SPORT.equals(scoreLineType)) {
                return "海南艺术体育需按本省综合分和资格规则单独查询，当前仅展示缺口。";
            }
            return super.getMissingDataReason(scoreLineType);
        }

        private boolean usesPhysicsHistory(ScoreLineModels.Query query) {
            String subject = firstNotBlank(query.getSubjectCategory(), query.getSubjectType());
            return "物理类".equals(subject) || "历史类".equals(subject) || "首选物理".equals(subject) || "首选历史".equals(subject);
        }

        private List<String> parseSelectedSubjects(String raw) {
            if (StringUtils.isBlank(raw)) {
                return List.of();
            }
            return java.util.Arrays.stream(raw.split("[,，/、\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList();
        }

        private boolean matchesSelectedSubjects(String requiredSubjects, List<String> selected) {
            if (StringUtils.isBlank(requiredSubjects) || selected.isEmpty()) {
                return true;
            }
            String normalized = requiredSubjects.toLowerCase(Locale.ROOT);
            if (normalized.contains("不限") || normalized.contains("不提科目要求")) {
                return true;
            }
            return selected.stream().anyMatch(normalized::contains);
        }
    }

    private static class LevelOneMissingAdapter extends BaseAdapter {

        LevelOneMissingAdapter(ProvincePolicyService provincePolicyService,
                               String provinceCode,
                               String provinceName,
                               String officialSourceName,
                               String officialSourceUrl,
                               String notice) {
            super(provincePolicyService, provinceCode, provinceName, officialSourceName, officialSourceUrl,
                    "AI_QA_ONLY", "OFFICIAL_DATA_PENDING", List.of("物理类", "历史类"), List.of(), notice);
        }

        @Override
        public ScoreLineModels.Capability capability() {
            ScoreLineModels.Capability capability = super.capability();
            capability.setDataStatus("OFFICIAL_DATA_PENDING");
            capability.setLatestOfficialDataYear(2025);
            capability.setAvailableYears(List.of());
            return capability;
        }

        @Override
        protected List<String> buildNotices() {
            return List.of(
                    notice,
                    "查询入口已开放；当前只展示数据缺口说明，不生成院校清单。",
                    "2026 官方数据发布并完成导入、校验后，才会升级历史估算或正式推荐能力。"
            );
        }

        @Override
        protected List<Integer> listAvailableYears() {
            return List.of();
        }

        @Override
        public ScoreLineModels.QueryResult queryControlLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_CONTROL_LINE, getMissingDataReason(TYPE_CONTROL_LINE));
        }

        @Override
        public ScoreLineModels.QueryResult queryScoreRank(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_SCORE_RANK, getMissingDataReason(TYPE_SCORE_RANK));
        }

        @Override
        public ScoreLineModels.QueryResult queryAdmissionLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_ADMISSION_LINE, getMissingDataReason(TYPE_ADMISSION_LINE));
        }

        @Override
        public ScoreLineModels.QueryResult queryMajorGroupLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_MAJOR_GROUP_LINE, getMissingDataReason(TYPE_MAJOR_GROUP_LINE));
        }

        @Override
        public ScoreLineModels.QueryResult queryMajorScoreLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_MAJOR_SCORE, getMissingDataReason(TYPE_MAJOR_SCORE));
        }

        @Override
        public ScoreLineModels.QueryResult queryArtSportLine(ScoreLineModels.Query query) {
            return emptyResult(query, TYPE_ART_SPORT, getMissingDataReason(TYPE_ART_SPORT));
        }

        @Override
        public String getMissingDataReason(String scoreLineType) {
            if (TYPE_SCORE_RANK.equals(scoreLineType)) {
                return provinceName + "官方历史一分一段表尚未接入，暂不能按位次查询。";
            }
            if (TYPE_ADMISSION_LINE.equals(scoreLineType) || TYPE_MAJOR_GROUP_LINE.equals(scoreLineType)) {
                return provinceName + "普通批院校专业组投档线尚未接入，暂不展示历史估算。";
            }
            if (TYPE_CONTROL_LINE.equals(scoreLineType)) {
                return provinceName + "批次控制线尚未接入结构化查询。";
            }
            if (TYPE_MAJOR_SCORE.equals(scoreLineType)) {
                return provinceName + "专业录取最低分和选科要求尚未接入，不能从其他省份推断。";
            }
            if (TYPE_ART_SPORT.equals(scoreLineType)) {
                return provinceName + "艺术、体育、专项等批次需按本省综合分和资格规则单独建模。";
            }
            return provinceName + "当前类型暂无可核验结构化数据。";
        }
    }
}
