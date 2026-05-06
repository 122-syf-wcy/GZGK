package com.gzly.controller;

import com.gzly.common.PageResult;
import com.gzly.common.Result;
import com.gzly.entity.UniOfficialLink;
import com.gzly.entity.University;
import com.gzly.mapper.UniOfficialLinkMapper;
import com.gzly.service.OfficialLinkPriorityService;
import com.gzly.service.UniversityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/university")
@RequiredArgsConstructor
public class UniversityController {

    private final UniversityService universityService;
    private final UniOfficialLinkMapper officialLinkMapper;

    @GetMapping("/list")
    public Result<PageResult<University>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        return Result.ok(universityService.list(keyword, province, region, tag, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<University> detail(@PathVariable Long id) {
        University u = universityService.getById(id);
        if (u == null) {
            return Result.fail("院校不存在");
        }
        return Result.ok(u);
    }

    @GetMapping("/by-school-id")
    public Result<University> bySchoolId(@RequestParam String schoolId) {
        University u = universityService.getBySchoolId(schoolId);
        if (u == null) {
            return Result.fail("院校不存在");
        }
        return Result.ok(u);
    }

    @GetMapping("/official-links")
    public Result<UniOfficialLink> officialLinks(@RequestParam String schoolId) {
        UniOfficialLink link = officialLinkMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UniOfficialLink>()
                        .eq(UniOfficialLink::getSchoolId, schoolId)
                        .last("LIMIT 1"));
        return Result.ok(sanitizeOfficialLink(link));
    }

    private UniOfficialLink sanitizeOfficialLink(UniOfficialLink link) {
        if (link == null) {
            return null;
        }
        link.setSchoolSite(OfficialLinkPriorityService.sanitizeOfficialUrl(link.getSchoolSite()));
        link.setAdmissionSite(OfficialLinkPriorityService.sanitizeOfficialUrl(link.getAdmissionSite()));
        link.setAdmissionBrochureUrl(sanitizeDetailUrl(link.getAdmissionBrochureUrl(), link));
        link.setMajorCatalogUrl(sanitizeDetailUrl(link.getMajorCatalogUrl(), link));
        link.setTuitionInfoUrl(sanitizeDetailUrl(link.getTuitionInfoUrl(), link));
        return link;
    }

    private String sanitizeDetailUrl(String value, UniOfficialLink link) {
        if (!OfficialLinkPriorityService.hasUsableDetailUrl(
                value,
                link.getSchoolSite(),
                link.getAdmissionSite())) {
            return "";
        }
        return OfficialLinkPriorityService.sanitizeOfficialUrl(value);
    }
}
