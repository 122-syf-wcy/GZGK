package com.gzly.service;

import com.gzly.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataReviewWorkbenchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void list_shouldReadReviewOnlyCsvAndKeepPendingByDefault() throws Exception {
        writeFixtures();
        DataReviewWorkbenchService service = new DataReviewWorkbenchService(tempDir.toString());

        DataReviewWorkbenchService.ReviewQuery query = new DataReviewWorkbenchService.ReviewQuery();
        DataReviewWorkbenchService.ReviewListResponse response = service.list(query);

        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getStats().getPending()).isEqualTo(2);
        assertThat(response.getStats().getApproved()).isZero();
        assertThat(response.getItems()).extracting(DataReviewWorkbenchService.ReviewRow::getProvinceCode)
                .containsExactly("CQ", "GS");
        assertThat(response.getValidatorCommand()).contains("validate_manual_intake_dry_run.py");
    }

    @Test
    void saveDecision_shouldRequireReviewerWhenApprovedAndWriteOnlySidecar() throws Exception {
        writeFixtures();
        DataReviewWorkbenchService service = new DataReviewWorkbenchService(tempDir.toString());
        DataReviewWorkbenchService.ReviewDecisionRequest bad = new DataReviewWorkbenchService.ReviewDecisionRequest();
        bad.setLineNo(2);
        bad.setDecision("APPROVED");

        assertThatThrownBy(() -> service.saveDecision(bad)).isInstanceOf(BizException.class);

        DataReviewWorkbenchService.ReviewDecisionRequest ok = new DataReviewWorkbenchService.ReviewDecisionRequest();
        ok.setLineNo(2);
        ok.setDecision("APPROVED");
        ok.setReviewer("human:reviewer");
        ok.setReviewNote("官网核验通过");
        DataReviewWorkbenchService.ReviewRow row = service.saveDecision(ok);

        assertThat(row.getCurrentStatus()).isEqualTo("APPROVED");
        assertThat(Files.readString(tempDir.resolve("review_decisions_sidecar.csv"))).contains("human:reviewer");
        assertThat(Files.exists(tempDir.resolve("reviewed_group_plan_manual_intake.csv"))).isFalse();
    }

    @Test
    void exportReviewedFiles_shouldCreateReviewOnlyExportsButNotProcessed() throws Exception {
        writeFixtures();
        DataReviewWorkbenchService service = new DataReviewWorkbenchService(tempDir.toString());
        DataReviewWorkbenchService.ReviewDecisionRequest request = new DataReviewWorkbenchService.ReviewDecisionRequest();
        request.setLineNo(2);
        request.setDecision("REJECTED");
        request.setRejectReason("批次无法确认");
        service.saveDecision(request);

        DataReviewWorkbenchService.ExportResponse response = service.exportReviewedFiles();

        assertThat(response.isImportAllowed()).isFalse();
        assertThat(response.getRejectedCount()).isEqualTo(1);
        assertThat(Files.exists(tempDir.resolve("reviewed_group_plan_manual_intake.csv"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("approved_only.csv"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("rejected_rows.tsv"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("review_summary.md"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("processed"))).isFalse();
    }

    @Test
    void saveDecision_shouldRejectUnknownLineWithoutWritingSidecar() throws Exception {
        writeFixtures();
        DataReviewWorkbenchService service = new DataReviewWorkbenchService(tempDir.toString());
        DataReviewWorkbenchService.ReviewDecisionRequest request = new DataReviewWorkbenchService.ReviewDecisionRequest();
        request.setLineNo(99);
        request.setDecision("REJECTED");
        request.setRejectReason("不存在的行");

        assertThatThrownBy(() -> service.saveDecision(request)).isInstanceOf(BizException.class);
        assertThat(Files.exists(tempDir.resolve("review_decisions_sidecar.csv"))).isFalse();
    }

    @Test
    void resolveAllowed_shouldRejectPathTraversal() {
        DataReviewWorkbenchService service = new DataReviewWorkbenchService(tempDir.toString());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveAllowed", "../secret"))
                .isInstanceOf(BizException.class);
    }

    private void writeFixtures() throws Exception {
        Files.writeString(tempDir.resolve("group_plan_manual_intake_template.csv"), String.join("\n",
                "province_code,year,batch,subject_type,school_id,university_name,major_group_code,major_group_name,major_name,plan_count,source_name,source_url,source_file_path,uploader,reviewer,confidence,notes",
                "CQ,2025,普通本科批（review-only）,物理,106,暨南大学,,,计算机科学与技术,2,学校官网,https://example.com/a,evidence/a.tsv,codex_server_review_only,,LOW,待人工复核",
                "GS,2025,普通本科批（review-only）,理工,106,暨南大学,,,汉语言文学,1,学校官网,,evidence/b.tsv,codex_server_review_only,,LOW,待人工复核",
                ""));
        Files.writeString(tempDir.resolve("manual_review_checklist_20260623.tsv"), String.join("\n",
                "source_line\treview_status\tevidence_file",
                "2\tPENDING_HUMAN_REVIEW\tevidence/a.tsv",
                "3\tPENDING_HUMAN_REVIEW\tevidence/b.tsv",
                ""));
    }
}
