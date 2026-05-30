# 飞书双向同步 + 消息推送 实施计划

## 整体架构

```
┌──────────┐  WebSocket    ┌──────────────────┐  Feishu Open API  ┌──────────────┐
│ 小程序用户 ├─────←────────┤    Spring Boot    ├─────→←───────────┤ 飞书多维表格  │
│ (看订单状态)│              │   (OrderService   │                  │ (商家操作界面) │
└──────────┘              │  + FeishuClient)  │                  └──────────────┘
        ↑                 └────────┬─────────┘                        ↑
        │  云端推送(待定)            │                                  │
        └──────────────────────────┘                                  │
                                                                      │
                              ┌──────┴──────┐                         │
                              │  定时任务     │←─── 轮询检测变更 ────────┘
                              │  (30s间隔)   │
                              └─────────────┘
```

## 实现步骤

### Step 1: 后端 → 飞书写入同步（单向 → 双向的基础）

- `OrderService.createOrder()` → 调 `FeishuBitableClient.syncOrderToBitable()`
- `OrderService.updateStatus()` → 调 `FeishuBitableClient.updateOrderStatus()`
- `OrderService.confirmPickup()` → 调 `FeishuBitableClient.updateOrderStatus()`

### Step 2: 飞书 → 后端轮询同步（双向）

- 新增 `FeishuSyncService`，`@Scheduled(fixedRate=30000)` 每 30s 执行
- 从飞书订单表读取最近 50 条记录
- 与 MySQL 对比 status 字段，有差异则更新数据库
- 防同步环：只有在飞书端人为变更时才同步回数据库

### Step 3: 用户消息推送

方案选择：

| 方案 | 做法 | 适用阶段 |
|------|------|---------|
| A. WebSocket | 后端加 WS 端点，小程序连接后接收实时推送 | Demo 可用 |
| B. Mini Program 轮询 | 订单详情页每秒查一次状态 | 最简单 |
| C. 飞书 Bot 通知商家 | 新订单到来时 Bot 发卡片消息给商户群 | 独立功能 |

**推荐：方案 A + C 组合**
- A 对用户：WebSocket 实时推送订单状态变更
- C 对商家：飞书 Bot 卡片消息通知新订单

### Step 4: 飞书 Bot 商家操作（Phase 3 前置）

- 当飞书表格中订单状态变更 → 自动通知对应用户
- 当后端创建新订单 → 飞书 Bot 发卡片给商家

## 需要的外部信息

| 信息 | 用途 | 获取方式 |
|------|------|---------|
| 商家飞书 open_id / chat_id | Bot 发通知到指定群或个人 | 你告诉我 |
| 用户微信 open_id | 微信订阅消息推送 | 暂不使用(Demo阶段) |
| 公网 IP / 域名 | 飞书 Webhook 回调 | 暂不使用(用轮询替代) |
