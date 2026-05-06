package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.service.SiteStatsService;
import com.gzly.service.SiteStatsService.OnlineStats;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/site-stats")
@RequiredArgsConstructor
public class SiteStatsController {

    private final SiteStatsService siteStatsService;

    @GetMapping("/online")
    public Result<OnlineStats> online(HttpServletRequest request) {
        return Result.ok(siteStatsService.touchAndCount(request));
    }
}
