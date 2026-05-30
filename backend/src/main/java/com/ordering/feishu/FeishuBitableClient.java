package com.ordering.feishu;

import com.ordering.config.FeishuConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeishuBitableClient {

    private static final Logger log = LoggerFactory.getLogger(FeishuBitableClient.class);
    private static final String BASE_URL = "https://open.feishu.cn/open-apis";

    private final FeishuConfig feishuConfig;
    private final RestTemplate restTemplate;

    private volatile String tenantToken;
    private volatile long tokenExpireAt;

    public FeishuBitableClient(FeishuConfig feishuConfig) {
        this.feishuConfig = feishuConfig;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        log.info("飞书Bitable客户端初始化 (appId={})", feishuConfig.getAppId());
        String token = getTenantToken();
        if (token == null) {
            log.warn("飞书 token 获取失败，跳过 Bitable 初始化");
            return;
        }
        // 测试写权限：尝试创建一条临时记录，能通则用已有表格
        boolean writeOk = false;
        try {
            Map<String, Object> testRecord = new HashMap<>();
            testRecord.put("fields", Map.of("订单号", "_probe_" + System.currentTimeMillis()));
            String testUrl = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId() + "/records");
            restTemplate.exchange(testUrl, HttpMethod.POST, new HttpEntity<>(testRecord, authHeaders()), Map.class);
            writeOk = true;
        } catch (Exception ignored) {}

        if (writeOk) {
            log.info("飞书Bitable写入权限正常，使用已有表格");
        } else {
            log.warn("已有Bitable写入权限不足，自动创建新表格...");
            provisionTables();
        }
    }

    /**
     * 使用 app 身份创建 Bitable 表格
     */
    private void provisionTables() {
        try {
            // 创建多维表格 App
            Map<String, Object> createReq = new HashMap<>();
            createReq.put("name", "点餐系统管理后台");
            var createResp = restTemplate.exchange(
                    BASE_URL + "/bitable/v1/apps",
                    HttpMethod.POST,
                    new HttpEntity<>(createReq, authHeaders()),
                    Map.class
            );
            Map<String, Object> dataObj = (Map<String, Object>) createResp.getBody().get("data");
            Map<String, Object> appData = (Map<String, Object>) dataObj.get("app");
            if (appData == null) { log.error("创建Bitable失败: data={}", dataObj); return; }

            String appToken = (String) appData.get("app_token");
            log.info("创建Bitable成功: app_token={}", appToken);

            // 更新内存中的配置
            feishuConfig.getBitable().setTableAppToken(appToken);

            // 使用创建时返回的默认表作为订单表
            String defaultTableId = (String) appData.get("default_table_id");

            // 重命名默认表为订单表
            Map<String, Object> rename = new HashMap<>();
            rename.put("name", "订单表");
            restTemplate.exchange(
                    BASE_URL + "/bitable/v1/apps/" + appToken + "/tables/" + defaultTableId,
                    HttpMethod.PUT, new HttpEntity<>(rename, authHeaders()), Map.class
            );
            feishuConfig.getBitable().setOrderTableId(defaultTableId);

            // 添加订单表字段
            addField(appToken, defaultTableId, "桌号", 1, null);
            addField(appToken, defaultTableId, "菜品明细", 1, null);
            addField(appToken, defaultTableId, "总价", 2, null);
            addField(appToken, defaultTableId, "状态", 3,
                    Map.of("options", List.of(
                            Map.of("name", "CREATED"), Map.of("name", "PREPARING"),
                            Map.of("name", "PENDING_CONFIRM"), Map.of("name", "CONFIRMED"),
                            Map.of("name", "CLOSED"), Map.of("name", "VERIFIED"))));
            addField(appToken, defaultTableId, "创建时间", 5, null);

            // 创建菜品表
            String menuTableId = createTable(appToken, "菜品表");
            feishuConfig.getBitable().setMenuTableId(menuTableId);
            addField(appToken, menuTableId, "菜品ID", 2, null);
            addField(appToken, menuTableId, "分类", 3,
                    Map.of("options", List.of(
                            Map.of("name", "招牌推荐"), Map.of("name", "主食"),
                            Map.of("name", "小食"), Map.of("name", "饮品"))));
            addField(appToken, menuTableId, "价格", 2, null);
            addField(appToken, menuTableId, "库存", 2, null);

            // 创建桌号表
            String tableTableId = createTable(appToken, "桌号表");
            feishuConfig.getBitable().setTableTableId(tableTableId);

            log.info("Bitable自动创建完成: appToken={}, 订单={}, 菜品={}, 桌号={}",
                    appToken, defaultTableId, menuTableId, tableTableId);
        } catch (Exception e) {
            log.error("自动创建Bitable失败", e);
        }
    }

    private String createTable(String appToken, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        var resp = restTemplate.exchange(
                BASE_URL + "/bitable/v1/apps/" + appToken + "/tables",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class
        );
        return (String) ((Map) ((Map) resp.getBody().get("data")).get("table")).get("table_id");
    }

    private void addField(String appToken, String tableId, String name, int type, Object property) {
        Map<String, Object> body = new HashMap<>();
        body.put("field_name", name);
        body.put("type", type);
        if (property != null) body.put("property", property);
        restTemplate.exchange(
                BASE_URL + "/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/fields",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class
        );
    }

    // ==================== Token 管理 ====================

    private synchronized String getTenantToken() {
        long now = System.currentTimeMillis();
        if (tenantToken != null && now < tokenExpireAt - 60000) return tenantToken;
        try {
            String secret = feishuConfig.getAppSecret();
            if (secret == null || "placeholder".equals(secret)) {
                log.warn("飞书 app-secret 未配置");
                return null;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("app_id", feishuConfig.getAppId());
            body.put("app_secret", secret);
            var resp = restTemplate.postForEntity(BASE_URL + "/auth/v3/tenant_access_token/internal", body, Map.class);
            Map<String, Object> result = resp.getBody();
            if (result != null && result.containsKey("tenant_access_token")) {
                tenantToken = (String) result.get("tenant_access_token");
                tokenExpireAt = now + ((int) result.getOrDefault("expire", 7200)) * 1000L;
                log.info("飞书 token 获取成功");
                return tenantToken;
            }
            log.error("飞书 token 获取失败: {}", result);
        } catch (Exception e) {
            log.warn("飞书 token 获取异常: {}", e.getMessage());
        }
        return null;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String t = getTenantToken();
        if (t != null) h.setBearerAuth(t);
        return h;
    }

    private String bitableApi(String path) {
        return BASE_URL + "/bitable/v1/apps/" + feishuConfig.getBitable().getTableAppToken() + path;
    }

    // ==================== 订单同步（异步，不阻塞主流程） ====================

    public void syncOrderToBitable(String orderNo, String tableNo, String items, int totalPrice, String status) {
        CompletableFuture.runAsync(() -> {
            try {
                if (getTenantToken() == null) return;
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("订单号", orderNo);
                fields.put("桌号", tableNo);
                fields.put("菜品明细", items);
                fields.put("总价", totalPrice);
                fields.put("状态", status);
                fields.put("备注", "");
                Map<String, Object> body = new HashMap<>();
                body.put("fields", fields);
                String url = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId() + "/records");
                var resp = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    log.info("飞书订单同步成功: {}", orderNo);
                }
            } catch (Exception e) {
                log.warn("飞书订单同步失败: {} - {}", orderNo, e.getMessage());
            }
        });
    }

    public void updateOrderStatus(String orderNo, String status) {
        CompletableFuture.runAsync(() -> {
            try {
                if (getTenantToken() == null) return;
                String listUrl = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId() + "/records?page_size=50");
                var listResp = restTemplate.exchange(listUrl, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
                Map<String, Object> data = (Map<String, Object>) listResp.getBody().get("data");
                if (data == null) return;
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
                String recordId = null;
                for (Map<String, Object> item : items) {
                    Map<String, Object> f = (Map<String, Object>) item.get("fields");
                    if (f != null && orderNo.equals(f.get("订单号"))) {
                        recordId = (String) item.get("record_id");
                        break;
                    }
                }
                if (recordId == null) { log.warn("飞书未找到订单: {}", orderNo); return; }
                Map<String, Object> fields = new HashMap<>();
                fields.put("状态", status);
                Map<String, Object> body = new HashMap<>();
                body.put("fields", fields);
                String updUrl = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId() + "/records/" + recordId);
                restTemplate.exchange(updUrl, HttpMethod.PUT, new HttpEntity<>(body, authHeaders()), Map.class);
                log.info("飞书订单状态更新成功: {} -> {}", orderNo, status);
            } catch (Exception e) {
                log.warn("飞书订单状态更新异常: {} - {}", orderNo, e.getMessage());
            }
        });
    }

    // ==================== 轮询/读取 ====================

    public Map<String, String> listOrdersFromBitable() {
        Map<String, String> result = new HashMap<>();
        try {
            if (getTenantToken() == null) return result;
            String url = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId() + "/records?page_size=50");
            var resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
            Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
            if (data == null) return result;
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
            for (Map<String, Object> item : items) {
                Map<String, Object> f = (Map<String, Object>) item.get("fields");
                if (f == null) continue;
                String orderNo = (String) f.get("订单号");
                String status = (String) f.get("状态");
                if (orderNo != null && status != null) result.put(orderNo, status);
            }
        } catch (Exception e) {
            log.debug("读取飞书订单失败: {}", e.getMessage());
        }
        return result;
    }

    public void syncMenuFromBitable() {
        CompletableFuture.runAsync(() -> {
            try {
                if (getTenantToken() == null) return;
                String url = bitableApi("/tables/" + feishuConfig.getBitable().getMenuTableId() + "/records");
                var resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
                log.info("从飞书同步菜品: {}", resp.getBody());
            } catch (Exception e) {
                log.warn("从飞书同步菜品失败: {}", e.getMessage());
            }
        });
    }

    public void handleBitableChange(String tableId, String recordId) {
        log.info("飞书表格变更: table={}, record={}", tableId, recordId);
    }
}
