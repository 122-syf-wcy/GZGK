package com.gzly.controller;

import com.gzly.common.Result;
import com.gzly.common.exception.BizException;
import com.gzly.service.CardKeyService.CardKeyView;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运营卡密管理（仅 admin 可访问，挂载于 /admin/card-keys 自动走 AuthInterceptor）。
 */
@RestController
@RequestMapping("/admin/card-keys")
@RequiredArgsConstructor
public class CardKeyAdminController {

    private static final String CARD_KEY_GONE_MESSAGE = "卡密功能已下线，请使用安全码访问志愿方案。";

    @PostMapping("/batch-generate")
    public Result<Map<String, Object>> batchGenerate(@RequestBody BatchGenerateRequest req) {
        if (req == null) throw new BizException("参数不能为空");
        throw new BizException(410, CARD_KEY_GONE_MESSAGE);
    }

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String batchNo,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        throw new BizException(410, CARD_KEY_GONE_MESSAGE);
    }

    @PostMapping("/{id}/revoke")
    public Result<CardKeyView> revoke(@PathVariable Long id, @RequestBody(required = false) RevokeRequest req) {
        throw new BizException(410, CARD_KEY_GONE_MESSAGE);
    }

    @Data
    public static class BatchGenerateRequest {
        private int count;
        private String batchNo;
        private int maxPlans;
        private int validDays;
        private String note;
    }

    @Data
    public static class RevokeRequest {
        private String reason;
    }
}
