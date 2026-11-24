package com.gzgk.core.catalog;

import com.gzgk.common.api.ApiResponse;
import com.gzgk.common.api.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/universities")
public class UniversityController {

    private final UniversityCatalogService catalogService;

    public UniversityController(UniversityCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<UniversityCatalogService.SearchPage> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(catalogService.search(keyword, province, tag, page, size));
    }

    @GetMapping("/{schoolId}")
    public ApiResponse<UniversityRecord> detail(@PathVariable String schoolId) {
        return catalogService.findBySchoolId(schoolId)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new BizException(404, "未找到对应院校"));
    }
}
