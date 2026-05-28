package com.ordering.integration;

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-IT: 全链路集成测试
 * 使用 H2 内存数据库，测试完整点餐业务流程
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("集成测试：完整点餐业务流程")
class OrderingApplicationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate();
    private String baseUrl;
    private static String orderNo;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api";
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("UC-IT-02: Demo 数据初始化 — 16道菜品，4个分类")
    void menu_shouldBeInitialized() {
        var resp = rest.getForEntity(baseUrl + "/menu", String.class);
        assertEquals(200, resp.getStatusCodeValue());

        var body = resp.getBody();
        Integer itemCount = JsonPath.read(body, "$.items.length()");
        Integer catCount = JsonPath.read(body, "$.categories.length()");

        assertEquals(16, itemCount);
        assertEquals(4, catCount);
    }

    @SuppressWarnings("unchecked")
    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("UC-IT-03: 分类计数与菜品列表一致")
    void categoryCount_shouldMatchItems() {
        var resp = rest.getForEntity(baseUrl + "/menu", String.class);
        var body = resp.getBody();

        // Use JSONArray instead of List<Integer> to avoid ClassCast issues
        JSONArray catNames = JsonPath.read(body, "$.categories[*].name");

        for (Object catObj : catNames) {
            String cat = (String) catObj;
            String escaped = cat.replace("'", "\\'");

            // Count items in this category from items array
            JSONArray itemsInCat = JsonPath.read(body,
                    "$.items[?(@.category == '" + escaped + "')]");
            int actualItemCount = itemsInCat.size();

            // Get expected count from categories array
            JSONArray countArr = JsonPath.read(body,
                    "$.categories[?(@.name == '" + escaped + "')].count");
            int expectedCount = ((Number) countArr.get(0)).intValue();

            assertEquals(expectedCount, actualItemCount,
                    "分类 [" + cat + "] 的 count 应等于菜品列表过滤结果");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("UC-IT-01-1: 下单成功 → status=CREATED")
    void createOrder_shouldCreateInCreatedState() {
        var request = Map.of(
                "tableNo", "B01",
                "items", List.of(
                        Map.of("name", "水煮牛肉", "price", 4200, "quantity", 1, "subtotal", 4200),
                        Map.of("name", "白米饭", "price", 200, "quantity", 2, "subtotal", 400)
                ),
                "note", ""
        );

        var resp = rest.postForEntity(baseUrl + "/order", request, String.class);
        assertEquals(201, resp.getStatusCodeValue());

        var body = resp.getBody();
        orderNo = JsonPath.read(body, "$.orderNo");
        String status = JsonPath.read(body, "$.status");
        Integer total = JsonPath.read(body, "$.totalPrice");

        assertNotNull(orderNo);
        assertTrue(orderNo.startsWith("ORD"));
        assertEquals("CREATED", status);
        assertEquals(4600, total);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("UC-IT-01-2: 备餐中 → PREPARING")
    void updateStatus_shouldChangeToPreparing() {
        assertNotNull(orderNo);

        rest.put(baseUrl + "/order/" + orderNo + "/status?status=PREPARING", null);
        var resp = rest.getForEntity(baseUrl + "/order/" + orderNo, String.class);
        String status = JsonPath.read(resp.getBody(), "$.status");
        assertEquals("PREPARING", status);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("UC-IT-01-3: 待取餐确认 → PENDING_CONFIRM")
    void updateStatus_shouldChangeToPendingConfirm() {
        assertNotNull(orderNo);

        rest.put(baseUrl + "/order/" + orderNo + "/status?status=PENDING_CONFIRM", null);
        var resp = rest.getForEntity(baseUrl + "/order/" + orderNo, String.class);
        String status = JsonPath.read(resp.getBody(), "$.status");
        assertEquals("PENDING_CONFIRM", status);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("UC-IT-01-4: 取餐确认 → CONFIRMED")
    void confirmPickup_shouldChangeToConfirmed() {
        assertNotNull(orderNo);
        var request = Map.of(
                "orderNo", orderNo,
                "confirmedItemNames", List.of("水煮牛肉", "白米饭")
        );

        var resp = rest.postForEntity(baseUrl + "/order/confirm", request, String.class);
        assertEquals(200, resp.getStatusCodeValue());

        var body = resp.getBody();
        String status = JsonPath.read(body, "$.status");
        assertEquals("CONFIRMED", status);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("UC-IT-01-5: 订单详情完整")
    void getOrder_shouldReturnFullDetail() {
        assertNotNull(orderNo);
        var resp = rest.getForEntity(baseUrl + "/order/" + orderNo, String.class);
        var body = resp.getBody();

        assertEquals("CONFIRMED", (String) JsonPath.read(body, "$.status"));
        assertEquals("B01", (String) JsonPath.read(body, "$.tableNo"));
        assertEquals(4600, ((Number) JsonPath.read(body, "$.totalPrice")).intValue());

        JSONArray items = JsonPath.read(body, "$.items");
        assertTrue(items.size() > 0);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("UC-IT-01-6: 搜索验证")
    void searchMenu_shouldWork() {
        var resp = rest.getForEntity(baseUrl + "/menu/search?keyword=牛", String.class);
        JSONArray items = JsonPath.read(resp.getBody(), "$");
        assertTrue(items.size() > 0);
        assertTrue(((String) JsonPath.read(resp.getBody(), "$[0].name")).contains("牛"));
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("UC-IT-01-7: 多笔订单查询")
    void multipleOrders_shouldBeListed() {
        var req = Map.of(
                "tableNo", "B02",
                "items", List.of(Map.of("name", "可乐", "price", 400, "quantity", 1, "subtotal", 400)),
                "note", ""
        );
        rest.postForEntity(baseUrl + "/order", req, String.class);

        var resp = rest.getForEntity(baseUrl + "/order/list", String.class);
        JSONArray orders = JsonPath.read(resp.getBody(), "$");
        assertTrue(orders.size() >= 2);
    }
}
