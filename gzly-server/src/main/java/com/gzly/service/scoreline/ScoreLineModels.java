package com.gzly.service.scoreline;

import com.gzly.common.PageResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScoreLineModels {

    public static final String TYPE_CONTROL_LINE = "control_line";
    public static final String TYPE_SCORE_RANK = "score_rank";
    public static final String TYPE_ADMISSION_LINE = "admission_line";
    public static final String TYPE_MAJOR_GROUP_LINE = "major_group_line";
    public static final String TYPE_MAJOR_SCORE = "major_score";
    public static final String TYPE_ART_SPORT = "art_sport";

    private ScoreLineModels() {
    }

    @Data
    public static class Capability {
        private String provinceCode;
        private String provinceName;
        private String officialSourceName;
        private String officialSourceUrl;
        private String policyMode;
        private String subjectMode;
        private String dataStatus;
        private Integer latestOfficialDataYear;
        private Integer targetYear;
        private List<Integer> availableYears = new ArrayList<>();
        private List<String> subjectOptions = new ArrayList<>();
        private List<String> selectedSubjectOptions = new ArrayList<>();
        private List<String> notices = new ArrayList<>();
        private List<TypeCapability> scoreLineTypes = new ArrayList<>();
        private Map<String, String> missingReasonByType = Map.of();
    }

    @Data
    public static class TypeCapability {
        private String type;
        private String label;
        private String description;
        private boolean queryable;
        private String dataStatus;
        private String missingReason;
        private List<Integer> availableYears = new ArrayList<>();
        private List<String> sourceTables = new ArrayList<>();
    }

    @Data
    public static class Query {
        private Integer year;
        private String scoreLineType;
        private String batchCode;
        private String subjectCategory;
        private String subjectType;
        private String selectedSubjects;
        private String schoolCode;
        private String schoolName;
        private String majorGroupCode;
        private String majorName;
        private Integer score;
        private int page = 1;
        private int pageSize = 20;
    }

    @Data
    public static class QueryResult {
        private String provinceCode;
        private String provinceName;
        private Integer year;
        private String scoreLineType;
        private String subjectCategory;
        private String dataStatus;
        private String missingReason;
        private PageResult<Record> pageResult = PageResult.of(List.of(), 0, 1, 20);
    }

    @Data
    public static class Record {
        private Long id;
        private String provinceCode;
        private String provinceName;
        private Integer year;
        private String scoreLineType;
        private String batchCode;
        private String batchName;
        private String subjectCategory;
        private String schoolCode;
        private String schoolName;
        private String majorGroupCode;
        private String majorGroupName;
        private String majorName;
        private Integer minScore;
        private Integer minRank;
        private Integer sameScoreCount;
        private Integer planCount;
        private Integer score;
        private Integer cumulativeCount;
        private Integer rankLow;
        private Integer rankHigh;
        private String requiredSubjects;
        private String sourceFile;
        private String sourceUrl;
        private String sourcePage;
        private String dataStatus;
        private String missingReason;
    }
}
