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
        log.info("飞书Bitable就绪 (app_token={})", feishuConfig.getBitable().getTableAppToken());
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

            // 重命名默认主字段 "文本" → "订单号"
            renameDefaultPrimaryField(appToken, defaultTableId);

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

    /** 检查 Feishu API 响应体中的 code 字段，0=成功 */
    private boolean isApiSuccess(Map<String, Object> body) {
        if (body == null) return false;
        Object code = body.get("code");
        // code 可能是 Integer 或 Long
        return code != null && (code.equals(0) || code.equals(0L));
    }

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
                Map<String, Object> respBody = resp.getBody();
                if (resp.getStatusCode().is2xxSuccessful() && isApiSuccess(respBody)) {
                    log.info("飞书订单同步成功: {}", orderNo);
                } else {
                    log.warn("飞书订单同步API返回异常: {} code={} msg={}", orderNo,
                            respBody != null ? respBody.get("code") : "N/A",
                            respBody != null ? respBody.get("msg") : "N/A");
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

    /** 订单快照：从 Bitable 读取的关键字段 */
    public static class OrderSnapshot {
        public String orderNo;
        public String tableNo;
        public String items;
        public Integer totalPrice;
        public String status;
        public String note;
    }

    /** 从 Bitable 拉取所有订单（含完整字段），用于双向同步 */
    public List<OrderSnapshot> listOrdersFromBitable() {
        List<OrderSnapshot> result = new ArrayList<>();
        try {
            if (getTenantToken() == null) return result;
            String url = bitableApi("/tables/" + feishuConfig.getBitable().getOrderTableId() + "/records?page_size=100");
            var resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
            Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
            if (data == null) return result;
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
            for (Map<String, Object> item : items) {
                Map<String, Object> f = (Map<String, Object>) item.get("fields");
                if (f == null) continue;
                OrderSnapshot s = new OrderSnapshot();
                s.orderNo   = asString(f.get("订单号"));
                s.tableNo   = asString(f.get("桌号"));
                s.items     = asString(f.get("菜品明细"));
                s.totalPrice = asInt(f.get("总价"));
                s.status    = asString(f.get("状态"));
                s.note      = asString(f.get("备注"));
                if (s.orderNo != null && s.status != null) {
                    result.add(s);
                }
            }
        } catch (Exception e) {
            log.debug("读取飞书订单失败: {}", e.getMessage());
        }
        return result;
    }

    /** Bitable 文本字段可能是 String 或 List<{text:...}> */
    private String asString(Object val) {
        if (val == null) return null;
        if (val instanceof String s) return s;
        if (val instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> m) {
                Object t = m.get("text");
                return t != null ? t.toString() : null;
            }
            return first.toString();
        }
        return val.toString();
    }

    private Integer asInt(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return null; }
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

    /** 重命名默认主字段 "文本" → "订单号"，供 provisionTables 调用 */
    private void renameDefaultPrimaryField(String appToken, String tableId) {
        try {
            // 查出当前字段列表，找到主字段
            String listUrl = BASE_URL + "/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/fields";
            var listResp = restTemplate.exchange(listUrl, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
            Map<String, Object> data = (Map<String, Object>) listResp.getBody().get("data");
            if (data == null) return;
            List<Map<String, Object>> fields = (List<Map<String, Object>>) data.get("items");
            for (Map<String, Object> f : fields) {
                if (Boolean.TRUE.equals(f.get("is_primary"))) {
                    String fid = (String) f.get("field_id");
                    String fname = (String) f.get("field_name");
                    if (!"订单号".equals(fname)) {
                        Map<String, Object> renameBody = new HashMap<>();
                        renameBody.put("field_name", "订单号");
                        renameBody.put("type", 1);
                        String renameUrl = BASE_URL + "/bitable/v1/apps/" + appToken
                                + "/tables/" + tableId + "/fields/" + fid;
                        var renameResp = restTemplate.exchange(renameUrl, HttpMethod.PUT,
                                new HttpEntity<>(renameBody, authHeaders()), Map.class);
                        if (isApiSuccess(renameResp.getBody())) {
                            log.info("默认主字段 {}→订单号 重命名成功", fname);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("重命名默认主字段失败", e);
        }
    }
}
