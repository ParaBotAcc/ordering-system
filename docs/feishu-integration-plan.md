# 飞书商家管理平台 — 实施计划

## 架构概览

```
点餐系统后端 ←→ 飞书 Open API ←→ 飞书多维表格（Bitable）
                    ↕
             飞书 Bot ←→ 商家在飞书聊天内操作
```

推送到 GitHub：

```bash
cd /home/para/workspaceForOpenclaw/ordering-system
git add docs/feishu-integration-plan.md && git commit -m "docs: 飞书集成实施计划" && git push
```

---

## 分阶段实施

### Phase 1：飞书基础设施（当前步骤）
- 创建多维表格：订单表、菜品表、桌号表
- 配置飞书应用权限
- 验证 API 连通性

### Phase 2：后端双向同步
- OrderService 中同步订单 → 飞书订单表
- 定时/事件触发：飞书菜品表变更 → 后端刷新缓存
- 订单状态变更双向同步

### Phase 3：飞书 Bot 交互
- Bot 接收商家指令：/orders、/prepare、/confirm
- 飞书卡片消息推送新订单通知
- 点击卡片按钮直接更新订单状态

---

## 多维表格设计

### 订单表（order_table）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| 订单号 | 文本 | orderNo，主键 |
| 桌号 | 文本 | tableNo |
| 菜品明细 | 文本 | JSON 格式的 items |
| 总价 | 数字 | 单位：分 |
| 状态 | 单选 | CREATED/PREPARING/PENDING_CONFIRM/CONFIRMED/CLOSED |
| 取餐确认 | 文本 | confirmDetail JSON |
| 备注 | 文本 | note |
| 创建时间 | 日期 | createdAt |
| 更新时间 | 日期 | updatedAt |

### 菜品表（menu_table）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| 菜品ID | 数字 | 数据库 ID |
| 名称 | 文本 | 菜品名 |
| 分类 | 单选 | 招牌推荐/主食/小食/饮品 |
| 价格 | 数字 | 单位：分 |
| 库存 | 数字 | -1=无限 |
| 规格 | 文本 | spec |
| 描述 | 文本 | description |
| 上架 | 复选框 | 1=上架 0=下架 |

### 桌号表（table_table）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| 桌号 | 文本 | tableNo |
| 状态 | 单选 | 空闲/就餐中 |
| 当前订单 | 文本 | 当前进行中的订单号 |
