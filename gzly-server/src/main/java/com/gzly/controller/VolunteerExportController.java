package com.gzly.controller;

import com.gzly.common.exception.BizException;
import com.gzly.service.SafetyCodeRequestResolver;
import com.gzly.service.SafetyCodeService;
import com.gzly.service.VolunteerExportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/volunteer/plans/{planId}")
@RequiredArgsConstructor
public class VolunteerExportController {

    private final VolunteerExportService exportService;
    private final SafetyCodeRequestResolver safetyCodeRequestResolver;
    private final SafetyCodeService safetyCodeService;

    @PostMapping("/export-long-image")
    public ResponseEntity<byte[]> exportLongImage(@PathVariable Long planId,
                                                  @RequestBody(required = false) ExportRequest req,
                                                  HttpServletRequest request) {
        String key = safetyCodeRequestResolver.resolve(request, req);
        if (!safetyCodeService.verifyPlanAccess(planId, key)) {
            throw new BizException(403, "方案不存在或访问密钥无效");
        }
        byte[] data = exportService.exportLongImage(planId, key);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("gzly-volunteer-plan.png"))
                .body(data);
    }

    @PostMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long planId,
                                              @RequestBody(required = false) ExportRequest req,
                                              HttpServletRequest request) {
        String key = safetyCodeRequestResolver.resolve(request, req);
        if (!safetyCodeService.verifyPlanAccess(planId, key)) {
            throw new BizException(403, "方案不存在或访问密钥无效");
        }
        byte[] data = exportService.exportExcel(planId, key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("gzly-volunteer-plan.xlsx"))
                .body(data);
    }

    private String attachment(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    @Data
    public static class ExportRequest {
        private String safetyCode;
        private String accessKey;
    }
}
