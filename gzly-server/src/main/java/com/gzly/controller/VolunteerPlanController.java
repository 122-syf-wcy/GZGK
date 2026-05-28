package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.VolunteerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/volunteer/plans")
@RequiredArgsConstructor
public class VolunteerPlanController {

    private final VolunteerService volunteerService;

    @GetMapping("/{planId}")
    public ResponseEntity<Result<VolunteerService.PlanResult>> detail(@PathVariable Long planId,
                                                                      @RequestParam(required = false) String accessKey,
                                                                      @RequestHeader(value = "X-Plan-Access-Key", required = false) String headerAccessKey) {
        String key = accessKey == null || accessKey.isBlank() ? headerAccessKey : accessKey;
        VolunteerService.PlanResult plan = volunteerService.getPlanResult(planId, key);
        if (plan == null) {
            return ResponseEntity.status(403).body(Result.fail(403, "方案不存在或访问密钥无效"));
        }
        plan.setAccessKey(null);
        return ResponseEntity.ok(Result.ok(plan));
    }
}
