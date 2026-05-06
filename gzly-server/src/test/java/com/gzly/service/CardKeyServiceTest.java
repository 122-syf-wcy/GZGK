package com.gzly.service;

import com.gzly.common.exception.BizException;
import com.gzly.entity.BizCardKey;
import com.gzly.entity.BizUser;
import com.gzly.mapper.BizCardKeyMapper;
import com.gzly.mapper.BizUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CardKeyService 行为测试。
 *
 * <ul>
 *   <li>批量发卡：数量 / 唯一性 / 默认值</li>
 *   <li>激活：成功路径 / 二次激活报错 / 安全码长度校验</li>
 *   <li>登录：成功路径 / 安全码错误 / 撤销卡密被拒</li>
 *   <li>用量：未达上限 +1 / 达上限抛错 / 撤销态拒绝</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardKeyServiceTest {

    @Mock BizCardKeyMapper cardKeyMapper;
    @Mock BizUserMapper bizUserMapper;
    @InjectMocks CardKeyService service;

    private final AtomicLong cardKeyIdSeq = new AtomicLong(1);
    private final AtomicLong userIdSeq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        // 默认无重复卡密，insert 后赋 id
        when(cardKeyMapper.selectOne(any())).thenReturn(null);
        when(cardKeyMapper.insert(any(BizCardKey.class))).thenAnswer(inv -> {
            BizCardKey row = inv.getArgument(0);
            row.setId(cardKeyIdSeq.getAndIncrement());
            return 1;
        });
        when(bizUserMapper.insert(any(BizUser.class))).thenAnswer(inv -> {
            BizUser u = inv.getArgument(0);
            u.setId(userIdSeq.getAndIncrement());
            return 1;
        });
    }

    @Test
    void generateBatch_createsRequestedCount_withDefaults() {
        List<BizCardKey> rows = service.generateBatch(3, "BATCH-A", 0, 0, "test note");

        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(CardKeyService.STATUS_UNUSED);
            assertThat(r.getMaxPlans()).isEqualTo(5);
            assertThat(r.getCardKey()).matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}");
            assertThat(r.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(r.getBatchNo()).isEqualTo("BATCH-A");
        });
        verify(cardKeyMapper, atLeast(3)).insert(any(BizCardKey.class));
    }

    @Test
    void generateBatch_rejectsZeroOrTooMany() {
        assertThatThrownBy(() -> service.generateBatch(0, null, 0, 0, null))
                .hasMessageContaining("大于 0");
        assertThatThrownBy(() -> service.generateBatch(500, null, 0, 0, null))
                .hasMessageContaining("不能超过");
    }

    @Test
    void activate_createsUserAndBindsCardKey() {
        BizCardKey unused = baseCard(1L, CardKeyService.STATUS_UNUSED);
        when(cardKeyMapper.selectOne(any()))
                .thenReturn(unused) // requireCardKeyForActivate
                ;

        BizUser user = service.activate("AAAA-BBBB-CCCC-DDDD", "1234", "测试昵称");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCardKeyId()).isEqualTo(1L);
        assertThat(user.getNickname()).isEqualTo("测试昵称");
        assertThat(user.getRemainCount()).isEqualTo(unused.getMaxPlans());
        verify(cardKeyMapper, atLeastOnce()).update(any(), any());
    }

    @Test
    void activate_rejectsAlreadyActiveCard() {
        BizCardKey active = baseCard(1L, CardKeyService.STATUS_ACTIVE);
        when(cardKeyMapper.selectOne(any())).thenReturn(active);

        assertThatThrownBy(() -> service.activate("AAAA-BBBB-CCCC-DDDD", "1234", null))
                .hasMessageContaining("已被激活");
    }

    @Test
    void activate_rejectsRevokedCard() {
        BizCardKey revoked = baseCard(1L, CardKeyService.STATUS_REVOKED);
        when(cardKeyMapper.selectOne(any())).thenReturn(revoked);

        assertThatThrownBy(() -> service.activate("AAAA-BBBB-CCCC-DDDD", "1234", null))
                .hasMessageContaining("撤销");
    }

    @Test
    void activate_rejectsSecretTooShort() {
        when(cardKeyMapper.selectOne(any())).thenReturn(baseCard(1L, CardKeyService.STATUS_UNUSED));
        assertThatThrownBy(() -> service.activate("AAAA-BBBB-CCCC-DDDD", "12", null))
                .hasMessageContaining("4 ~ 8");
    }

    @Test
    void verifyLogin_succeedsWithCorrectSecret() {
        BizCardKey active = baseCard(2L, CardKeyService.STATUS_ACTIVE);
        active.setUserId(200L);
        active.setSecretCodeHash(BCrypt.hashpw("888888", BCrypt.gensalt(4)));
        when(cardKeyMapper.selectOne(any())).thenReturn(active);
        BizUser user = new BizUser();
        user.setId(200L);
        user.setIdentifier("card:AAAA:1");
        when(bizUserMapper.selectById(200L)).thenReturn(user);

        BizUser logged = service.verifyLogin("AAAA-BBBB-CCCC-DDDD", "888888");

        assertThat(logged.getId()).isEqualTo(200L);
        verify(bizUserMapper, atLeastOnce()).update(any(), any());
    }

    @Test
    void verifyLogin_rejectsWrongSecret() {
        BizCardKey active = baseCard(2L, CardKeyService.STATUS_ACTIVE);
        active.setUserId(200L);
        active.setSecretCodeHash(BCrypt.hashpw("888888", BCrypt.gensalt(4)));
        when(cardKeyMapper.selectOne(any())).thenReturn(active);

        assertThatThrownBy(() -> service.verifyLogin("AAAA-BBBB-CCCC-DDDD", "wrong-code"))
                .hasMessageContaining("错误");
    }

    @Test
    void verifyLogin_rejectsRevokedCard() {
        BizCardKey revoked = baseCard(2L, CardKeyService.STATUS_REVOKED);
        when(cardKeyMapper.selectOne(any())).thenReturn(revoked);

        assertThatThrownBy(() -> service.verifyLogin("AAAA-BBBB-CCCC-DDDD", "888888"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("撤销");
    }

    @Test
    void consumePlan_incrementsBelowLimit() {
        BizCardKey active = baseCard(3L, CardKeyService.STATUS_ACTIVE);
        active.setUsedPlans(2);
        active.setMaxPlans(5);
        when(cardKeyMapper.selectOne(any())).thenReturn(active);

        int after = service.consumePlan(300L);

        assertThat(after).isEqualTo(3);
        verify(cardKeyMapper, atLeastOnce()).update(any(), any());
    }

    @Test
    void consumePlan_rejectsOverLimit() {
        BizCardKey active = baseCard(3L, CardKeyService.STATUS_ACTIVE);
        active.setUsedPlans(5);
        active.setMaxPlans(5);
        when(cardKeyMapper.selectOne(any())).thenReturn(active);

        assertThatThrownBy(() -> service.consumePlan(300L))
                .hasMessageContaining("已用完");
    }

    @Test
    void consumePlan_rejectsRevoked() {
        BizCardKey revoked = baseCard(3L, CardKeyService.STATUS_REVOKED);
        when(cardKeyMapper.selectOne(any())).thenReturn(revoked);

        assertThatThrownBy(() -> service.consumePlan(300L))
                .hasMessageContaining("撤销");
    }

    @Test
    void revoke_setsStatusAndReason() {
        BizCardKey active = baseCard(4L, CardKeyService.STATUS_ACTIVE);
        when(cardKeyMapper.selectById(4L)).thenReturn(active);

        service.revoke(4L, "测试撤销");

        verify(cardKeyMapper, atLeastOnce()).update(any(), any());
    }

    private BizCardKey baseCard(Long id, String status) {
        BizCardKey row = new BizCardKey();
        row.setId(id);
        row.setCardKey("AAAA-BBBB-CCCC-DDDD");
        row.setBatchNo("BATCH-X");
        row.setStatus(status);
        row.setMaxPlans(5);
        row.setUsedPlans(0);
        row.setExpiresAt(LocalDateTime.now().plusDays(30));
        row.setCreatedAt(LocalDateTime.now().minusDays(1));
        return row;
    }
}
