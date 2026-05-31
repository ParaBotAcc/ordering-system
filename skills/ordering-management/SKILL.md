---
name: ordering-management
description: "点餐系统管理。触发词: 订单, 查单, 状态, 确认, 同步, 菜单, 菜品, 下单, 核销, 查看订单, 列出订单, 订单列表, 订单详情, 改状态, 取餐, 搜菜, 搜索, 桌号, /orders, /order, /status, /confirm, /sync, /menu, /search"
---

# 点餐系统管理

通过后端 API 管理订单和菜单。后端默认 `localhost:8080`，curl 加 `--noproxy '*'`。

## API 速查

```text
POST   /api/order                          下单
GET    /api/order/{orderNo}                查单
GET    /api/order/list                     全部订单
GET    /api/order/table/{tableNo}          按桌号查
PUT    /api/order/{orderNo}/status?status= 改状态
POST   /api/order/confirm                  确认取餐
POST   /api/order/merge                    合单
GET    /api/menu                           菜单
GET    /api/menu/search?keyword=           搜菜品
POST   /api/webhook/sync/now               手动同步
```

## 指令处理

### 结构化指令（高优先级，精确匹配后直接执行）

| 指令 | 动作 | API |
|------|------|-----|
| `/orders` 或 `订单列表` | 列出最近订单 | `GET /api/order/list` |
| `/order ORDxxx` 或 `查单 ORDxxx` | 订单详情 | `GET /api/order/{orderNo}` |
| `/status ORDxxx PREPARING` 或 `改状态 ORDxxx → PREPARING` | 更新状态 | `PUT /api/order/{orderNo}/status?status=...` |
| `/confirm ORDxxx` 或 `确认取餐 ORDxxx` | 取餐确认 | `POST /api/order/confirm` |
| `/menu` 或 `显示菜单` | 查看菜单 | `GET /api/menu` |
| `/sync` 或 `手动同步` | 触发飞书同步 | `POST /api/webhook/sync/now` |
| `/search 关键词` 或 `搜索 关键词` | 搜菜品 | `GET /api/menu/search?keyword=...` |

### 状态流转规则

```
CREATED → PREPARING → PENDING_CONFIRM → CONFIRMED → CLOSED / VERIFIED
```
- 只允许正向，不可回退
- 取餐确认需提交 `{"orderNo":"...","confirmedItemNames":["菜品1","菜品2",...]}`，需包含全部菜品名

### 语义查询（低优先级，模糊理解后执行）

自然语言触发如 "看看最近有啥单"、"ORDxxx 到哪了"、"帮我查一下 A01 桌的订单" 等：

1. 从语义中提取 `orderNo`、`tableNo`、`status`、`keyword` 等参数
2. 映射到上述 API
3. 执行并返回结果

## 输出规范

- **列表输出**：表格（飞书支持 markdown 表格）或分段排列，包含：订单号、桌号、总价、状态、时间
- **详情输出**：状态卡片 + 菜品明细列表 + 总额
- **错误处理**：后端不可达时提示"后端未启动，请先 `docker compose up -d` 并启动 Java 进程"
- 价格单位：分 → 元（除以 100），保留两位小数
