# 小餐饮扫码点餐系统

面向小微餐饮店的扫码点餐解决方案。顾客扫码下单、后厨实时接单、飞书管理后台一体化。

## 架构

```
┌─────────────┐     ┌──────────────┐     ┌──────────┐
│  微信小程序   │ ←WS→ │  Java 后端    │ ←→  │  MySQL    │
│  (uni-app)   │     │  Spring Boot │     │  + Redis  │
└─────────────┘     └──────┬───────┘     └──────────┘
                           │ REST / WebSocket
                           ↓
                    ┌──────────────┐
                    │  飞书 Bitable  │  ← 双向同步(30s定时+手动)
                    │  (管理后台)    │
                    └──────────────┘
```

## 技术栈

| 层 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.2, Spring Data JPA, Hibernate |
| 数据库 | MySQL 8.0 (prod, via phpMyAdmin :8081) / H2 (demo) |
| 缓存 | Redis 7 (prod) / 本地内存 (demo) |
| WebSocket | Spring WebSocket (原生, 非 STOMP) |
| 前端 | uni-app (Vue 3), 微信小程序 |
| 飞书集成 | Bitable REST API, tenant_access_token (app 身份) |

## 快速启动

### 前置条件

- **Java 17+**
- **Maven 3.8+**（或用 `mvnw`）
- **Docker + Docker Compose**（生产模式需要）
- **代理**（飞书 API 走外网，需 `http_proxy` 配置；直连则忽略）

### Demo 模式（H2 + 本地缓存，无需 Docker）

```bash
# 构建
cd backend && mvn package -DskipTests

# 启动
java -jar target/ordering-backend-1.0.0.jar --spring.profiles.active=demo

# 访问
#   API:      http://localhost:8080
#   Swagger:  http://localhost:8080/swagger-ui.html
#   H2:       http://localhost:8080/h2-console
```

Demo 模式下使用 H2 内存数据库 + 本地缓存，内置 4 分类 16 道菜品数据。重启数据清空。

### 生产模式（MySQL + Redis）

```bash
# 1. 启动基础设施（MySQL + Redis + phpMyAdmin）
docker compose up -d

# 2. 构建并启动后端
cd backend && mvn package -DskipTests
java -jar target/ordering-backend-1.0.0.jar --spring.profiles.active=prod

# 3. 如需使用代理
java -jar target/ordering-backend-1.0.0.jar --spring.profiles.active=prod \
  -Dhttp.proxyHost=172.22.0.1 -Dhttp.proxyPort=7897

# 4. phpMyAdmin（可选）
#     访问 http://localhost:8081
#     用户: ordering / 密码: ordering123
```

### 小程序前端

```bash
# 用 HbuilderX 打开 miniapp/ 目录
# 微信开发者工具运行 → 勾选"不校验合法域名"
# 默认连接 localhost:8080，真机调试需改为电脑局域网 IP
```

## 项目结构

```
ordering-system/
├── backend/                          # Spring Boot 后端
│   └── src/main/java/com/ordering/
│       ├── config/                   # 配置（Feishu, WebSocket, 异常处理, 数据初始化）
│       ├── controller/               # REST API 控制器
│       │   ├── MenuController.java   # 菜单查询/搜索
│       │   ├── OrderController.java  # 下单/查单/确认/合并
│       │   └── BitableWebhookController.java  # 飞书 Webhook 入口
│       ├── dto/                      # 请求/响应 DTO
│       ├── entity/                   # JPA 实体（Menu, Order）
│       ├── feishu/                   # 飞书 Bitable 客户端
│       │   └── FeishuBitableClient.java  # Token管理 + 记录CRUD + 双向同步
│       ├── repository/               # JPA 仓库
│       ├── service/
│       │   ├── OrderService.java     # 订单核心逻辑
│       │   ├── MenuSyncService.java  # 菜单缓存定时刷新
│       │   └── FeishuSyncService.java # 飞书↔MySQL双向同步
│       └── websocket/
│           └── OrderWebSocketHandler.java  # WS广播 + 状态推送
├── miniapp/                          # uni-app 小程序前端
│   ├── api/                          # API 封装 + WebSocket 管理器
│   ├── pages/
│   │   ├── index/index.vue           # 首页（菜单浏览 + 下单）
│   │   ├── order/index.vue           # 订单列表
│   │   ├── order-detail/index.vue    # 订单详情（含取餐确认）
│   │   └── search/index.vue          # 菜品搜索
│   └── store/index.js                # 全局状态（桌号、购物车）
├── docker-compose.yml                # MySQL + Redis + phpMyAdmin
├── docs/                             # 设计文档
└── start-demo.sh                     # Demo 启动脚本
```

## API 文档

启动服务后访问 `/swagger-ui.html` 查看完整接口。核心端点：

### 订单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/order` | 提交订单 |
| GET | `/api/order/{orderNo}` | 订单详情 |
| GET | `/api/order/list` | 全部订单列表 |
| GET | `/api/order/table/{tableNo}` | 按桌号查单 |
| PUT | `/api/order/{orderNo}/status?status=` | 更新订单状态 |
| POST | `/api/order/confirm` | 顾客取餐确认（需全选菜品） |
| POST | `/api/order/merge` | 合并订单 |

### 菜单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/menu` | 全菜单（含分类 + 菜品） |
| GET | `/api/menu/search?keyword=` | 搜索菜品 |

### 飞书同步

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/webhook/sync/now` | 手动触发一次同步 |
| POST | `/api/webhook/bitable` | Bitable Webhook 接收端 |

### WebSocket

| 端点 | 说明 |
|------|------|
| `ws://host/ws/order` | 全局长连接。服务端广播订单状态变更。 |

## 飞书集成状态

### 已实现

- **Bitable 双向同步**
  - MySQL → Bitable：下单/改状态时异步同步
  - Bitable → MySQL：每 30s 定时拉取 + 一键手动同步
  - Bitable 新增订单 → 自动创建到 MySQL（需菜品明细为合法 JSON）
  - 非法状态流转拦截（不允许逆生命周期）
- **Bitable 表格结构**
  - 订单表：订单号、桌号、菜品明细、总价、状态、备注、创建时间、更新时间
  - 菜品表、桌号表（预留）
- **同步事件推送**：Bitable 改状态 → 同步到 MySQL → WS 推送到前端

### 待实现

- **菜品管理**：Bitable「菜品表」编辑 → 后端菜单缓存刷新
- **飞书群消息通知**：新订单/状态变更 → 推送到群，含桌号/菜品/金额富文本卡片
- **消息卡片操作**：群消息卡片按钮直接操作（确认/备餐/核销），无需进表格
- **Bitable 管理控制台增强**
  - 按状态/桌号筛选视图
  - 数据仪表盘（今日单量、收入、畅销菜品）
  - Bitable 直接创建订单
- **飞书机器人管理指令**：通过机器人对话管理订单（替代暴露后端 API 到公网）

### 前置条件（如需完整使用飞书集成）

| 条件 | 替代方案 |
|------|----------|
| 公网 IP / 内网穿透 | 由飞书机器人中转（即 me） |
| Bitable 应用权限 | 已开通 ✅ |
| app_id / app_secret | 已配置 ✅ |

## TODO

- [ ] 飞书群消息推送（新订单通知卡片）
- [ ] 消息卡片按钮交互（确认/核销操作）
- [ ] Bitable 菜品表 → 后端菜单刷新
- [ ] Bitable 仪表盘 + 筛选视图
- [ ] 飞书机器人管理指令（查单/改状态/同步）
- [ ] 订单超时未取餐提醒
- [ ] 合单逻辑前端对接
- [ ] 库存管理（Redis 扣减 → 飞书同步）
- [ ] 生产环境部署脚本
- [ ] 微信小程序真机调试配置

## 状态流转

```
CREATED → PREPARING → PENDING_CONFIRM → CONFIRMED → CLOSED / VERIFIED
```

- 只允许正向流转，不可回退
- 取餐确认（PENDING_CONFIRM → CONFIRMED）要求全选所有菜品

## 开发注意事项

- **非交互式 shell 问题**：`.bashrc` 中 `case $- in *i*) ;; *) return;; esac` 会导致非交互 shell 提前 return，手动 `export JAVA_HOME` 和 `M2_HOME`
- **curl 走代理**：WSL 中 curl 默认走 Clash 代理（`172.22.0.1:7897`），访问本地服务加 `--noproxy '*'`
- **飞书 API 响应码**：HTTP 2xx 不代表业务成功，需检查 body 中 `code` 字段（0=成功）
- **小程序 WS**：微信开发者工具需勾选"不校验合法域名"；最多 2 条并发 socket

## License

MIT
