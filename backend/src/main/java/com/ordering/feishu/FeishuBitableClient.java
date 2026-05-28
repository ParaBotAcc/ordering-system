package com.ordering.feishu;

import com.ordering.config.FeishuConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 飞书多维表格集成层
 * 负责菜品表/订单表/桌号表的读写、Webhook 事件处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuBitableClient {

    private final FeishuConfig feishuConfig;

    @PostConstruct
    public void init() {
        log.info("飞书Bitable客户端初始化 (appId={})", feishuConfig.getAppId());
        log.info("  AppToken={}", feishuConfig.getBitable().getTableAppToken());
        // TODO: 初始化飞书 SDK Client
    }

    // ==================== 菜品同步 ====================

    /**
     * 从飞书菜品表读取所有菜品
     */
    public void syncMenuFromBitable() {
        // TODO: 调用飞书 API list table records
        // TODO: 解析记录，更新 MySQL + Redis
        log.info("从飞书同步菜品...");
    }

    /**
     * 同步订单到飞书订单表
     */
    public void syncOrderToBitable(String orderNo, String tableNo, String items, int totalPrice, String status) {
        // TODO: 创建/更新飞书订单表记录
        log.info("同步订单 {} 到飞书", orderNo);
    }

    /**
     * 更新订单状态到飞书
     */
    public void updateOrderStatus(String orderNo, String status) {
        // TODO: 通过飞书 API 更新表格记录字段
        log.info("更新飞书订单 {} 状态为 {}", orderNo, status);
    }

    // ==================== 监听事件 ====================

    /**
     * 处理飞书 Webhook 事件（菜品表变更触发缓存刷新）
     */
    public void handleBitableChange(String tableId, String recordId) {
        // TODO: 根据 tableId 判断是哪张表变更
        // TODO: 同步对应数据
        log.info("飞书表格变更: table={}, record={}", tableId, recordId);
    }
}
