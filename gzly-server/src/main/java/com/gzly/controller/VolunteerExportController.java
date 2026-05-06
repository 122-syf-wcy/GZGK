package com.gzly.controller;

import com.gzly.service.VolunteerExportService;
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

    @PostMapping("/export-long-image")
    public ResponseEntity<byte[]> exportLongImage(@PathVariable Long planId,
                                                  @RequestParam(required = false) String safetyCode,
                                                  @RequestParam(required = false) String accessKey,
                                                  @RequestHeader(value = "X-Plan-Safety-Code", required = false) String headerSafetyCode,
                                                  @RequestHeader(value = "X-Plan-Access-Key", required = false) String headerAccessKey,
                                                  @RequestBody ExportRequest req) {
        byte[] data = exportService.exportLongImage(planId,
                firstNonBlank(req == null ? null : req.getSafetyCode(), safetyCode, headerSafetyCode,
                        req == null ? null : req.getAccessKey(), accessKey, headerAccessKey));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("gzly-volunteer-plan.png"))
                .body(data);
    }

    @PostMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long planId,
                                              @RequestParam(required = false) String safetyCode,
                                              @RequestParam(required = false) String accessKey,
                                              @RequestHeader(value = "X-Plan-Safety-Code", required = false) String headerSafetyCode,
                                              @RequestHeader(value = "X-Plan-Access-Key", required = false) String headerAccessKey,
                                              @RequestBody ExportRequest req) {
        byte[] data = exportService.exportExcel(planId,
                firstNonBlank(req == null ? null : req.getSafetyCode(), safetyCode, headerSafetyCode,
                        req == null ? null : req.getAccessKey(), accessKey, headerAccessKey));
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

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
