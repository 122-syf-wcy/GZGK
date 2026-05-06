package com.gzly.service;

import com.gzly.common.exception.BizException;
import com.gzly.entity.DataAdmissionGroupLine;
import com.gzly.entity.DataSourceRegistry;
import com.gzly.entity.University;
import com.gzly.mapper.DataAdmissionGroupLineMapper;
import com.gzly.mapper.DataAdmissionGroupPlanMapper;
import com.gzly.mapper.DataScoreRankMapper;
import com.gzly.mapper.DataSourceRegistryMapper;
import com.gzly.mapper.UniversityMapper;
import com.gzly.service.SichuanDataAdminService.GroupLineImportRequest;
import com.gzly.service.SichuanDataAdminService.GroupLineRow;
import com.gzly.service.SichuanDataAdminService.GroupPlanImportRequest;
import com.gzly.service.SichuanDataAdminService.GroupPlanRow;
import com.gzly.service.SichuanDataAdminService.ImportResult;
import com.gzly.service.SichuanDataAdminService.ScoreRankImportRequest;
import com.gzly.service.SichuanDataAdminService.ScoreRankRow;
import com.gzly.service.SichuanDataAdminService.StatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SichuanDataAdminServiceTest {

    private DataSourceRegistryMapper sourceRegistryMapper;
    private DataScoreRankMapper dataScoreRankMapper;
    private DataAdmissionGroupLineMapper groupLineMapper;
    private DataAdmissionGroupPlanMapper groupPlanMapper;
    private UniversityMapper universityMapper;
    private SichuanDataAdminService service;

    @BeforeEach
    void setUp() {
        sourceRegistryMapper = mock(DataSourceRegistryMapper.class);
        dataScoreRankMapper = mock(DataScoreRankMapper.class);
        groupLineMapper = mock(DataAdmissionGroupLineMapper.class);
        groupPlanMapper = mock(DataAdmissionGroupPlanMapper.class);
        universityMapper = mock(UniversityMapper.class);
        service = new SichuanDataAdminService(
                sourceRegistryMapper, dataScoreRankMapper, groupLineMapper, groupPlanMapper, universityMapper);
    }

    @Test
    void status_marksGenerationReadyOnlyWhenBothSubjectsMeetGate() {
        when(dataScoreRankMapper.selectCount(any())).thenReturn(600L, 600L);
        when(groupLineMapper.countDistinctGroups(eq("SC"), eq(2025), eq("物理类"), any())).thenReturn(45L);
        when(groupLineMapper.countDistinctGroups(eq("SC"), eq(2025), eq("历史类"), any())).thenReturn(44L);
        when(groupPlanMapper.countDistinctGroups(eq("SC"), eq(2025), eq("物理类"), any())).thenReturn(45L);
        when(groupPlanMapper.countDistinctGroups(eq("SC"), eq(2025), eq("历史类"), any())).thenReturn(45L);
        when(sourceRegistryMapper.selectList(any())).thenReturn(List.of());

        StatusResponse status = service.status(2025);

        assertThat(status.isGenerationReady()).isFalse();
        assertThat(status.getSubjects().get("物理类").isGenerationReady()).isTrue();
        assertThat(status.getSubjects().get("历史类").isGenerationReady()).isFalse();
        assertThat(status.getSubjects().get("历史类").getLockReason()).contains("尚未完整核验");
        assertThat(status.getSourceCompleteness().get("group_line").getSourceCompleteness())
                .isEqualTo("missing_official_source");
        assertThat(status.getBlockingReasons()).isNotEmpty();
    }

    @Test
    void status_reportsSourceCompletenessWithoutUnlockingWhenOfficialPagesOnlyRegistered() {
        when(dataScoreRankMapper.selectCount(any())).thenReturn(0L, 0L);
        when(groupLineMapper.countDistinctGroups(eq("SC"), eq(2025), any(), any())).thenReturn(0L);
        when(groupPlanMapper.countDistinctGroups(eq("SC"), eq(2025), any(), any())).thenReturn(0L);
        when(sourceRegistryMapper.selectList(any())).thenReturn(List.of(
                source("score_rank", "manual_review"),
                source("group_line", "manual_review"),
                source("group_plan", "manual_review")
        ));

        StatusResponse status = service.status(2025);

        assertThat(status.isGenerationReady()).isFalse();
        assertThat(status.getSourceStatusCounts()).containsEntry("manual_review", 3L);
        assertThat(status.getSourceCompleteness().get("score_rank").getSourceCompleteness())
                .isEqualTo("official_source_registered");
        assertThat(status.getSourceCompleteness().get("group_line").getBlockingReason())
                .contains("未发现完整院校专业组调档线明细");
        assertThat(status.getSourceCompleteness().get("group_plan").getBlockingReason())
                .contains("未发现普通本科批B段全量结构化招生计划");
    }

    @Test
    void importScoreRanks_dryRunCalculatesRanksAndDoesNotWrite() {
        ScoreRankImportRequest request = new ScoreRankImportRequest();
        request.setYear(2025);
        request.setSubjectType("物理类");
        request.setSourcePageUrl("https://www.sceea.cn/Html/202506/Newsdetail_4335.html");
        request.setSourceUrl("https://www.sceea.cn/Upload/image/20250625/a.jpg");
        request.setRows(List.of(scoreRankRow(700, 2, 2), scoreRankRow(699, 3, 5)));
        when(dataScoreRankMapper.selectOne(any())).thenReturn(null);

        ImportResult result = service.importScoreRanks(request, true);

        assertThat(result.getRejected()).isZero();
        assertThat(result.getValidRows()).isEqualTo(2);
        assertThat(result.getInserted()).isEqualTo(2);
        verify(dataScoreRankMapper, never()).insert(any());
    }

    @Test
    void importScoreRanks_acceptsSichuanOfficialOmittedScoreRows() {
        ScoreRankImportRequest request = new ScoreRankImportRequest();
        request.setYear(2025);
        request.setSubjectType("物理类");
        request.setSourcePageUrl("https://www.sceea.cn/Html/202506/Newsdetail_4335.html");
        request.setSourceUrl("https://www.sceea.cn/Upload/image/20250625/a.jpg");
        request.setRows(List.of(
                scoreRankRow(691, 8, 68),
                scoreRankRow(690, 12, 80),
                scoreRankRow(172, 3, 284743),
                scoreRankRow(170, 4, 284747)
        ));
        when(dataScoreRankMapper.selectOne(any())).thenReturn(null);

        ImportResult result = service.importScoreRanks(request, false);

        assertThat(result.getRejected()).isZero();
        assertThat(result.getValidRows()).isEqualTo(4);
        ArgumentCaptor<com.gzly.entity.DataScoreRank> captor = ArgumentCaptor.forClass(com.gzly.entity.DataScoreRank.class);
        verify(dataScoreRankMapper, org.mockito.Mockito.times(4)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getRankLow()).isEqualTo(61);
        assertThat(captor.getAllValues().get(0).getRankHigh()).isEqualTo(68);
        assertThat(captor.getAllValues().get(3).getRankLow()).isEqualTo(284744);
        assertThat(captor.getAllValues().get(3).getRankHigh()).isEqualTo(284747);
    }

    @Test
    void importScoreRanks_rejectsNonOfficialSource() {
        ScoreRankImportRequest request = new ScoreRankImportRequest();
        request.setYear(2025);
        request.setSubjectType("物理类");
        request.setSourcePageUrl("https://example.com/source");
        request.setRows(List.of(scoreRankRow(700, 2, 2)));

        assertThatThrownBy(() -> service.importScoreRanks(request, true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("sourcePageUrl");
    }

    @Test
    void importGroupLines_rejectsUnmatchedSchool() {
        GroupLineImportRequest request = groupLineRequest();
        GroupLineRow row = new GroupLineRow();
        row.setSchoolId("999999");
        row.setUniversityName("不存在大学");
        row.setGroupCode("001");
        row.setSubjectType("物理类");
        row.setMinScore(520);
        row.setMinRank(30000);
        request.setRows(List.of(row));
        when(universityMapper.selectOne(any())).thenReturn(null);

        ImportResult result = service.importGroupLines(request, true);

        assertThat(result.getRejected()).isEqualTo(1);
        assertThat(result.getUnresolvedSchools()).contains("999999|不存在大学");
        verify(groupLineMapper, never()).insert(any());
    }

    @Test
    void importGroupPlans_requiresExistingGroupLine() {
        GroupPlanImportRequest request = groupPlanRequest();
        GroupPlanRow row = new GroupPlanRow();
        row.setSchoolId("100");
        row.setUniversityName("测试大学");
        row.setGroupCode("001");
        row.setMajorCode("01");
        row.setMajorName("计算机类");
        row.setSubjectType("物理类");
        row.setPlanCount(5);
        request.setRows(List.of(row));
        when(universityMapper.selectOne(any())).thenReturn(university("100", "测试大学"));
        when(groupLineMapper.selectOne(any())).thenReturn(null);

        ImportResult result = service.importGroupPlans(request, true);

        assertThat(result.getRejected()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrors()).contains("未找到对应院校专业组线");
        verify(groupPlanMapper, never()).insert(any());
    }

    @Test
    void importGroupPlans_acceptsReviewedRowWhenGroupLineExists() {
        GroupPlanImportRequest request = groupPlanRequest();
        GroupPlanRow row = new GroupPlanRow();
        row.setSchoolId("100");
        row.setUniversityName("测试大学");
        row.setGroupCode("001");
        row.setMajorCode("01");
        row.setMajorName("计算机类");
        row.setSubjectType("物理类");
        row.setPlanCount(5);
        request.setRows(List.of(row));
        when(universityMapper.selectOne(any())).thenReturn(university("100", "测试大学"));
        DataAdmissionGroupLine line = new DataAdmissionGroupLine();
        line.setGroupName("测试大学001组");
        line.setFirstSubjectRequirement("物理");
        line.setResubjectRequirement("不限");
        when(groupLineMapper.selectOne(any())).thenReturn(line);
        when(groupPlanMapper.selectOne(any())).thenReturn(null);

        ImportResult result = service.importGroupPlans(request, true);

        assertThat(result.getRejected()).isZero();
        assertThat(result.getValidRows()).isEqualTo(1);
        assertThat(result.getInserted()).isEqualTo(1);
    }

    @Test
    void importGroupPlans_allowsSameGroupAndMajorCodeAcrossSubjects() {
        GroupPlanImportRequest request = groupPlanRequest();
        GroupPlanRow physics = new GroupPlanRow();
        physics.setSchoolId("100");
        physics.setUniversityName("测试大学");
        physics.setGroupCode("101");
        physics.setMajorCode("01");
        physics.setMajorName("会计学");
        physics.setSubjectType("物理类");
        physics.setPlanCount(5);
        GroupPlanRow history = new GroupPlanRow();
        history.setSchoolId("100");
        history.setUniversityName("测试大学");
        history.setGroupCode("101");
        history.setMajorCode("01");
        history.setMajorName("会计学");
        history.setSubjectType("历史类");
        history.setPlanCount(3);
        request.setRows(List.of(physics, history));
        when(universityMapper.selectOne(any())).thenReturn(university("100", "测试大学"));
        DataAdmissionGroupLine line = new DataAdmissionGroupLine();
        line.setGroupName("测试大学101组");
        line.setFirstSubjectRequirement("物理/历史");
        line.setResubjectRequirement("不限");
        when(groupLineMapper.selectOne(any())).thenReturn(line);
        when(groupPlanMapper.selectOne(any())).thenReturn(null);

        ImportResult result = service.importGroupPlans(request, true);

        assertThat(result.getRejected()).isZero();
        assertThat(result.getValidRows()).isEqualTo(2);
        assertThat(result.getInserted()).isEqualTo(2);
    }

    private ScoreRankRow scoreRankRow(int score, int segmentCount, int cumulativeCount) {
        ScoreRankRow row = new ScoreRankRow();
        row.setScore(score);
        row.setSegmentCount(segmentCount);
        row.setCumulativeCount(cumulativeCount);
        return row;
    }

    private GroupLineImportRequest groupLineRequest() {
        GroupLineImportRequest request = new GroupLineImportRequest();
        request.setYear(2025);
        request.setSourcePageUrl("https://www.sceea.cn/Html/202507/Newsdetail_4405.html");
        request.setSourceUrl("https://www.sceea.cn/Html/202507/Newsdetail_4405.html");
        request.setSourceLevel("manual_verified");
        return request;
    }

    private GroupPlanImportRequest groupPlanRequest() {
        GroupPlanImportRequest request = new GroupPlanImportRequest();
        request.setYear(2025);
        request.setSourcePageUrl("https://www.sceea.cn/Html/202506/Newsdetail_4330.html");
        request.setSourceUrl("https://www.sceea.cn/Html/202506/Newsdetail_4330.html");
        request.setSourceLevel("manual_verified");
        return request;
    }

    private University university(String schoolId, String name) {
        University university = new University();
        university.setSchoolId(schoolId);
        university.setName(name);
        return university;
    }

    private DataSourceRegistry source(String dataType, String status) {
        DataSourceRegistry source = new DataSourceRegistry();
        source.setProvinceCode("SC");
        source.setYear(2025);
        source.setDataType(dataType);
        source.setStatus(status);
        return source;
    }
}
