# 点餐系统后端测试说明文档

## 1. 测试目标

### 1.1 覆盖范围
- **单元测试**：Service 层核心业务逻辑（OrderService）
- **控制器测试**：Controller 层 REST 接口（MenuController、OrderController）
- **集成测试**：Repository 数据访问层 + 端到端业务场景
- **数据初始化测试**：DemoDataInitializer 启动加载

### 1.2 测试策略

| 层级 | 技术 | 数据库 | Mock |
|------|------|--------|------|
| 单元 | JUnit 5 + Mockito | — | MenuRepository, OrderRepository, RedisTemplate |
| 控制器 | MockMvc | H2 内存 | Service 层（可选） |
| 集成 | SpringBootTest | H2 内存 | — |
| 数据 | SpringBootTest | H2 内存 | — |

### 1.3 环境假设
- 测试运行在 `test` profile 下，使用 H2 内存数据库
- Redis 被 `@AutoConfigureMock` 或 `@MockBean` 替代
- 测试不依赖任何外部服务

---

## 2. 测试用例设计

### 2.1 单元测试：OrderService

#### UC-SVC-01: 获取菜单
- **输入**：菜单数据存在
- **预期**：返回正确分类聚合 + 菜品列表，仅返回上架（status=1）菜品

#### UC-SVC-02: 搜索菜品
- **输入**：关键词 "牛"
- **预期**：返回名称含 "牛" 的上架菜品

#### UC-SVC-03: 创建订单成功
- **输入**：桌号 "A01"，2 件菜品（招牌酸菜鱼¥38x1 + 白米饭¥2x2），库存充足
- **验证点**：
  - orderNo 不为空且格式为 ORD + 时间戳 + 4位随机
  - items JSON 正确序列化
  - totalPrice 正确计算（3800 + 400 = 4200 分）
  - status = "CREATED"
  - tableNo = "A01"
  - 调用 orderRepository.save()

#### UC-SVC-04: 创建订单库存不足
- **输入**：Redis 返回库存不足
- **预期**：抛出 RuntimeException "库存不足"

#### UC-SVC-05: 创建订单 Redis 不可用（降级）
- **输入**：RedisTemplate 抛出异常
- **预期**：降级本地内存库存，订单创建成功

#### UC-SVC-06: 查询订单
- **输入**：已存在的 orderNo
- **预期**：返回对应 Order 对象

#### UC-SVC-07: 查询不存在的订单
- **输入**：不存在的 orderNo
- **预期**：抛出 RuntimeException "订单不存在"

#### UC-SVC-08: 更新订单状态
- **输入**：CREATED 状态订单 + 新状态 "PREPARING"
- **预期**：status 更新为 "PREPARING"

#### UC-SVC-09: 取餐确认
- **输入**：PENDING_CONFIRM 状态订单 + confirmedItemNames: ["招牌酸菜鱼"]
- **预期**：
  - status → "CONFIRMED"
  - confirmDetail 存储确认的菜品 JSON

#### UC-SVC-10: 按桌号查询订单
- **输入**：桌号 "A01"
- **预期**：返回该桌所有订单，按创建时间降序

#### UC-SVC-11: 订单合并
- **输入**：2 笔同一桌的 CREATED 状态订单
- **预期**：
  - 生成新合并订单（总价 = 两笔之和）
  - 原订单 status → "CLOSED"

### 2.2 控制器测试

#### UC-CTL-01: GET /api/menu → 200
- **预期响应体**：含有 `categories` 和 `items` 字段

#### UC-CTL-02: GET /api/menu/search?keyword=牛 → 200
- **预期**：返回匹配菜品列表

#### UC-CTL-03: POST /api/order → 201
- **请求体**：合法 OrderRequest
- **预期**：返回订单 JSON，status=CREATED

#### UC-CTL-04: POST /api/order 校验失败 → 400
- **输入**：空 items 的请求体
- **预期**：HTTP 400 + 校验错误信息

#### UC-CTL-05: GET /api/order/{orderNo} → 200
- **预期**：返回完整订单详情

#### UC-CTL-06: GET /api/order/table/{tableNo} → 200
- **预期**：返回该桌订单列表

#### UC-CTL-07: GET /api/order/list → 200
- **预期**：返回所有订单

#### UC-CTL-08: PUT /api/order/{orderNo}/status → 200
- **预期**：状态更新成功

#### UC-CTL-09: POST /api/order/confirm → 200
- **预期**：status → CONFIRMED

#### UC-CTL-10: POST /api/order/merge → 200
- **预期**：返回合并后的订单

### 2.3 集成测试

#### UC-IT-01: 完整点餐流程
- 步骤：菜单 → 下单 → 备餐 → 待确认 → 取餐确认
- **预期**：每一步状态正确，数据持久化

#### UC-IT-02: DemoDataInitializer 初始化
- **预期**：启动后 menu 表有 16 条记录，4 个分类

#### UC-IT-03: 搜索与分类筛选一致性
- **预期**：菜单中每个分类的 count 与 items 列表分类过滤结果一致

---

## 3. 测试代码结构

```
src/test/java/com/ordering/
├── service/
│   └── OrderServiceTest.java       # 单元测试（Mockito）
├── controller/
│   └── OrderControllerTest.java    # MockMvc 控制器测试
└── integration/
    └── OrderingApplicationTest.java # 全链路集成测试

src/test/resources/
└── application-test.yml            # 测试配置
```

## 4. 边界条件与异常场景

| 场景 | 预期行为 |
|------|----------|
| 空购物车下单 | HTTP 400 |
| 不存在的订单号 | HTTP 404 / 抛异常 |
| 已确认的订单再次确认 | 允许重复确认，覆盖前次记录 |
| 不存在的桌号查询 | 返回空列表 |
| Redis 不可用 | 静默降级，不影响业务流程 |
| 合并不同桌的订单 | 允许合并（以第一笔的桌号为准） |
