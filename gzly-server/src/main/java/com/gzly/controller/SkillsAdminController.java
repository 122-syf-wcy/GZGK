package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.common.Result;
import com.gzly.entity.SkillsSource;
import com.gzly.mapper.SkillsSourceMapper;
import com.gzly.service.SkillsSyncService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SkillsAdminController {

    private final SkillsSyncService skillsSyncService;
    private final SkillsSourceMapper sourceMapper;

    @GetMapping("/admin/skills/sources")
    public Result<List<SkillsSource>> sources() {
        return Result.ok(sourceMapper.selectList(new LambdaQueryWrapper<SkillsSource>()
                .orderByDesc(SkillsSource::getLastSyncTime)
                .orderByDesc(SkillsSource::getId)));
    }

    @PostMapping("/admin/skills/sync")
    public Result<SkillsSyncService.SyncResult> sync(@RequestBody(required = false) SyncRequest req) {
        return Result.ok(skillsSyncService.sync(req == null ? null : req.getLocalPath()));
    }

    @Data
    public static class SyncRequest {
        private String localPath;
    }
}
