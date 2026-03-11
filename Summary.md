# 项目技术架构与实现总结 (Project Summary)

本文档旨在梳理 **RL-Job-Scheduler** 项目（强化学习训练调度平台）的技术架构、核心功能及实现细节。

---

## 1. 项目定位 (Project Vision)
这是一个轻量级、可扩展的分布式任务调度平台，专为管理耗时的强化学习（Reinforcement Learning）训练任务而设计。它解决了直接在命令行运行脚本时“缺乏管理、无法监控、数据易丢失”的痛点。

---

## 2. 核心架构 (Architecture)

### 2.1 技术栈概览
| 层级 | 技术选型 | 作用 |
| :--- | :--- | :--- |
| **Web 层** | Spring Boot Web (MVC) | 处理 HTTP 请求，提供 API 和页面 |
| **视图层** | Thymeleaf + Bootstrap 5 | 服务端渲染页面，提供友好的 UI |
| **交互层** | jQuery (AJAX) | 实现无刷新提交、分页、弹窗交互 |
| **业务层** | Spring Service + Async | 处理业务逻辑，异步调度耗时任务 |
| **持久层** | MyBatis-Plus + MySQL 8 | 高效的数据库 CRUD 操作 |
| **集成层** | Java ProcessBuilder + Python | 跨语言调用，执行真实训练脚本 |

### 2.2 数据流向
1.  **用户提交** -> Web 页面 (AJAX) -> Controller
2.  **任务创建** -> Service (写入数据库状态 `PENDING`) -> 返回 Task ID
3.  **异步执行** -> `@Async` 线程池 -> 启动 Python 子进程
4.  **实时日志** -> Java 读取 Python stdout/stderr -> 写入 `logs/training_all.log`
5.  **结果回写** -> 解析日志中的 `FINAL_REWARD` -> 更新数据库状态 `COMPLETED`

---

## 3. 核心功能实现细节 (Implementation Details)

### 3.1 异步任务调度 (Async Task Scheduling)
*   **痛点**：RL 训练可能持续数小时，HTTP 请求不能一直等待。
*   **实现**：
    *   使用 `@EnableAsync` 和 `@Async` 注解。
    *   `TrainingService.startTraining` 负责创建记录并立即返回。
    *   `TrainingExecutor.executeTraining` 在后台线程中运行，互不阻塞。

### 3.2 数据库持久化 (Persistence)
*   **痛点**：内存数据库 (H2) 重启即失；JPA 对于复杂 SQL 优化不便。
*   **实现**：
    *   采用 **MySQL 8.0** 作为存储引擎。
    *   引入 **MyBatis-Plus**，利用其 `BaseMapper` 快速实现 CRUD，同时保留手写 SQL 的能力。
    *   配置分页插件 `PaginationInnerInterceptor` 实现高效的分页查询。

### 3.3 前端交互与可视化 (Frontend Interaction)
*   **痛点**：传统的表单提交会刷新页面，体验差；报错直接显示 Whitelabel Error。
*   **实现**：
    *   **局部刷新**：利用 `WebController` 返回 HTML 片段 (`return "index :: task-list"`)，前端用 `$.get(...).replaceWith(...)` 动态更新表格，保留输入框状态。
    *   **AJAX 提交**：拦截 `<form>` 的 submit 事件，使用 `$.post` 提交数据，成功后弹出 **Bootstrap Modal** 提示，彻底解决页面跳转导致的 Session/URL 问题。

### 3.4 跨语言调用与日志管理 (Cross-Language & Logging)
*   **痛点**：Java 无法直接运行 Python 代码；多任务并发时日志混乱。
*   **实现**：
    *   **ProcessBuilder**：构建命令行参数 `python scripts/train.py --algo PPO ...`。
    *   **流合并**：使用 `redirectErrorStream(true)` 将标准错误合并到标准输出，避免死锁。
    *   **共享日志**：所有任务日志写入同一个文件 `logs/training_all.log`，每行日志带上 `[Task-ID]` 前缀，既便于管理又便于检索。
    *   **结果协议**：约定 Python 脚本最后输出 `FINAL_REWARD:xxx`，Java 捕获该行并解析入库。

---

## 4. 目录结构说明
```text
src/main/java/com/example/demo/
├── config/             # MybatisPlusConfig (分页配置)
├── controller/         # WebController (页面/AJAX接口)
├── dto/                # TrainingRequest/Result (数据传输)
├── entity/             # TrainingTask (数据库映射)
├── mapper/             # TrainingTaskMapper (DAO层)
├── service/            # TrainingService (业务), TrainingExecutor (异步执行)
└── DemoApplication.java

src/main/resources/
├── templates/          # index.html (前端模板)
├── application.properties # 数据库/日志配置
└── schema.sql          # 数据库初始化脚本

scripts/
└── train.py            # 被调用的 Python 训练脚本
logs/
└── training_all.log    # 统一运行日志
```

---

## 5. 未来展望 (Future Work)
*   **WebSocket 集成**：将 `logs/training_all.log` 的增量内容实时推送到前端，实现“类似控制台”的实时监控体验。
*   **Docker 化**：将 MySQL、Java 应用和 Python 环境打包成 Docker Compose，一键部署。
*   **算法扩展**：对接真实的 PyTorch/TensorFlow 训练代码，支持 GPU 调度。

# RL-Job-Scheduler Advanced Roadmap (高级功能规划)

本文档详细规划了项目从单机 Demo 向 **分布式企业级平台** 演进的五个关键阶段 (Phase 6 - Phase 10)。

---

## 🔐 Phase 6: 统一认证与授权 (Monolithic Security)
> **目标**: 在现有单体架构下，构建坚固的安全基石，实现多用户隔离。

### 1. 核心痛点
- 目前系统是“裸奔”的，任何人只要知道 URL 就能提交任务、删除数据。
- 无法区分是谁提交的任务。

### 2. 技术栈详解
- **安全框架**: **Spring Security**
    - Java 领域事实上的安全标准，提供强大的认证（Authentication）和授权（Authorization）能力。
- **认证方式**: **JWT (JSON Web Token)**
    - 无状态认证机制。登录成功后服务器签发 Token，后续请求携带 Token，服务器解密校验即可，无需查库。
- **权限模型**: **RBAC (Role-Based Access Control)**
    - 定义角色：`ADMIN` (管理所有), `USER` (管理自己)。

### 3. 实现流程
1.  **用户体系**: 新增 `User` 表 (id, username, password_hash, role)。
2.  **安全配置**: 编写 `SecurityConfig`，配置拦截规则（`/api/login` 放行，其他需认证）。
3.  **登录逻辑**: 实现登录接口，校验密码（BCrypt），签发 JWT。
4.  **Token 过滤器**: 实现 `JwtAuthenticationFilter`，拦截请求头中的 `Authorization: Bearer xxx`，解析 Token 并注入 SecurityContext。
5.  **数据隔离**: 修改 `TrainingTask` 表，增加 `user_id` 字段。Service 层查询时自动过滤当前用户的数据。

---

## 📅 Phase 7: 实时交互优化 (Real-time Interaction)
> **目标**: 彻底消除前端的无效轮询 (AJAX Polling)，实现低延迟的状态同步。

### 1. 核心痛点
- 当前每 3 秒全量刷新一次列表，浪费带宽。
- 用户提交任务后需要傻等几秒才能看到状态变化。

### 2. 技术栈详解
- **协议**: **WebSocket (RFC 6455)**
    - 全双工通信，服务器可以主动推送数据。
- **框架**: **Spring Boot Starter WebSocket**
    - 基于 STOMP (Simple Text Oriented Messaging Protocol) 协议，比裸 WebSocket 更易用。
    - 使用 `SockJS` 作为前端兼容方案（如果浏览器不支持 WebSocket，自动降级为长轮询）。
- **前端**: **Stomp.js + SockJS-client**
    - 订阅 `/topic/tasks` 频道，接收 JSON 格式的任务更新。

### 3. 实现流程
1.  **配置 WebSocket**: 创建 `WebSocketConfig`，启用 STOMP 代理。
2.  **后端推送**: 在 `TrainingExecutor` 更新任务状态（如 `RUNNING` -> `COMPLETED`）时，通过 `SimpMessagingTemplate.convertAndSend("/topic/tasks", taskDto)` 推送消息。
3.  **前端监听**: 页面加载时建立 WebSocket 连接，收到消息后直接修改 DOM 元素（不再刷新整个表格）。

---

## 📺 Phase 8: 实时日志监控 (Log Streaming)
> **目标**: 在网页上实现类似 `tail -f` 的效果，实时观看训练进度。

### 1. 核心痛点
- 目前只能去服务器翻 `logs/` 文件，或者等训练完了看结果，无法监控训练过程中的 Loss 变化。

### 2. 技术栈详解
- **文件监听**: **Apache Commons IO (Tailer)** 或 **Java NIO (WatchService)**
    - 实时监听 `logs/training_all.log` 文件的增量变化。
- **推送通道**: **WebSocket (复用 Phase 7)**
    - 建立专属频道 `/topic/logs/{taskId}`。
- **前端展示**: **Xterm.js** (可选) 或简单的 `<pre>` 标签自动滚动。

### 3. 实现流程
1.  **日志分流**: 保持 Phase 5 的设计，Python 输出重定向到文件。
2.  **监听器 (LogWatcher)**: Java 启动一个后台线程，使用 `Tailer` 监听特定任务的日志文件。
3.  **增量推送**: 每当文件有新行写入，立即通过 WebSocket 推送给订阅了该 Task ID 的前端用户。
4.  **前端渲染**: 收到日志行 -> 追加到页面 `<div id="console">` -> 自动滚动到底部。

---

Closing non transactional SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@2d113013]
2026-03-12T01:00:43.992+08:00  WARN 892 --- [RL-Job-Scheduler] [nio-8080-exec-5] .w.s.m.s.DefaultHandlerExceptionResolver : Resolved [org.springframework.http.converter.HttpMessageNotReadableException: Required request body is missing: public org.springframework.http.ResponseEntity<?> org.sgj.rljobscheduler.controller.AuthController.register(java.lang.String,java.lang.String) throws java.lang.Exception]

## ☁️ Phase 9: 分布式调度 (Distributed Scheduling & RPC)
> **目标**: 将计算压力从 Web 服务器剥离，构建可横向扩展的算力集群。

### 1. 核心痛点
- Web 服务器和训练进程在同一台机器上。如果训练任务把 CPU/内存吃光了，Web 服务也会挂掉。
- 只有一台机器，算力有限。

### 2. 技术栈详解
- **网络框架**: **Netty**
    - 高性能 NIO 框架，用于实现自定义 RPC 协议。
- **通信协议**: **Protobuf (Google Protocol Buffers)**
    - 比 JSON 更小、更快，适合内部高性能传输。
- **注册中心**: **Nacos** 或 **Redis**
    - 管理所有 Worker 节点的在线状态。
- **架构角色**:
    - **Master (Scheduler)**: 也就是现在的 Spring Boot 应用，负责接收 HTTP 请求、分发任务。
    - **Worker (Agent)**: 部署在 GPU 服务器上的轻量级 Java/Go 程序，负责接收指令、执行 Python、回传日志。

### 3. 实现流程
1.  **Worker 开发**: 基于 Netty 编写一个 Agent，启动时向 Redis 注册 ("我是 Worker-1，我有 4 个 GPU")。
2.  **任务分发**: Scheduler 收到任务 -> 查询 Redis 找空闲 Worker -> 建立 Netty 长连接 -> 发送 `ExecuteTaskRequest` (Protobuf)。
3.  **远程执行**: Worker 收到请求 -> 启动本地 `ProcessBuilder` -> 捕获日志。
4.  **日志回传**: Worker -> Netty -> Scheduler -> WebSocket -> 前端。

---

## 🌐 Phase 10: 微服务网关重构 (Microservices Gateway)
> **目标**: 当业务极其复杂、团队规模扩大时，将单体应用拆分为微服务集群。

### 1. 核心痛点
- 随着 Phase 9 的完成，系统已经有了 Master 和 Worker，架构逐渐复杂。
- 需要统一的流量入口来处理限流、熔断、灰度发布等高级治理需求。

### 2. 技术栈详解
- **网关**: **Spring Cloud Gateway** (基于 WebFlux/Netty)
- **鉴权**: **Spring Security OAuth2** (作为独立的认证中心)
- **注册中心**: **Nacos** (服务发现)

### 3. 演进策略
1.  **物理拆分**: 将现在的 `DemoApplication` 拆分为 `Auth Service` (认证)、`Job Service` (业务)、`Gateway` (网关)。
2.  **网关接入**: 所有外部请求走 Gateway，Gateway 负责校验 OAuth2 Token，然后转发给后端服务。
3.  **全链路异步**: 逐步将核心业务改造为 WebFlux，实现极致吞吐。

---

## 📊 总结与演进路线

| 阶段 | 核心价值 | 关键技术 | 复杂度 |
| :--- | :--- | :--- | :--- |
| **Phase 6** | **安全性 (单体)** | Spring Security, JWT | ⭐⭐⭐ |
| **Phase 7** | **体验升级** | WebSocket, STOMP | ⭐⭐ |
| **Phase 8** | **可观测性** | IO Tailer, Xterm.js | ⭐⭐ |
| **Phase 9** | **高性能 (分布式)** | **Netty**, RPC, Protobuf | ⭐⭐⭐⭐⭐ |
| **Phase 10** | **微服务架构** | **Spring Cloud Gateway**, OAuth2 | ⭐⭐⭐⭐⭐ |

**当前任务**: 聚焦 **Phase 6**，为单体应用穿上铠甲！🛡️
