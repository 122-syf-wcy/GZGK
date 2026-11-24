package com.gzgk.core.policy;

import com.gzgk.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/policies")
public class PolicyController {

    private final ProvincePolicyService policyService;

    public PolicyController(ProvincePolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ApiResponse<List<ProvincePolicyModels.ProvincePolicyView>> listAll() {
        return ApiResponse.ok(policyService.listAll());
    }

    @GetMapping("/{provinceCode}")
    public ApiResponse<ProvincePolicyModels.ProvincePolicyView> getOne(@PathVariable String provinceCode) {
        return ApiResponse.ok(policyService.getPolicy(provinceCode));
    }
}
