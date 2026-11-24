package com.gzgk.core.sandbox;

import com.gzgk.common.api.ApiResponse;
import com.gzgk.core.recommend.RecommendModels.PlanSnapshot;
import com.gzgk.core.sandbox.PlanSandboxService.ApplyResult;
import com.gzgk.core.sandbox.PlanSandboxService.MutationLogEntry;
import com.gzgk.core.sandbox.PlanSandboxService.MutationOp;
import com.gzgk.core.sandbox.PlanSandboxService.SimulationResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/plans")
public class PlanController {

    private final PlanSandboxService sandboxService;

    public PlanController(PlanSandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @GetMapping
    public ApiResponse<List<PlanSnapshot>> listRecent() {
        return ApiResponse.ok(sandboxService.listRecentPlans());
    }

    @GetMapping("/{planId}")
    public ApiResponse<PlanSnapshot> getPlan(@PathVariable String planId) {
        return ApiResponse.ok(sandboxService.getPlanOrThrow(planId));
    }

    @PostMapping("/{planId}/simulate")
    public ApiResponse<SimulationResult> simulate(
            @PathVariable String planId,
            @RequestBody SimulatePayload payload
    ) {
        return ApiResponse.ok(sandboxService.simulate(planId, payload.operations()));
    }

    @PostMapping("/{planId}/apply")
    public ApiResponse<ApplyResult> apply(
            @PathVariable String planId,
            @RequestBody ApplyPayload payload
    ) {
        return ApiResponse.ok(sandboxService.applyMutation(
                planId, payload.expectedVersion(), payload.threadId(), payload.summary(), payload.operations()));
    }

    @PostMapping("/{planId}/rollback")
    public ApiResponse<ApplyResult> rollback(@PathVariable String planId) {
        return ApiResponse.ok(sandboxService.rollbackLast(planId));
    }

    @GetMapping("/{planId}/mutations")
    public ApiResponse<List<MutationLogEntry>> mutations(@PathVariable String planId) {
        return ApiResponse.ok(sandboxService.listMutations(planId));
    }

    public record SimulatePayload(List<MutationOp> operations) {
    }

    public record ApplyPayload(int expectedVersion, String threadId, String summary, List<MutationOp> operations) {
    }
}
