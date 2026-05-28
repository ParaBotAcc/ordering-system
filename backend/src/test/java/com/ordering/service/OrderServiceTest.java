package com.ordering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordering.dto.OrderConfirmRequest;
import com.ordering.dto.OrderRequest;
import com.ordering.entity.Menu;
import com.ordering.entity.Order;
import com.ordering.repository.MenuRepository;
import com.ordering.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** UC-SVC: OrderService 单元测试 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 单元测试")
class OrderServiceTest {

    @Mock private MenuRepository menuRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private OrderService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Captor private ArgumentCaptor<Order> orderCaptor;

    @BeforeEach
    void setUp() {
        service = new OrderService(menuRepository, orderRepository, objectMapper);
        service.setRedisTemplate(redisTemplate);
    }

    private Menu createMenu(String name, String category, int price, int stock) {
        Menu m = new Menu();
        m.setId(new Random().nextLong());
        m.setName(name);
        m.setCategory(category);
        m.setPrice(price);
        m.setStock(stock);
        m.setStatus(1);
        return m;
    }

    private OrderRequest makeRequest(String tableNo, String... items) {
        var req = new OrderRequest();
        req.setTableNo(tableNo);
        var list = new ArrayList<OrderRequest.OrderItem>();
        for (int i = 0; i < items.length; i += 4) {
            var item = new OrderRequest.OrderItem();
            item.setName(items[i]);
            item.setSpec(items[i + 1]);
            item.setPrice(Integer.parseInt(items[i + 2]));
            item.setQuantity(Integer.parseInt(items[i + 3]));
            item.setSubtotal(item.getPrice() * item.getQuantity());
            list.add(item);
        }
        req.setItems(list);
        return req;
    }

    // ==================== 2.1 菜单相关 ====================

    @Nested
    @DisplayName("UC-SVC-01 ~ 02: 菜单查询")
    class MenuTests {

        @Test
        @DisplayName("UC-SVC-01: 获取菜单 — 正确聚合分类和菜品")
        void getMenu_shouldAggregateCategories() {
            var items = List.of(
                    createMenu("酸菜鱼", "招牌推荐", 3800, -1),
                    createMenu("白米饭", "主食", 200, -1)
            );
            when(menuRepository.findByStatusOrderByCategoryAscIdAsc(1)).thenReturn(items);

            var result = service.getMenu();

            assertEquals(2, result.getCategories().size());
            assertEquals(2, result.getItems().size());
            assertEquals("招牌推荐", result.getCategories().get(0).getName());
        }

        @Test
        @DisplayName("UC-SVC-02: 搜索菜品 — 关键词模糊匹配")
        void searchMenu_shouldReturnMatchingItems() {
            var items = List.of(
                    createMenu("水煮牛肉", "招牌推荐", 4200, -1)
            );
            when(menuRepository.findByNameContainingAndStatus("牛", 1)).thenReturn(items);

            var result = service.searchMenu("牛");

            assertEquals(1, result.size());
            assertEquals("水煮牛肉", result.get(0).getName());
        }
    }

    // ==================== 2.1 订单核心 ====================

    @Nested
    @DisplayName("UC-SVC-03 ~ 07: 订单创建与查询")
    class OrderCreationTests {

        @Test
        @DisplayName("UC-SVC-03: 创建订单成功")
        void createOrder_shouldSucceed() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.decrement(anyString(), anyLong())).thenReturn(100L);
            when(orderRepository.save(any())).thenAnswer(invocation -> {
                Order o = invocation.getArgument(0);
                o.setId(1L);
                return o;
            });

            var req = makeRequest("A01", "招牌酸菜鱼", "", "3800", "1", "白米饭", "", "200", "2");
            var order = service.createOrder(req);

            assertNotNull(order.getOrderNo());
            assertTrue(order.getOrderNo().startsWith("ORD"));
            assertEquals("A01", order.getTableNo());
            assertEquals(4200, order.getTotalPrice());
            assertEquals("CREATED", order.getStatus());
            verify(orderRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("UC-SVC-04: 库存不足时抛出异常")
        void createOrder_shouldFail_whenStockInsufficient() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.decrement(anyString(), anyLong())).thenReturn(-1L);

            var req = makeRequest("A01", "招牌酸菜鱼", "", "3800", "1");
            assertThrows(RuntimeException.class, () -> service.createOrder(req));
        }

        @Test
        @DisplayName("UC-SVC-05: Redis 不可用时降级本地库存")
        void createOrder_shouldFallbackToLocalStock_whenRedisDown() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.decrement(anyString(), anyLong())).thenThrow(new RuntimeException("Redis down"));

            // 本地库存未追踪该项，应视为有货
            when(orderRepository.save(any())).thenAnswer(invocation -> {
                Order o = invocation.getArgument(0);
                o.setId(1L);
                return o;
            });

            var req = makeRequest("A01", "招牌酸菜鱼", "", "3800", "1");
            assertDoesNotThrow(() -> service.createOrder(req));
        }

        @Test
        @DisplayName("UC-SVC-06: 按 orderNo 查询订单")
        void getOrder_shouldReturnOrder() {
            var order = new Order();
            order.setOrderNo("ORD12345678");
            when(orderRepository.findByOrderNo("ORD12345678")).thenReturn(Optional.of(order));

            var result = service.getOrder("ORD12345678");
            assertEquals("ORD12345678", result.getOrderNo());
        }

        @Test
        @DisplayName("UC-SVC-07: 查询不存在的订单抛异常")
        void getOrder_shouldThrow_whenNotFound() {
            when(orderRepository.findByOrderNo("NONEXIST")).thenReturn(Optional.empty());
            assertThrows(RuntimeException.class, () -> service.getOrder("NONEXIST"));
        }
    }

    // ==================== 2.1 状态管理 ====================

    @Nested
    @DisplayName("UC-SVC-08 ~ 11: 订单状态与合并")
    class OrderStateTests {

        private Order createOrder(String status) {
            var o = new Order();
            o.setOrderNo("ORD" + System.currentTimeMillis());
            o.setTableNo("A01");
            o.setStatus(status);
            o.setItems("[{\"name\":\"测试\",\"price\":1000,\"quantity\":1,\"subtotal\":1000}]");
            o.setTotalPrice(1000);
            return o;
        }

        @Test
        @DisplayName("UC-SVC-08: 更新订单状态")
        void updateStatus_shouldChangeStatus() {
            var order = createOrder("CREATED");
            when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.updateStatus(order.getOrderNo(), "PREPARING");
            assertEquals("PREPARING", result.getStatus());
        }

        @Test
        @DisplayName("UC-SVC-09: 取餐确认")
        void confirmPickup_shouldSetConfirmed() {
            var order = createOrder("PENDING_CONFIRM");
            when(orderRepository.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var req = new OrderConfirmRequest();
            req.setOrderNo(order.getOrderNo());
            req.setConfirmedItemNames(List.of("测试"));

            var result = service.confirmPickup(req);
            assertEquals("CONFIRMED", result.getStatus());
            assertNotNull(result.getConfirmDetail());
            assertTrue(result.getConfirmDetail().contains("测试"));
        }

        @Test
        @DisplayName("UC-SVC-10: 按桌号查询订单")
        void getOrdersByTable_shouldReturnOrders() {
            var orders = List.of(createOrder("CREATED"), createOrder("PREPARING"));
            when(orderRepository.findByTableNoOrderByCreatedAtDesc("A01")).thenReturn(orders);

            var result = service.getOrdersByTable("A01");
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("UC-SVC-11: 订单合并")
        void mergeOrders_shouldCreateMergedOrder() {
            var o1 = createOrder("CREATED");
            var o2 = createOrder("CREATED");
            o2.setTableNo("A01");

            when(orderRepository.findByOrderNo(o1.getOrderNo())).thenReturn(Optional.of(o1));
            when(orderRepository.findByOrderNo(o2.getOrderNo())).thenReturn(Optional.of(o2));
            // Track saves to verify original orders were saved as CLOSED
            var savedOrders = new java.util.ArrayList<Order>();
            when(orderRepository.save(any())).thenAnswer(i -> {
                Order saved = i.getArgument(0);
                savedOrders.add(saved);
                return saved;
            });

            var result = service.mergeOrders(List.of(o1.getOrderNo(), o2.getOrderNo()));
            assertEquals("CREATED", result.getStatus());
            assertTrue(result.getOrderNo().startsWith("MERGE"));
            assertEquals(2000, result.getTotalPrice());

            // Verify: at least 3 saves (2 originals + 1 merged)
            assertTrue(savedOrders.size() >= 3);
            // Verify originals saved as CLOSED
            long closedCount = savedOrders.stream()
                    .filter(o -> "CLOSED".equals(o.getStatus())).count();
            assertEquals(2, closedCount);
            // Verify merged saved as CREATED
            long mergedCount = savedOrders.stream()
                    .filter(o -> "CREATED".equals(o.getStatus())).count();
            assertEquals(1, mergedCount);
        }
    }
}
