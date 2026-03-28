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

### 3.5 实时交互优化 (WebSocket Implementation)
*   **痛点**：AJAX 轮询效率低，状态更新有延迟。
*   **实现**：
    *   **STOMP over WebSocket**：利用 Spring Boot WebSocket 建立持久连接。
    *   **实时推送**：`TrainingExecutor` 在更新数据库状态的同时，通过 `SimpMessagingTemplate` 向 `/topic/tasks` 发送实时数据。
    *   **局部刷新**：前端监听到更新后，精准修改表格中对应行的 DOM，无需全量重载。

### 3.6 实时日志监控 (Log Streaming Implementation)
*   **痛点**：用户无法实时看到 Python 训练脚本的输出，黑盒运行。
*   **实现**：
    *   **全局 Tailer**：使用 `Apache Commons IO` 的 `Tailer` 类，开启单线程异步监听 `logs/training.log` 文件。
    *   **正则解析**：`GlobalLogTailerListener` 通过正则捕获日志中的 `[TaskID]`。
    *   **精准广播**：利用 WebSocket 动态 Topic `/topic/logs/{taskId}`，将日志行推送到订阅该任务的前端控制台。
    *   **历史回填 (History Backfill)**：新增 `GET /api/monitor/logs/{taskId}` 接口。前端打开日志窗口时，会先通过该接口加载已持久化的历史日志，加载完成后再自动切入 WebSocket 实时流模式，解决了“关闭弹窗再打开日志丢失”的问题。
    *   **前端渲染**：自定义深色控制台 UI，支持时间戳着色及自动滚动。

### 3.7 系统稳定性与监控 (Stability & Monitoring)
*   **进程超时控制**：在 `TrainingExecutor` 中为 `process.waitFor` 设置了 **2 小时** 超时时间。若 Python 脚本因算法死循环或环境问题挂起，Java 将强制执行 `process.destroyForcibly()` 并释放线程。
*   **线程池健康检查**：新增 `/api/monitor/health` 接口，实时返回 `trainingTaskExecutor` 的活跃线程数、队列积压情况及已完成任务数，便于排查系统阻塞问题。

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

### 1. 技术实现细节
*   **后端配置 (STOMP over WebSocket)**:
    *   在 [WebSocketConfig.java](file:///c:\Users\13253\dataDisk\java_code\Welcome\RL-Job-Scheduler\src\main\java\org\sgj\rljobscheduler\config\WebSocketConfig.java) 中通过 `@EnableWebSocketMessageBroker` 开启 STOMP 协议。
    *   配置内存级消息代理 `/topic`，并注册 `/ws` 端点，支持 `SockJS` 降级方案。
*   **主动推送机制**:
    *   在 [TrainingExecutor.java](file:///c:\Users\13253\dataDisk\java_code\Welcome\RL-Job-Scheduler\src\main\java\org\sgj\rljobscheduler\service\TrainingExecutor.java) 中集成 `SimpMessagingTemplate`。
    *   每当任务状态由 `PENDING` 变为 `RUNNING` 或 `COMPLETED` 时，后端立即向 `/topic/tasks` 频道广播最新的任务实体 JSON。
*   **前端精准渲染**:
    *   [index.html](file:///c:\Users\13253\dataDisk\java_code\Welcome\RL-Job-Scheduler\src\main\resources\templates\index.html) 使用 `Stomp.js` 订阅主题。
    *   通过 jQuery 监听消息回调，利用 `task.id` 唯一定位表格行，仅更新“状态”、“奖励”和“完成时间”等关键字段，实现无感刷新。

### 2. 故障复盘 (Error Analysis)
在实现过程中，项目曾遇到以下核心报错，均已解决：
*   **端口 8080 冲突**: 僵尸 Java 进程未释放端口导致启动失败。**对策**: 使用 `taskkill` 清理占用。
*   **MySQL 8.0 连接异常**: 报错 `Public Key Retrieval is not allowed`。**对策**: 在 JDBC URL 增加 `allowPublicKeyRetrieval=true`。
*   **Java 语法与包路径错误**:
    *   [ObjectMapperConfig.java](file:///c:\Users\13253\dataDisk\java_code\Welcome\RL-Job-Scheduler\src\main\java\org\sgj\rljobscheduler\config\ObjectMapperConfig.java) 中存在多余分号 `;` 导致编译失败。
    *   Spring Boot 4.0.3 环境下 Jackson 路径变更为 `tools.jackson.databind`。
*   **SQL 保留字冲突**: 数据库表名 `user` 未加反引号导致初始化失败。**对策**: 在 [schema.sql](file:///c:\Users\13253\dataDisk\java_code\Welcome\RL-Job-Scheduler\src\main\resources\schema.sql) 中使用 `` `user` ``。

---

## 📺 Phase 8: 实时日志监控 (Log Streaming)
> **目标**: 在网页上实现类似 `tail -f` 的效果，实时监控训练进度。

### 1. 技术实现细节
*   **多流异步消费**:
    *   `TrainingExecutor` 不再重定向错误流，而是利用 `CompletableFuture` 开启两个独立线程分别消费 Python 进程的 stdout 和 stderr。
*   **日志持久化策略**:
    *   **独立日志**: 每个任务生成 `logs/{taskId}.log` (标准输出) 和 `logs/{taskId}error.log` (错误输出)，保持原始输出纯净。
    *   **聚合日志**: 所有流同步追加到 `logs/training.log`，并统一加上 `[时间戳] [TaskID]` 前缀。
*   **实时推送 (WebSocket)**:
    *   `GlobalLogTailerListener` 监听聚合日志文件，利用正则精准提取 TaskID 并广播至 `/topic/logs/{taskId}`。
*   **前端控制台**:
    *   自定义深色模态框 UI，支持日志着色、自动滚动及实时追加。

### 2. 系统稳定性增强
*   **隔离线程池**: 引入 `trainingTaskExecutor` (核心4/最大8线程)，确保长耗时 RL 任务不阻塞 Web 服务。
*   **进程超时杀灭**: 为 Python 进程设置 **2 小时** 强制超时，防止算法死循环导致僵尸进程积压。
*   **健康监控**: 提供 `/api/monitor/health` 接口，实时输出线程池活跃度及队列积压情况。

---

## 4. 目录结构说明
```text
src/main/java/org/sgj/rljobscheduler/
├── config/             # SecurityConfig, WebSocketConfig, MybatisPlusConfig, AsyncConfig
├── controller/         # WebController, TrainingController, AuthController, MonitorController
├── dto/                # TrainingRequest, TrainingResult
├── entity/             # TrainingTask, User
├── mapper/             # TrainingTaskMapper, UserMapper
├── service/            # TrainingService, TrainingExecutor, AuthService
└── RlJobSchedulerApplication.java

logs/
├── training.log        # 聚合日志 (包含所有任务，用于实时推送)
├── {taskId}.log        # 单个任务的标准输出日志
└── {taskId}error.log   # 单个任务的错误输出日志
```
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
