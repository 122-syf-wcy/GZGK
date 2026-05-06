package com.gzly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.common.Result;
import com.gzly.entity.Announcement;
import com.gzly.mapper.AnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementMapper announcementMapper;

    @GetMapping("/current")
    public Result<Announcement> current() {
        Announcement announcement = announcementMapper.selectOne(
                new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getStatus, 1)
                        .eq(Announcement::getPopupEnabled, 1)
                        .orderByDesc(Announcement::getSortOrder)
                        .orderByDesc(Announcement::getPublishedAt)
                        .orderByDesc(Announcement::getId)
                        .last("LIMIT 1")
        );
        return Result.ok(announcement);
    }
}
