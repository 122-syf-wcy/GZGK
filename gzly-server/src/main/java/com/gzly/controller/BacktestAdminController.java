package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.entity.AlgoBacktestReport;
import com.gzly.service.BacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 推荐算法回测（管理端）。
 * 路径位于 /admin/** 下，由 AuthInterceptor 强制 admin JWT。
 */
@RestController
@RequestMapping("/admin/backtest")
@RequiredArgsConstructor
public class BacktestAdminController {

    private final BacktestService backtestService;

    /**
     * 触发一次离线回测：用 evaluationYear-1 及以前数据推荐，用 evaluationYear 真实录取位次验证。
     * 示例请求体：{"evaluationYear":2025,"subjectType":"物理类","strategyMode":"均衡型"}
     */
    @PostMapping("/run")
    public Result<BacktestService.BacktestReportView> run(@RequestBody BacktestService.BacktestRequest request) {
        return Result.ok(backtestService.run(request));
    }

    @GetMapping("/reports")
    public Result<List<AlgoBacktestReport>> reports(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(backtestService.listReports(limit));
    }
}
