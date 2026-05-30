package com.ordering.feishu;

import com.ordering.config.FeishuConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书多维表格集成层
 * 负责菜品表/订单表/桌号表的读写、Webhook 事件处理
 *
 * 使用飞书 Open API v2 (REST), 无需额外 SDK
 */
@Component
public class FeishuBitableClient {

    private static final Logger log = LoggerFactory.getLogger(FeishuBitableClient.class);

    private final FeishuConfig feishuConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    public FeishuBitableClient(FeishuConfig feishuConfig) {
        this.feishuConfig = feishuConfig;
    }

    private static final String BASE_URL = "https://open.feishu.cn/open-apis";

    // 缓存 tenant_access_token
    private volatile String tenantToken;
    private volatile long tokenExpireAt;

    @PostConstruct
    public void init() {
        log.info("飞书Bitable客户端初始化 (appId={})", feishuConfig.getAppId());
        log.info("  AppToken={}", feishuConfig.getBitable().getTableAppToken());
        log.info("  订单表ID={}", feishuConfig.getBitable().getOrderTableId());
        log.info("  菜品表ID={}", feishuConfig.getBitable().getMenuTableId());
        log.info("  桌号表ID={}", feishuConfig.getBitable().getTableTableId());
    }

    // ==================== Token 管理 ====================

    private synchronized String getTenantToken() {
        long now = System.currentTimeMillis();
        if (tenantToken != null && now < tokenExpireAt - 60000) {
            return tenantToken;
        }
        try {
            String secret = feishuConfig.getAppSecret();
            if (secret == null || "placeholder".equals(secret)) {
                log.warn("飞书 app-secret 未配置，跳过 API 调用");
                return null;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("app_id", feishuConfig.getAppId());
            body.put("app_secret", secret);

            var resp = restTemplate.postForEntity(
                    BASE_URL + "/auth/v3/tenant_access_token/internal",
                    body,
                    Map.class
            );
            Map<String, Object> result = resp.getBody();
            if (result != null && result.containsKey("tenant_access_token")) {
                tenantToken = (String) result.get("tenant_access_token");
                int expire = (int) result.getOrDefault("expire", 7200);
                tokenExpireAt = now + expire * 1000L;
                log.info("飞书 tenant_token 获取成功");
                return tenantToken;
            }
            log.error("飞书 token 获取失败: {}", result);
        } catch (Exception e) {
            log.error("飞书 token 获取异常", e);
        }
        return null;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = getTenantToken();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String bitableApi(String path) {
        return BASE_URL + "/bitable/v1/apps/"
                + feishuConfig.getBitable().getTableAppToken()
                + path;
    }

    // ==================== 菜品同步 ====================

    /**
     * 从飞书菜品表读取所有菜品
     */
    public void syncMenuFromBitable() {
        try {
            String url = bitableApi("/tables/" + feishuConfig.getBitable().getMenuTableId() + "/records");
            var exchange = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    Map.class
            );
            log.info("从飞书同步菜品: {}", exchange.getBody());
        } catch (Exception e) {
            log.error("从飞书同步菜品失败", e);
        }
    }

    // ==================== 订单同步 ====================

    /**
     * 同步订单到飞书订单表
     */
    public void syncOrderToBitable(String orderNo, String tableNo, String items, int totalPrice, String status) {
        try {
            String token = getTenantToken();
            if (token == null) return;

            String url = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId() + "/records");

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("订单号", orderNo);
            fields.put("桌号", tableNo);
            fields.put("菜品明细", items);
            fields.put("总价", totalPrice);
            fields.put("状态", status);
            fields.put("备注", "");

            Map<String, Object> body = new HashMap<>();
            body.put("fields", fields);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, authHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("飞书订单同步成功: {}", orderNo);
            } else {
                log.warn("飞书订单同步失败: {} {}", orderNo, resp.getBody());
            }
        } catch (Exception e) {
            log.error("飞书订单同步异常", e);
        }
    }

    /**
     * 更新订单状态到飞书
     */
    public void updateOrderStatus(String orderNo, String status) {
        try {
            String token = getTenantToken();
            if (token == null) return;

            // 先按订单号查找记录
            String findUrl = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId()
                    + "/records?page_size=20&filter=订单号=\" + orderNo + \"");
            var findResp = restTemplate.exchange(
                    findUrl, HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    Map.class
            );

            Map<String, Object> findData = findResp.getBody();
            if (findData == null) return;

            List<Map<String, Object>> items = (List<Map<String, Object>>)
                    ((Map<String, Object>) findData.getOrDefault("data", Collections.emptyMap()))
                            .getOrDefault("items", Collections.emptyList());

            if (items.isEmpty()) {
                log.warn("飞书未找到订单: {}", orderNo);
                return;
            }

            String recordId = (String) items.get(0).get("record_id");
            String updateUrl = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId()
                    + "/records/" + recordId);

            Map<String, Object> fields = new HashMap<>();
            fields.put("状态", status);
            Map<String, Object> body = new HashMap<>();
            body.put("fields", fields);

            restTemplate.exchange(updateUrl, HttpMethod.PUT, new HttpEntity<>(body, authHeaders()), Map.class);
            log.info("飞书订单状态更新成功: {} -> {}", orderNo, status);
        } catch (Exception e) {
            log.error("飞书订单状态更新异常", e);
        }
    }

    // ==================== 监听事件 ====================

    /**
     * 处理飞书 Webhook 事件（菜品表变更触发缓存刷新）
     */
    public void handleBitableChange(String tableId, String recordId) {
        log.info("飞书表格变更: table={}, record={}", tableId, recordId);
    }
}
