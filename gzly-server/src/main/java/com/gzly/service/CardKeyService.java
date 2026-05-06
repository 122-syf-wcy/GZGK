package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gzly.common.exception.BizException;
import com.gzly.entity.BizCardKey;
import com.gzly.entity.BizUser;
import com.gzly.mapper.BizCardKeyMapper;
import com.gzly.mapper.BizUserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 卡密激活、登录、用量管控服务。
 *
 * <ul>
 *   <li>{@link #generateBatch(int, String, int, int, String)} 批量发卡（运营接口）。</li>
 *   <li>{@link #activate(String, String, String)} 首次激活：核销卡密 + 设安全码 + 创建 BizUser。</li>
 *   <li>{@link #verifyLogin(String, String)} 校验「卡密 + 安全码」并返回绑定 user。</li>
 *   <li>{@link #consumePlan(Long)} 生成方案时原子 +1，超 max_plans 抛业务异常。</li>
 *   <li>{@link #revoke(Long, String)} 运营撤销，已绑定 user 不删除以保留追溯。</li>
 * </ul>
 *
 * 卡密格式：`AA12-BB34-CC56-DD78`（4 段 × 4 字母数字，共 19 位含分隔，明文长度始终 ≤ 40）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardKeyService {

    /** 卡密字符集：去除易混淆字符 0/O/I/1。 */
    private static final char[] CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int SEGMENT_LEN = 4;
    private static final int SEGMENT_COUNT = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String STATUS_UNUSED = "unused";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_REVOKED = "revoked";
    public static final String STATUS_EXPIRED = "expired";

    private final BizCardKeyMapper cardKeyMapper;
    private final BizUserMapper bizUserMapper;

    // ---------------------------------------------------------------------
    // 运营批量发卡
    // ---------------------------------------------------------------------

    /**
     * 批量生成卡密。同批 batchNo 便于回收/筛选。
     *
     * @param count     张数；服务端兜底范围 1 ~ 200，避免一次性发太多
     * @param batchNo   批次号；空则用当前时间戳
     * @param maxPlans  每张卡可生成方案上限；&le; 0 时使用 5
     * @param validDays 有效天数；&le; 0 时使用 90
     * @param note      运营备注
     */
    @Transactional
    public List<BizCardKey> generateBatch(int count, String batchNo, int maxPlans, int validDays, String note) {
        if (count <= 0) throw new BizException("发卡数量必须大于 0");
        if (count > 200) throw new BizException("单次发卡数量不能超过 200 张");
        int finalMax = maxPlans > 0 ? maxPlans : 5;
        int finalValid = validDays > 0 ? validDays : 90;
        String finalBatch = (batchNo == null || batchNo.isBlank())
                ? "BATCH-" + System.currentTimeMillis()
                : batchNo.trim();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(finalValid);

        List<BizCardKey> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BizCardKey row = new BizCardKey();
            row.setCardKey(generateUniqueCardKey());
            row.setBatchNo(finalBatch);
            row.setStatus(STATUS_UNUSED);
            row.setMaxPlans(finalMax);
            row.setUsedPlans(0);
            row.setExpiresAt(expiresAt);
            row.setNote(note == null ? null : note.trim());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            cardKeyMapper.insert(row);
            result.add(row);
        }
        log.info("[card-key] batch generated: count={}, batchNo={}, maxPlans={}, validDays={}",
                count, finalBatch, finalMax, finalValid);
        return result;
    }

    // ---------------------------------------------------------------------
    // 用户首次激活：卡密 + 安全码 + 昵称 → 创建 BizUser
    // ---------------------------------------------------------------------

    /**
     * 激活卡密：仅当 status=unused 才能首次激活。
     * <ul>
     *   <li>校验卡密存在、未过期、未撤销</li>
     *   <li>创建 BizUser（identifier 用卡密前 8 位 + 时间戳，避免冲突）</li>
     *   <li>BCrypt 哈希安全码、回写卡密 user_id / status=active / activated_at</li>
     * </ul>
     */
    @Transactional
    public BizUser activate(String cardKey, String secretCode, String nickname) {
        BizCardKey row = requireCardKeyForActivate(cardKey, secretCode);
        if (!STATUS_UNUSED.equals(row.getStatus())) {
            // 用户提示：让其使用登录入口
            throw new BizException("该卡密已被激活，请直接使用「卡密 + 安全码」登录");
        }
        LocalDateTime now = LocalDateTime.now();

        BizUser user = new BizUser();
        user.setIdentifier(buildIdentifier(row.getCardKey()));
        user.setCardKeyId(row.getId());
        user.setNickname(nickname == null ? "" : nickname.trim());
        user.setRemainCount(row.getMaxPlans());
        user.setTotalUsed(0);
        user.setLastActiveTime(now);
        user.setLastLoginAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        bizUserMapper.insert(user);

        cardKeyMapper.update(null, new UpdateWrapper<BizCardKey>()
                .eq("id", row.getId())
                .set("status", STATUS_ACTIVE)
                .set("user_id", user.getId())
                .set("secret_code_hash", BCrypt.hashpw(secretCode, BCrypt.gensalt(10)))
                .set("activated_at", now)
                .set("updated_at", now));

        log.info("[card-key] activated: cardKey={}, userId={}, nickname={}", maskCardKey(cardKey), user.getId(), user.getNickname());
        return user;
    }

    // ---------------------------------------------------------------------
    // 登录验证
    // ---------------------------------------------------------------------

    /**
     * 校验「卡密 + 安全码」。成功返回当前绑定的 BizUser，失败抛 BizException。
     */
    public BizUser verifyLogin(String cardKey, String secretCode) {
        BizCardKey row = requireCardKeyForLogin(cardKey, secretCode);
        if (!STATUS_ACTIVE.equals(row.getStatus())) {
            throw new BizException("该卡密尚未激活，请先完成首次激活");
        }
        if (row.getUserId() == null) {
            throw new BizException("卡密绑定异常，请联系运营人员");
        }
        if (!BCrypt.checkpw(secretCode, row.getSecretCodeHash())) {
            throw new BizException("卡密或安全码错误");
        }
        BizUser user = bizUserMapper.selectById(row.getUserId());
        if (user == null) {
            throw new BizException("卡密绑定的账号不存在，请联系运营人员");
        }
        LocalDateTime now = LocalDateTime.now();
        bizUserMapper.update(null, new UpdateWrapper<BizUser>()
                .eq("id", user.getId())
                .set("last_login_at", now)
                .set("last_active_time", now)
                .set("updated_at", now));
        return user;
    }

    // ---------------------------------------------------------------------
    // 用量管控
    // ---------------------------------------------------------------------

    /**
     * 生成方案前调用：原子 +1 并校验上限。返回当前累计已用次数。
     * 超过 max_plans 抛 BizException。
     */
    @Transactional
    public int consumePlan(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException("请先完成卡密激活");
        }
        BizCardKey row = cardKeyMapper.selectOne(new QueryWrapper<BizCardKey>()
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BizException("当前账号未绑定有效卡密");
        }
        if (!STATUS_ACTIVE.equals(row.getStatus())) {
            throw new BizException("该卡密已被" + statusLabel(row.getStatus()) + "，无法继续生成");
        }
        if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(LocalDateTime.now())) {
            cardKeyMapper.update(null, new UpdateWrapper<BizCardKey>()
                    .eq("id", row.getId())
                    .set("status", STATUS_EXPIRED));
            throw new BizException("当前卡密已过期，请联系运营人员申请新卡密");
        }
        int used = row.getUsedPlans() == null ? 0 : row.getUsedPlans();
        int max = row.getMaxPlans() == null ? 5 : row.getMaxPlans();
        if (used >= max) {
            throw new BizException("当前卡密已用完 " + max + " 次生成额度，请联系运营人员升级");
        }
        cardKeyMapper.update(null, new UpdateWrapper<BizCardKey>()
                .eq("id", row.getId())
                .setSql("used_plans = used_plans + 1")
                .set("updated_at", LocalDateTime.now()));
        return used + 1;
    }

    public BizCardKey findByUserId(Long userId) {
        if (userId == null) return null;
        return cardKeyMapper.selectOne(new QueryWrapper<BizCardKey>()
                .eq("user_id", userId)
                .last("LIMIT 1"));
    }

    // ---------------------------------------------------------------------
    // 撤销
    // ---------------------------------------------------------------------

    @Transactional
    public BizCardKey revoke(Long id, String reason) {
        BizCardKey row = cardKeyMapper.selectById(id);
        if (row == null) throw new BizException("卡密不存在");
        if (STATUS_REVOKED.equals(row.getStatus())) {
            return row;
        }
        cardKeyMapper.update(null, new UpdateWrapper<BizCardKey>()
                .eq("id", id)
                .set("status", STATUS_REVOKED)
                .set("revoked_at", LocalDateTime.now())
                .set("revoke_reason", reason == null ? "" : reason.trim())
                .set("updated_at", LocalDateTime.now()));
        return cardKeyMapper.selectById(id);
    }

    // ---------------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------------

    private BizCardKey requireCardKeyForActivate(String cardKey, String secretCode) {
        if (cardKey == null || cardKey.isBlank()) throw new BizException("请输入卡密");
        if (secretCode == null || secretCode.length() < 4 || secretCode.length() > 8) {
            throw new BizException("安全码长度必须在 4 ~ 8 位之间");
        }
        BizCardKey row = cardKeyMapper.selectOne(new QueryWrapper<BizCardKey>()
                .eq("card_key", cardKey.trim())
                .last("LIMIT 1"));
        if (row == null) throw new BizException("卡密不存在或已被回收");
        if (STATUS_REVOKED.equals(row.getStatus())) throw new BizException("该卡密已被运营撤销，请联系运营人员");
        if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException("该卡密已过期，请联系运营人员申请新卡密");
        }
        return row;
    }

    private BizCardKey requireCardKeyForLogin(String cardKey, String secretCode) {
        if (cardKey == null || cardKey.isBlank()) throw new BizException("请输入卡密");
        if (secretCode == null || secretCode.isBlank()) throw new BizException("请输入安全码");
        BizCardKey row = cardKeyMapper.selectOne(new QueryWrapper<BizCardKey>()
                .eq("card_key", cardKey.trim())
                .last("LIMIT 1"));
        if (row == null) throw new BizException("卡密或安全码错误");
        if (STATUS_REVOKED.equals(row.getStatus())) throw new BizException("该卡密已被撤销，请联系运营人员");
        if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException("该卡密已过期，请联系运营人员申请新卡密");
        }
        return row;
    }

    private String generateUniqueCardKey() {
        // 极小概率冲突时重试 5 次
        for (int retry = 0; retry < 5; retry++) {
            String candidate = randomCardKey();
            BizCardKey existing = cardKeyMapper.selectOne(new QueryWrapper<BizCardKey>()
                    .eq("card_key", candidate)
                    .last("LIMIT 1"));
            if (existing == null) return candidate;
        }
        throw new BizException("卡密生成冲突，请稍后重试");
    }

    private String randomCardKey() {
        StringBuilder sb = new StringBuilder(SEGMENT_COUNT * (SEGMENT_LEN + 1));
        for (int s = 0; s < SEGMENT_COUNT; s++) {
            if (s > 0) sb.append('-');
            for (int i = 0; i < SEGMENT_LEN; i++) {
                sb.append(CHARSET[RANDOM.nextInt(CHARSET.length)]);
            }
        }
        return sb.toString();
    }

    private String buildIdentifier(String cardKey) {
        String prefix = cardKey == null ? "GZLY" : cardKey.replace("-", "").substring(0, Math.min(8, cardKey.replace("-", "").length()));
        return "card:" + prefix.toUpperCase() + ":" + System.currentTimeMillis();
    }

    private String maskCardKey(String cardKey) {
        if (cardKey == null) return "";
        if (cardKey.length() <= 4) return "***";
        return cardKey.substring(0, 4) + "-***-***-" + cardKey.substring(cardKey.length() - 4);
    }

    private String statusLabel(String status) {
        if (STATUS_REVOKED.equals(status)) return "撤销";
        if (STATUS_EXPIRED.equals(status)) return "标记为过期";
        return "停用";
    }

    /** 暴露给运营接口的公开摘要：不含 secretCodeHash。 */
    @Data
    public static class CardKeyView {
        private Long id;
        private String cardKey;
        private String batchNo;
        private String status;
        private Long userId;
        private Integer maxPlans;
        private Integer usedPlans;
        private LocalDateTime expiresAt;
        private LocalDateTime activatedAt;
        private LocalDateTime revokedAt;
        private String revokeReason;
        private String note;
        private LocalDateTime createdAt;
        public static CardKeyView from(BizCardKey row) {
            CardKeyView view = new CardKeyView();
            view.id = row.getId();
            view.cardKey = row.getCardKey();
            view.batchNo = row.getBatchNo();
            view.status = row.getStatus();
            view.userId = row.getUserId();
            view.maxPlans = row.getMaxPlans();
            view.usedPlans = row.getUsedPlans();
            view.expiresAt = row.getExpiresAt();
            view.activatedAt = row.getActivatedAt();
            view.revokedAt = row.getRevokedAt();
            view.revokeReason = row.getRevokeReason();
            view.note = row.getNote();
            view.createdAt = row.getCreatedAt();
            return view;
        }
    }
}
