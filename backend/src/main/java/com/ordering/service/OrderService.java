package com.ordering.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordering.dto.MenuResponse;
import com.ordering.dto.OrderConfirmRequest;
import com.ordering.dto.OrderRequest;
import com.ordering.entity.Menu;
import com.ordering.entity.Order;
import com.ordering.repository.MenuRepository;
import com.ordering.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderService(MenuRepository menuRepository, OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.menuRepository = menuRepository;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    // 可选 Redis（不存在时使用本地内存兜底）
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // package-private setter，测试注入用
    void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Demo 模式库存（Redis 不可用时回退）
    private final Map<String, Integer> localStock = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        if (redisTemplate != null) {
            try {
                var conn = redisTemplate.getConnectionFactory().getConnection();
                conn.ping();
                log.info("Redis 连接正常");
                conn.close();
            } catch (Exception e) {
                log.warn("Redis 不可用，使用本地内存库存（Demo 模式）");
            }
        } else {
            log.warn("Redis 未配置，使用本地内存库存（Demo 模式）");
        }
    }

    // ==================== 菜单 ====================

    @Cacheable(value = "menu", key = "'all'", unless = "#result == null")
    public MenuResponse getMenu() {
        List<Menu> items = menuRepository.findByStatusOrderByCategoryAscIdAsc(1);
        MenuResponse resp = new MenuResponse();

        Map<String, Long> categoryCount = items.stream()
                .collect(Collectors.groupingBy(Menu::getCategory, Collectors.counting()));
        resp.setCategories(categoryCount.entrySet().stream()
                .map(e -> {
                    MenuResponse.Category c = new MenuResponse.Category();
                    c.setName(e.getKey());
                    c.setCount(e.getValue().intValue());
                    return c;
                })
                .collect(Collectors.toList()));

        resp.setItems(items.stream().map(this::toMenuItem).collect(Collectors.toList()));
        return resp;
    }

    @Cacheable(value = "menu", key = "#keyword", unless = "#result == null")
    public List<MenuResponse.MenuItem> searchMenu(String keyword) {
        return menuRepository.findByNameContainingAndStatus(keyword, 1)
                .stream().map(this::toMenuItem).collect(Collectors.toList());
    }

    @CacheEvict(value = "menu", allEntries = true)
    public void refreshMenuCache() {
        log.info("菜单缓存已清除");
    }

    private MenuResponse.MenuItem toMenuItem(Menu menu) {
        MenuResponse.MenuItem item = new MenuResponse.MenuItem();
        item.setId(menu.getId());
        item.setName(menu.getName());
        item.setCategory(menu.getCategory());
        item.setImageUrl(menu.getImageUrl());
        item.setPrice(menu.getPrice());
        item.setStock(menu.getStock());
        item.setSpec(menu.getSpec());
        item.setDescription(menu.getDescription());
        item.setAvailable(menu.getStock() == -1 || menu.getStock() > 0);
        return item;
    }

    // ==================== 下单 ====================

    @Transactional
    public Order createOrder(OrderRequest request) {
        String orderNo = "ORD" + System.currentTimeMillis()
                + String.format("%04d", new Random().nextInt(10000));

        // 库存扣减（优先 Redis，回退本地内存）
        for (OrderRequest.OrderItem item : request.getItems()) {
            if (!deductStock(item.getName(), item.getQuantity())) {
                throw new RuntimeException("库存不足：" + item.getName());
            }
        }

        int total = request.getItems().stream().mapToInt(OrderRequest.OrderItem::getSubtotal).sum();

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setTableNo(request.getTableNo());
        order.setNote(request.getNote());
        try {
            order.setItems(objectMapper.writeValueAsString(request.getItems()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
        order.setTotalPrice(total);
        order.setStatus("CREATED");
        // 设置时间
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        log.info("订单创建成功: {}", orderNo);
        return saved;
    }

    private boolean deductStock(String name, int quantity) {
        if (redisTemplate != null) {
            try {
                String key = "stock:" + name;
                // 先检查当前值
                String current = redisTemplate.opsForValue().get(key);
                if (current == null) {
                    // 不存在，从数据库加载真实库存
                    int dbStock = menuRepository.findByNameAndStatus(name, 1)
                            .map(Menu::getStock)
                            .orElse(-1);
                    redisTemplate.opsForValue().set(key, String.valueOf(dbStock));
                    current = String.valueOf(dbStock);
                }
                // -1 = 无限库存，直接通过
                if ("-1".equals(current)) {
                    return true;
                }
                Long stock = redisTemplate.opsForValue().decrement(key, quantity);
                if (stock >= 0) {
                    return true;
                }
                // 超额扣减，回滚
                redisTemplate.opsForValue().increment(key, quantity);
                return false;
            } catch (Exception e) {
                log.debug("Redis 异常，回退本地库存");
            }
        }
        // Redis 不可用，使用本地内存
        Integer current = localStock.get(name);
        if (current == null) return true; // 未追踪的菜品默认有货
        return localStock.merge(name, -quantity, Integer::sum) >= 0;
    }

    // ==================== 订单管理 ====================

    public Order getOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + orderNo));
    }

    public List<Order> getOrdersByTable(String tableNo) {
        return orderRepository.findByTableNoOrderByCreatedAtDesc(tableNo);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Order updateStatus(String orderNo, String status) {
        Order order = getOrder(orderNo);
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    @Transactional
    public Order confirmPickup(OrderConfirmRequest request) {
        Order order = getOrder(request.getOrderNo());
        try {
            order.setConfirmDetail(objectMapper.writeValueAsString(request.getConfirmedItemNames()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
        order.setStatus("CONFIRMED");
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    @Transactional
    public Order mergeOrders(List<String> orderNos) {
        List<Order> orders = orderNos.stream().map(this::getOrder).toList();

        String mergedNo = "MERGE" + System.currentTimeMillis();
        Order merged = new Order();
        merged.setOrderNo(mergedNo);
        merged.setTableNo(orders.get(0).getTableNo());
        merged.setStatus("CREATED");
        merged.setCreatedAt(LocalDateTime.now());
        merged.setUpdatedAt(LocalDateTime.now());

        List<OrderRequest.OrderItem> allItems = new ArrayList<>();
        int total = 0;
        try {
            for (Order o : orders) {
                OrderRequest.OrderItem[] items = objectMapper.readValue(o.getItems(), OrderRequest.OrderItem[].class);
                allItems.addAll(Arrays.asList(items));
                total += o.getTotalPrice();
                o.setStatus("CLOSED");
                o.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(o);
            }
            merged.setItems(objectMapper.writeValueAsString(allItems));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 处理失败", e);
        }
        merged.setTotalPrice(total);

        return orderRepository.save(merged);
    }
}
