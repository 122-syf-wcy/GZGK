package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzly.entity.UniOfficialLink;
import com.gzly.mapper.UniOfficialLinkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 院校官方资料入口的批量查询封装。
 * <p>
 * 用于在志愿生成时一次性拉取相关院校的招生章程/专业目录/收费标准等链接，
 * 形成证据链与人工复核清单需要的官方来源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialLinkService {

    private final UniOfficialLinkMapper officialLinkMapper;

    /**
     * 按 schoolId 批量查询官方报考资料入口。
     */
    public Map<String, UniOfficialLink> loadBySchoolIds(Collection<String> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> ids = new HashSet<>();
        for (String sid : schoolIds) {
            if (sid != null && !sid.isBlank()) {
                ids.add(sid);
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<UniOfficialLink> rows = officialLinkMapper.selectList(
                    new LambdaQueryWrapper<UniOfficialLink>().in(UniOfficialLink::getSchoolId, ids));
            Map<String, UniOfficialLink> result = new HashMap<>(rows.size() * 2);
            for (UniOfficialLink row : rows) {
                if (row.getSchoolId() != null) {
                    result.put(row.getSchoolId(), row);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("批量加载官方报考资料入口失败: count={}", ids.size(), e);
            return Collections.emptyMap();
        }
    }
}
