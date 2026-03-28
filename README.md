this is a web Service for RL training manager 
 永远不要带着“未提交的修改”切换分支。


### 第一步：在当前分支 (dev) “结账”
在你准备离开 dev 分支之前，必须处理掉手头的改动。

- 做法 A（正式提交） ： git add . -> git commit -m "完成某某功能" 。这是最推荐的，因为你的进度被永久记录了。
- 做法 B（临时存入“储物柜”） ：如果你改了一半不想提交，执行 git stash 。这会把你的改动暂时藏起来，让工作区瞬间变干净。
### 第二步：切换到目标分支 (master)
现在工作区干净了，切换分支将非常顺滑：

- 执行： git checkout master
### 第三步：同步远程状态
在合并之前，先确保你本地的 master 是最新的：

- 执行： git pull origin master
### 第四步：执行合并
将 dev 的成果拿过来：

- 执行： git merge dev
- 此时可能发生两种情况 ：
  1. Fast-forward ：没有任何冲突，合并瞬间完成。
  2. Conflict（冲突） ：如果两个分支改了同一行，Git 会停下来。你需要手动打开报错的文件，选择保留哪个版本，然后再次 add 和 commit 。
### 第五步：推送到远程
- 执行： git push origin master
### 💡 特殊场景：如果你已经在 master 分支上改了东西怎么办？
就像你刚才遇到的情况，如果你在 master 上已经写了代码但没提交：

1. 方案 A（推荐） ：直接在 master 上 commit 掉这些改动，然后再 merge dev 。
2. 方案 B ：执行 git stash 藏起来 -> git merge dev -> git stash pop 把藏起来的代码再拿出来贴上去。


这个项目不仅仅是用来练手的，它的目标是 能够写在简历上，证明你懂“后端架构”和“AI工程化” 。
1. 核心业务痛点 (你的故事线)
   你发现在实验室里，大家跑 RL 实验非常混乱：

- 手动 SSH 到服务器跑脚本，没人记录参数。
- 多个人抢 GPU，经常把别人的进程 kill 掉。
- 跑完的结果（TensorBoard logs、模型 checkpoints）散落在各处，很难对比。
  解决方案 ：开发一个 Web 平台，统一管理训练任务、调度计算资源、可视化结果。

# RL Training Platform (强化学习训练平台)

> **Current Status**: Phase 10 (Gateway + Nacos + 流量治理 + 可观测性) 已完成第一轮落地与收尾硬化

这是一个基于 Spring Boot 构建的后端系统，旨在管理和调度强化学习（RL）训练任务。目前支持异步任务提交、MySQL 持久化存储、以及基于 Web 的可视化交互界面。

## 🏗️ 架构划分 (Architecture)
本项目采用 **Master-Worker** 分布式架构，将 Web 管理与计算压力解耦。

### 1. 目录结构
```text
src/main/java/org/sgj/rljobscheduler/
├── common/             # [公共模块] 
│   ├── proto/          # Protobuf 协议定义及生成的 Java 类
│   └── netty/          # 自定义 RPC 协议头、编解码器
├── master/             # [调度大脑] (Spring Boot 应用)
│   ├── config/         # 异步线程池、安全、WebSocket、Redis 配置
│   ├── controller/     # RESTful API、监控接口、Thymeleaf 路由
│   ├── service/        # 任务分发逻辑、LogManager (异步日志处理器)
│   └── mapper/         # 数据库访问层
└── worker/             # [计算节点] (轻量级 Java Agent)
    ├── agent/          # 心跳上报、RPC 连接管理
    └── process/        # Python 进程执行器、实时日志拦截
```

### 2. 核心设计
*   **通讯协议**: 基于 Netty + Protobuf 的自定义二进制协议。
*   **状态机**: 实现 `IDLE` / `PENDING` / `RUNNING` / `DOWN` 四种状态流转，支持网络波动容错。
*   **性能优化**: Master 端引入 `LogManager` 异步队列，实现日志持久化与 WebSocket 旁路推送的解耦。

---

## 📅 路线图 (Roadmap)

- [x] **Phase 1: 基础骨架 (RESTful API)**
    - 实现 Spring Boot Web 服务
    - 定义 Controller -> Service -> DTO 数据流转
    - 接口: `POST /api/train`, `GET /api/tasks`

- [x] **Phase 2: 异步任务调度 (Async Processing)**
    - 引入 `@Async` 和线程池
    - 实现非阻塞任务提交（立即返回 Task ID）
    - 后台线程模拟耗时训练过程

- [x] **Phase 3: 数据持久化 (Persistence & ORM)**
    - 数据库迁移: H2 (内存) -> **MySQL 8.0**
    - ORM 框架迁移: JPA -> **MyBatis-Plus**
    - 数据库初始化: `schema.sql` 自动建表

- [x] **Phase 4: 可视化交互 (Frontend Interaction)**
    - 模板引擎: **Thymeleaf**
    - UI 框架: **Bootstrap 5**
    - 交互增强: **jQuery AJAX** (无刷新提交/翻页)
    - 功能:
        - 任务提交表单 (AJAX + Modal 弹窗)
        - 任务列表自动刷新 (Auto-refresh)
        - 分页查询 (Pagination)

- [x] **Phase 5: 真实算法集成 (Real RL Integration)**
    - Java `ProcessBuilder` 调用 Python 脚本
    - 实时日志流处理 (stdout/stderr 合并)
    - 共享日志文件 (`logs/training.log`)
    - 结果解析与数据库回写

- [x] **Phase 6: 统一认证与授权 (Security)**
    - 框架: **Spring Security**
    - 认证方式: **JWT (JSON Web Token)**
    - 权限模型: **RBAC** (用户隔离)

- [x] **Phase 7: 实时交互优化 (Real-time Interaction)**
    - 协议: **WebSocket (STOMP)**
    - 后端推送: `SimpMessagingTemplate`
    - 前端同步: `SockJS` + `Stomp.js` (无刷新更新状态)

- [ ] **Phase 8: 实时日志监控 (Log Streaming)**
    - 文件监听: `Apache Commons IO (Tailer)`
    - 推送通道: WebSocket (复用)
    - 前端展示: 自动滚动日志控制台

- **Backend**: Java 17, Spring Boot 4.0.3
- **Database**: MySQL 8.0.33
- **ORM**: MyBatis-Plus 3.5.15
- **Frontend**: Thymeleaf, Bootstrap 5, jQuery 3.6
- **Integration**: Python 3 (ProcessBuilder)
- **Build Tool**: Maven

## 📦 快速开始 (Quick Start)

### 1) 环境准备
- JDK 17+
- Maven 3.6+
- MySQL 8+（创建数据库 `testdb`）
- Redis（用于调度/限流）
- Nacos（服务注册发现）
- uv（用于本地 Python 依赖管理，可选）

同步 scripts 里的 Python 环境（可选）：
```bash
uv sync
```

### 2) 启动依赖（本机）
- MySQL：确保 `testdb` 已创建，且账号密码与配置一致
- Redis：默认 `localhost:6379`
- Nacos：建议使用 `8848` 作为 Server 端口（Console/UI 可能映射到 `8080`，两者用途不同）
  - Nacos Console 常见地址：`http://127.0.0.1:8848/nacos`

### 3) 启动服务（推荐顺序）
1. 启动 Master
```bash
./mvnw spring-boot:run
```

2. 启动 Gateway（独立 Maven 工程）
```bash
./mvnw -f gateway/pom.xml spring-boot:run
```

3. 访问入口（统一入口）
- `http://localhost:8081/`

### 4) Worker（可选）
Worker 是独立 Java Agent（用于 Phase 9 的分布式执行），只在你需要分布式调度时启动。

---

## ⚙️ 配置整理 (Config Cheatsheet)

### 入口与端口
- Gateway：`GATEWAY_PORT`（默认 8081）
- Master：`MASTER_PORT`（默认 8082）
- Netty RPC：`rpc.server.port`（默认 9000）

### Nacos（Gateway/Master 通用）
- `NACOS_ADDR`（默认 `127.0.0.1:8848`）
- `NACOS_USERNAME`（默认 `nacos`）
- `NACOS_PASSWORD`（默认 `599500`）

### JWT（Gateway/Master 通用）
- `JWT_SECRET`（建议线上显式设置，避免默认值）

### 网关信任边界（Master）
- `GATEWAY_TRUST_HEADERS`：是否接受来自网关的签名 Header（默认 `true`）
- `GATEWAY_REQUIRE`：是否强制要求“受保护路径”必须带网关签名头（默认 `true`）
  - 生产建议保持 `true`，并确保 Master 端口不对公网暴露
  - 本地直连调试时可临时设置 `GATEWAY_REQUIRE=false`
- `GATEWAY_SHARED_SECRET`：网关与 Master 共享密钥（两边必须一致）
- `GATEWAY_MAX_SKEW_SECONDS`：签名时间戳允许误差（默认 300）

### 流量治理（Gateway）
- JWT 强制鉴权：`JWT_ENFORCE`（默认 `true`）
- 限流开关：`RATE_LIMIT_ENABLED`（默认 `true`）
- 限流窗口：`RATE_LIMIT_WINDOW_SECONDS`（默认 10）
- 限流次数：`RATE_LIMIT_MAX_REQUESTS`（默认 3）
- 降级开关：`FALLBACK_ENABLED`（默认 `true`）
- 降级超时：`FALLBACK_TIMEOUT_MS`（默认 5000）

配置文件参考：
- Master：[application.yaml](file:///c:/Users/13253/dataDisk/java_code/Welcome/RL-Job-Scheduler/src/main/resources/application.yaml)
- Gateway：[application.yaml](file:///c:/Users/13253/dataDisk/java_code/Welcome/RL-Job-Scheduler/gateway/src/main/resources/application.yaml)

---

## 🔭 可观测性硬化 (Observability)

### TraceId（跨 Gateway/Master）
- Header：`X-Trace-Id`
- 网关会自动生成并在请求/响应上携带；Master 会回写响应头并写入日志 MDC
- Master 日志文件：`logs/app.log`
  - 每行中 `-[<traceId>]` 就是 TraceId（未处在 HTTP 请求链路时会显示为空 `[]`）

### 任务日志与 TraceId 关联
- 新任务的日志文件第一行写入 `TRACE_ID:<traceId>`
  - Master：`logs/<taskId>.log`
  - Worker：`server_log/<taskId>.log`
用途：从任务日志可反查该任务对应的 HTTP TraceId，再去 `logs/app.log` 精准定位入口与链路问题。

---

## 🧪 灰度转发 (Canary Routing)

最小灰度规则：请求头带 `X-Canary: true` 的流量转发到 `rl-master-canary`（HTTP + WebSocket）。
增强规则（可选）：
- 按用户白名单：`security.canary.user-ids`（匹配 `X-User-Id`）
- 按百分比分流：`security.canary.percent`（0-100，基于 `X-User-Id` / `X-Trace-Id` 做稳定 hash）

示例：
```bash
curl -i -H "X-Canary: true" http://localhost:8081/
```

要让灰度生效，需要额外启动一个 Master 实例并注册为 `rl-master-canary`：
- 方式：覆盖 `spring.application.name` 与端口，例如（PowerShell）：
```bash
$env:MASTER_PORT=8083
$env:SPRING_APPLICATION_NAME="rl-master-canary"
./mvnw spring-boot:run
```

---

## 🩺 Actuator（网关）
- BasePath：`/gateway-actuator`
- 暴露端点：`health,info,gateway`
  - 例：`http://localhost:8081/gateway-actuator/health`
  - 路由：`http://localhost:8081/gateway-actuator/gateway/routes`


## 📂 项目结构
```
src/main/java/org/example/demo/
├── config/             # 配置类 (MyBatis-Plus 分页插件)
├── controller/         # 控制器 (WebController, TrainingController)
├── dto/                # 数据传输对象 (Request/Result)
├── entity/             # 数据库实体 (TrainingTask)
├── mapper/             # MyBatis Mapper 接口
├── service/            # 业务逻辑 (TrainingService, TrainingExecutor)
└── DemoApplication.java # 启动类

scripts/
└── train.py            # Python 训练模拟脚本
```

## 📝 API 文档 (Legacy)
虽然现在有了 Web 界面，但原来的 REST API 依然可用：

- **提交训练任务**
    - `POST /api/train`
    - Body: `{ "algorithm": "PPO", "episodes": 1000, "learningRate": 0.001 }`

- **查询所有任务**
    - `GET /api/tasks`

## run:

    1. 启动MySQL数据库 net start mysql 
    2. 启动nacos  startup.cmd -m standalone（建议 8848）
    3. 启动 redis
    4. 启动 Master  ./mvnw spring-boot:run
    5. 启动 Gateway ./mvnw -f gateway/pom.xml spring-boot:run
    6. 启动前端 npm run dev
    7. 访问gateway http://localhost:8081

