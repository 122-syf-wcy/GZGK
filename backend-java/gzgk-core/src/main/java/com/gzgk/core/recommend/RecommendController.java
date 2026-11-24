package com.gzgk.core.recommend;

import com.gzgk.common.api.ApiResponse;
import com.gzgk.core.recommend.RecommendModels.GeneratePlanRequest;
import com.gzgk.core.recommend.RecommendModels.PlanSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommend")
public class RecommendController {

    private final RecommendPipelineService pipelineService;

    public RecommendController(RecommendPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping
    public ApiResponse<PlanSnapshot> generate(@RequestBody GeneratePlanRequest req, HttpServletRequest httpReq) {
        PlanSnapshot snapshot = pipelineService.generate(req);
        return ApiResponse.ok(snapshot, httpReq.getHeader("X-Request-Id"));
    }
}
