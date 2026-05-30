package com.ordering.service;

import com.ordering.feishu.FeishuBitableClient;
import com.ordering.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书 → 后端 定时同步服务
 * 每 30 秒从飞书订单表拉取记录，检测状态变更后同步到 MySQL
 */
@Component
public class FeishuSyncService {

    private static final Logger log = LoggerFactory.getLogger(FeishuSyncService.class);

    private final FeishuBitableClient feishuClient;
    private final OrderRepository orderRepository;

    private final Set<String> syncingOrders = ConcurrentHashMap.newKeySet();

    public FeishuSyncService(FeishuBitableClient feishuClient, OrderRepository orderRepository) {
        this.feishuClient = feishuClient;
        this.orderRepository = orderRepository;
    }

    @PostConstruct
    public void init() {
        log.info("飞书同步服务启动 (间隔30s)");
    }

    /**
     * 定时从飞书拉取订单，检测状态变更
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void syncOrdersFromFeishu() {
        try {
            var feishuOrders = feishuClient.listOrdersFromBitable();
            if (feishuOrders == null || feishuOrders.isEmpty()) return;

            for (var entry : feishuOrders.entrySet()) {
                String orderNo = entry.getKey();
                String feishuStatus = entry.getValue();

                // 防止同步环：标记正在同步中
                if (!syncingOrders.add(orderNo)) continue;

                try {
                    orderRepository.findByOrderNo(orderNo).ifPresent(dbOrder -> {
                        String dbStatus = dbOrder.getStatus();
                        if (!dbStatus.equals(feishuStatus)
                                && isValidTransition(dbStatus, feishuStatus)) {
                            log.info("飞书同步状态变更: {} {} → {}", orderNo, dbStatus, feishuStatus);
                            dbOrder.setStatus(feishuStatus);
                            dbOrder.setUpdatedAt(LocalDateTime.now());
                            orderRepository.save(dbOrder);
                        }
                    });
                } finally {
                    syncingOrders.remove(orderNo);
                }
            }
        } catch (Exception e) {
            log.debug("飞书同步扫描异常(若无配置属正常): {}", e.getMessage());
        }
    }

    /**
     * 校验状态转换合法性
     */
    private boolean isValidTransition(String from, String to) {
        var validNext = Map.of(
                "CREATED", Set.of("PREPARING"),
                "PREPARING", Set.of("PENDING_CONFIRM"),
                "PENDING_CONFIRM", Set.of("CONFIRMED"),
                "CONFIRMED", Set.of("CLOSED", "VERIFIED")
        );
        return validNext.getOrDefault(from, Set.of()).contains(to);
    }
}
