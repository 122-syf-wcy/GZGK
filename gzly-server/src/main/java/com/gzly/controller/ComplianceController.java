package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.compliance.ComplianceTextGuard;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceTextGuard complianceTextGuard;

    @PostMapping("/check")
    public Result<ComplianceTextGuard.ComplianceReview> check(@RequestBody ComplianceCheckRequest req) {
        return Result.ok(complianceTextGuard.review(
                req == null || req.getBusinessType() == null ? "manual_check" : req.getBusinessType(),
                req == null || req.getBusinessId() == null ? "manual" : req.getBusinessId(),
                req == null ? "" : req.getText()));
    }

    @Data
    public static class ComplianceCheckRequest {
        private String businessType;
        private String businessId;
        private String text;
    }
}
