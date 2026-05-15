package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzly.entity.SafetyCodeIdentity;
import com.gzly.mapper.SafetyCodeIdentityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 安全码匿名身份服务：明文安全码从不入库，仅存放 SHA-256 指纹用于按当前码定位历史方案。
 *
 * <ul>
 *   <li>{@link #ensureIdentity(String)} 在生成方案前调用：找不到则创建，找到则刷新 last_seen_at 并 +1 plan_count</li>
 *   <li>{@link #findByCode(String)} 仅查询，不变更</li>
 *   <li>{@link #createIdentityForNewCode(String)} 主动为新生成的安全码创建身份，专门给 /safety-code/create 用</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SafetyCodeIdentityService {

    private final SafetyCodeIdentityMapper identityMapper;
    private final SafetyCodeService safetyCodeService;

    public SafetyCodeIdentity findByCode(String code) {
        String fingerprint = fingerprintOf(code);
        if (fingerprint.isEmpty()) {
            return null;
        }
        return identityMapper.selectOne(new QueryWrapper<SafetyCodeIdentity>()
                .eq("safety_code_hash", fingerprint)
                .last("LIMIT 1"));
    }

    public SafetyCodeIdentity createIdentityForNewCode(String code) {
        String fingerprint = fingerprintOf(code);
        if (fingerprint.isEmpty()) {
            return null;
        }
        SafetyCodeIdentity existing = identityMapper.selectOne(new QueryWrapper<SafetyCodeIdentity>()
                .eq("safety_code_hash", fingerprint).last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        SafetyCodeIdentity entity = new SafetyCodeIdentity();
        entity.setSafetyCodeHash(fingerprint);
        entity.setSafetyCodeVersion("v1");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setLastSeenAt(entity.getCreatedAt());
        entity.setPlanCount(0);
        entity.setEnabled(1);
        try {
            identityMapper.insert(entity);
        } catch (DuplicateKeyException dup) {
            return identityMapper.selectOne(new QueryWrapper<SafetyCodeIdentity>()
                    .eq("safety_code_hash", fingerprint).last("LIMIT 1"));
        }
        return entity;
    }

    /**
     * 在生成方案时调用：为新码创建身份，已有身份则更新 last_seen_at + plan_count。返回 fingerprint，便于上层把它写到 plan 上。
     */
    public String ensureIdentity(String code) {
        String fingerprint = fingerprintOf(code);
        if (fingerprint.isEmpty()) {
            return "";
        }
        SafetyCodeIdentity entity = identityMapper.selectOne(new QueryWrapper<SafetyCodeIdentity>()
                .eq("safety_code_hash", fingerprint).last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            entity = new SafetyCodeIdentity();
            entity.setSafetyCodeHash(fingerprint);
            entity.setSafetyCodeVersion("v1");
            entity.setCreatedAt(now);
            entity.setLastSeenAt(now);
            entity.setPlanCount(1);
            entity.setEnabled(1);
            try {
                identityMapper.insert(entity);
            } catch (DuplicateKeyException dup) {
                return fingerprint;
            }
            return fingerprint;
        }
        entity.setLastSeenAt(now);
        entity.setPlanCount((entity.getPlanCount() == null ? 0 : entity.getPlanCount()) + 1);
        identityMapper.updateById(entity);
        return fingerprint;
    }

    public String fingerprintOf(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return safetyCodeService.fingerprint(code);
        } catch (Exception e) {
            return "";
        }
    }
}
