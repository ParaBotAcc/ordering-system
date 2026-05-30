# 安全 TODO List — 点餐系统

> 优先级：P0（致命）> P1（高危）> P2（中危）> P3（低危）> P4（建议）

---

## P0 — 致命

### 1. 价格完全由前端控制
- **位置**: `OrderService.createOrder()` / `OrderRequest.OrderItem`
- **风险**: 前端发送 `price` + `subtotal`，后端不校验直接使用。恶意请求可任意改价。
- **修复**: 后端根据 `menu_id` 从数据库拉取当前单价，重新计算 `subtotal` 和 `totalPrice`，忽略前端传入的价格字段。
- **文件**: `backend/src/main/java/com/ordering/service/OrderService.java`

### 2. 菜品无 ID 关联
- **位置**: `OrderRequest.OrderItem` 只存 `name` 字段
- **风险**: 菜品改名后历史订单错乱；同名不同规格无法区分；无法做价格追溯。
- **修复**: OrderItem 增加 `menuId` 字段，后端校验 menuId 存在且对应菜品上架中；价格以数据库为准。
- **文件**: `backend/src/main/java/com/ordering/dto/OrderRequest.java`

---

## P1 — 高危

### 3. 全接口无认证鉴权
- **位置**: 所有 `@RestController`
- **风险**: 任何人可调用全部接口，包括查全部订单、改任意订单状态、合单。
- **修复**: 至少加简单 Token/API Key 校验；后续可扩展为 JWT 或微信登录态。
- **文件**: `backend/src/main/java/com/ordering/controller/*.java`

### 4. 异常处理裸奔
- **位置**: `OrderService` 中 `throw new RuntimeException(...)` 遍布各处
- **风险**: Spring Boot 默认返回 500 + 完整 stack trace，可能泄露内部实现细节。
- **修复**: 加 `@RestControllerAdvice` 全局异常处理器 + 自定义业务异常类 `OrderException`。
- **文件**: `backend/src/main/java/com/ordering/service/OrderService.java`

---

## P2 — 中危

### 5. 订单列表无分页
- **位置**: `OrderController.getAllOrders()` → `OrderRepository.findAllByOrderByCreatedAtDesc()`
- **风险**: 数据量增大后全表查询导致 OOM 或响应超时。
- **修复**: 加 `Pageable` 分页参数，默认 `page=0, size=20`。

### 6. 无订单过期/取消机制
- **位置**: 无定时任务
- **风险**: 顾客提交订单后不取餐，订单永远卡在 CREATED/PENDING_CONFIRM 状态。
- **修复**: `@Scheduled` 定时扫描超时未流转的订单，自动取消。

### 7. 库存扣减并发竞争
- **位置**: `OrderService.deductStock()` — `get(key)` + `decrement(key)` 非原子操作
- **风险**: 高并发下两个请求同时读到库存 ≥ 0，同时扣减导致超卖。
- **修复**: 使用 Redis Lua 脚本或 `WATCH` 事务实现原子扣减。

### 8. CORS 过于宽松
- **位置**: `WebConfig.corsFilter()` — `addAllowedOriginPattern("*")` + `setAllowCredentials(true)`
- **风险**: 浏览器安全规范明确禁止 `*` + credentials 组合；允许任意域名跨域访问。
- **修复**: 限定允许的 Origin 列表，或使用 Nginx 反向代理统一入口。

---

## P3 — 低危

### 9. 敏感信息在配置文件中硬编码
- **位置**: `application.yml` — MySQL 密码、飞书 appSecret
- **风险**: 代码泄露即密码泄露。
- **修复**: 使用环境变量 `${MYSQL_PASSWORD}` 或 Spring Cloud Config。

### 10. 无 HTTPS
- **位置**: 全 HTTP
- **风险**: 中间人可截获请求体（订单内容、桌号等）。
- **修复**: Nginx/Caddy 反代 + Let's Encrypt 免费证书。

### 11. 无请求频率限制
- **位置**: 无 Rate Limiting
- **风险**: 恶意刷单、暴力遍历订单号。
- **修复**: Spring Boot + Bucket4j 或网关层面限流。

### 12. 无审计日志
- **位置**: 无日志追踪
- **风险**: 出现问题无法追溯操作人和操作时间。
- **修复**: 加 MDC traceId + AOP 记录关键操作日志。

---

## P4 — 建议

### 13. 订单号可预测
- **位置**: `System.currentTimeMillis() + random(4)`
- **风险**: 按时间序猜测其他用户的订单号。
- **修复**: 使用 UUID 或 Snowflake ID。

### 14. 无数据校验白名单
- **位置**: `status` 字段任意字符串
- **风险**: 可设置任意状态值，如 `"DELETED"`、`"HACKED"`。
- **修复**: 状态值限定枚举白名单。
