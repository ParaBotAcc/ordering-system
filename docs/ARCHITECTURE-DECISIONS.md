# 架构决策记录

## ADR-001: SnapAdmin 集成失败总结与 ELADMIN 调研报告

**状态**: 已归档（SnapAdmin 路线已关闭）
**日期**: 2026-05-31
**决策者**: master + Saber

---

## 一、教训总结

### 踩坑记录

| 环节 | 预期 | 实际 | 根因 |
|------|------|------|------|
| 集成方式 | `@Import(SnapAdmin.class)` 即可 | 组件扫描不生效 | SnapAdmin 没有 `spring.factories` |
| 方案 → Spring Boot 3 | 应该兼容 | `FileDownloadController` 映射报错，**无解** | SnapAdmin 0.2.1 最后一次更新 2024-04，未适配 Spring MVC 6.1 |
| 替代方案 | 手动写 spring.factories | 不支持 Spring Boot 3 的 `AutoConfiguration.imports` | 不熟悉新版自动配置机制就直接上了 |

### 经验规则

1. **三方集成的第一问: 最后一次更新是什么时候？** SnapAdmin 距今 14 个月无人维护 → 从一开始就应该判死刑
2. **依赖版本链必须对齐**: SnapAdmin 基于 SB 3.0.x 开发，我们在 3.2.5 上跑 → minor 升级都可能 break，更何况这种小型个人项目
3. **"加一行依赖就能用"的宣传要打折扣**: 集成成本往往被低估，尤其是自动配置类和组件扫描这种隐式契约
4. **先看 issue 区**: snap-admin 的 issue 里有多人反映 Spring Boot 3.2 兼容问题，早期没查

---

## 二、ELADMIN 调研报告

### 基本信息

| 项目 | 值 |
|------|-----|
| 仓库 | [elunez/eladmin](https://github.com/elunez/eladmin) |
| Stars | **21.9k**（国内 Spring Boot 后台框架前三） |
| 最后更新 | 2024-12（JPA 版）/ 持续活跃（MP 版） |
| 许可证 | Apache 2.0 |
| 文档 | [eladmin.vip](https://eladmin.vip) |
| 社区 | QQ 群 3 个，Gitee/GitHub 双平台 |

### 技术栈对比

| 维度 | 我们的项目 | ELADMIN JPA 版 | 兼容？ |
|------|-----------|----------------|--------|
| Spring Boot | **3.2.5** | 2.7.18 | ❌ 差两个大版本 |
| Java | **17** | 8+ | ✅ |
| JPA | **Jakarta** (javax→jakarta) | javax.persistence | ❌ 包名不同 |
| 数据库 | MySQL 8.0 ✅ | MySQL 5.7+ | ✅ |
| Redis | ✅ | ✅ | ✅ |
| 前端 | uni-app (Vue 3) | Vue 2 + Element UI | ❌ 框架不同 |
| 构建工具 | Maven | Maven | ✅ |
| 鉴权 | 无 | JWT + Spring Security | 需集成 |

### ELADMIN 核心能力

```
用户管理        ✅ RBAC 权限控制
角色/菜单管理   ✅ 动态路由
部门/岗位管理   ✅ 树形结构
数据字典       ✅ 状态/分类管理
操作日志       ✅ 自动记录
代码生成器     ✅ 前后端 CRUD 一键生成
定时任务       ✅ Quartz 集成
服务监控       ✅ 服务器负载/在线用户
运维部署       ✅ 远程部署
对象存储       ✅ 七牛/阿里云 OSS / S3
支付宝支付     ✅
系统接口       ✅ Swagger 文档
```

### 生态活跃度

- **Star 趋势**: 21.9k，每周仍有 +3
- **贡献者**: 59 人
- **Issue 响应**: 活跃，作者定期回复
- **周边生态**: 有 Mybatis-Plus 版（eladmin-mp），更新更频繁
- **国内知名度**: 极高，B站/CSDN/知乎大量教程

### 兼容性评估

**不兼容项：**

1. **Spring Boot 版本差（2.7 → 3.2）**：ELADMIN JPA 版最后一次发布是基于 SB 2.7.18。升级到 3.2 需要：
   - `javax.persistence` → `jakarta.persistence`（全局替换）
   - Spring Security 5 → 6（配置变化大）
   - 部分 API 变更（如 `WebMvcConfigurer`）
   - 预估工作量：**3-5 天** 纯升级

2. **前端技术栈不同**：ELADMIN 前端是 Vue 2 + Element UI，我们是 uni-app。但 ELADMIN 的后台管理前端和点餐小程序前端**本来就应该分开**——后台管理不需要跑在小程序里。

3. **鉴权体系**：我们目前没有 Spring Security，ELADMIN 自带完整的 JWT + Security 体系。如果复用 ELADMIN 的后端模块，需要调整 API 认证策略。

**兼容项（可直接复用）：**
- JPA 实体设计模式（`BaseEntity`、审计字段）
- 分模块架构（common / system / tools / logging）
- 代码生成器的模板逻辑
- 数据字典设计
- 操作日志 AOP 实现

### 推荐集成方案

**方案 A：ELADMIN 作为独立管理后台（推荐）**

```
┌─────────────────────────────────────────────┐
│  两个独立 Spring Boot 应用，共享同一个 MySQL  │
│                                             │
│  点餐 API (SB 3.2)       管理后台 (ELADMIN)   │
│  - 现有 REST API          - 用户/角色/权限     │
│  - 飞书同步               - 订单管理页面        │
│  - WS 推送                - 菜品管理页面        │
│  - 微信小程序前端           - 数据看板          │
└─────────────────────────────────────────────┘
```

- 优点：互不干扰，各自升级，风险最低
- 缺点：两套后端需要部署

**方案 B：将 ELADMIN 模块合并到现有项目（激进）**

将 ELADMIN 的模块（common / system / logging / tools）作为子模块加入我们的项目，统一使用 SB 3.2 + Jakarta。

- 优点：单一部署
- 缺点：升级工作量大，且 ELADMIN 的许多功能（支付/OSS/部署）我们根本用不上

**推荐方案 A。** 理由：
- 零迁移成本，我们现有的 API 一行都不用改
- ELADMIN 直接用原版（SB 2.7 + javax），不需要做兼容性升级
- 管理后台和顾客端本来就应该物理隔离
- 将来升级任意一方都不影响另一方

### 下一步

如果走方案 A，需要：
1. 在 `feat/snap-admin` 分支基础上清理掉 SnapAdmin 残留（我一会做）
2. 单独起一个目录或仓库放 ELADMIN 后端 + 后端管理前端
3. 配置 ELADMIN 连接我们已有的 MySQL 数据库
4. 为 `orders` 和 `menu` 表编写管理页面

要开始实施吗？
