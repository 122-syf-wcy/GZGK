package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.VolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/volunteer/plans")
@RequiredArgsConstructor
public class VolunteerPlanController {

    private final VolunteerService volunteerService;

    @GetMapping("/{planId}")
    public Result<VolunteerService.PlanResult> detail(@PathVariable Long planId,
                                                      @RequestParam(required = false) String safetyCode,
                                                      @RequestParam(required = false) String accessKey,
                                                      @RequestHeader(value = "X-Plan-Safety-Code", required = false) String headerSafetyCode,
                                                      @RequestHeader(value = "X-Plan-Access-Key", required = false) String headerAccessKey) {
        String key = firstNonBlank(safetyCode, headerSafetyCode, accessKey, headerAccessKey);
        VolunteerService.PlanResult plan = volunteerService.getPlanResult(planId, key);
        if (plan == null) {
            throw new BizException("方案不存在或访问密钥无效");
        }
        return Result.ok(plan);
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
