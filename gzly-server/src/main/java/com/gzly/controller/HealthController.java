package com.gzly.controller;

import com.gzly.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 存活探针，供部署脚本与运维监控使用。
 *
 * 刻意不做鉴权、不读数据库、不依赖任何业务服务：探针只回答“进程是否在服务请求”。
 * 部署脚本原先用 /volunteer/metrics 做探针，而该端点已收敛为管理员鉴权（会返回 401），
 * 故独立出本端点，避免把运维探针耦合在业务端点上——业务端点的鉴权口径变化不应打断部署。
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of("status", "UP"));
    }
}
