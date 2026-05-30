package com.ordering.controller;

import com.ordering.repository.OrderRepository;
import com.ordering.service.FeishuSyncService;
import com.ordering.websocket.OrderWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 飞书 Bitable Webhook 接收端
 *
 * 使用方式：
 * 1. 在飞书多维表格 → 右上角 ... → 高级权限 → Webhook → 添加
 * 2. URL 填 http://<你的服务器>/api/webhook/bitable
 * 3. 勾选"记录新增"、"记录更新"触发
 *
 * 当 Bitable 中订单表有变更时，实时同步到 MySQL + WS 推前端
 */
@RestController
@RequestMapping("/api/webhook")
public class BitableWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BitableWebhookController.class);

    private final FeishuSyncService feishuSyncService;

    public BitableWebhookController(FeishuSyncService feishuSyncService) {
        this.feishuSyncService = feishuSyncService;
    }

    /**
     * Bitable Webhook 入口
     * Feishu 会 POST 变更事件到此端点
     */
    @PostMapping("/bitable")
    public ResponseEntity<Map<String, Object>> handleBitableWebhook(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce) {

        log.info("Bitable webhook 收到事件: {}", body != null ? body : "empty body");

        // 触发一次同步（拉取 Bitable 中所有订单的最新状态）
        feishuSyncService.syncOrdersFromFeishu();

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "msg", "ok"
        ));
    }

    /**
     * 一键手动同步
     */
    @PostMapping("/sync/now")
    public ResponseEntity<Map<String, Object>> syncNow() {
        log.info("手动触发同步");
        feishuSyncService.syncOrdersFromFeishu();
        return ResponseEntity.ok(Map.of(
                "code", 0,
                "msg", "同步完成"
        ));
    }
}
