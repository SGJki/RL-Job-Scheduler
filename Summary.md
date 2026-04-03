# 项目技术架构与实现总结 (Project Summary)

本文档总结 **RL-Job-Scheduler**（强化学习训练调度平台）从单体 Demo 演进到“Gateway + Master + Worker”的阶段性成果：每个 Phase 的模块功能、实现方式、技术栈、架构逻辑、关键 bug 复盘，以及后续完善方向。

---

## 1. 项目定位 (Project Vision)
平台目标是让 RL 训练任务具备“可提交、可追踪、可回放、可恢复、可扩展”的工程能力：
- **任务管理**：提交/查询/分页/状态机（PENDING/RUNNING/COMPLETED/FAILED）。
- **可观测性**：任务日志、WebSocket 实时推送、TraceId 贯穿排障。
- **可扩展性**：从单机 Python 进程执行演进为分布式 Master-Worker 调度。
- **可治理性**：引入网关完成鉴权、限流、降级、灰度等入口治理。

---

## 2. 当前架构 (Current Architecture)

### 2.1 组件与职责
- **Gateway（Spring Cloud Gateway / WebFlux）**
  - 统一入口、JWT 校验、Header 透传与签名、限流、超时降级、灰度路由、TraceId 注入与回写、Actuator 收敛。
- **Master（Spring Boot MVC）**
  - Web/UI + API、任务落库、调度中心（Redis 抢占 + 队列）、Netty RPC 服务端、日志汇聚（LogManager）、WebSocket 推送、对账/回收定时任务。
- **Worker（Java Agent / Netty Client）**
  - 常驻执行代理：连接 Master、接收任务、启动 Python 进程、推送日志/状态、自动重连、自维护 Redis 租约（续租）。
- **Redis**
  - Worker 存活/占用租约、调度抢占（Lua）、任务队列、任务 owner 映射、attempt（栅栏 token 的 currentAttempt）。
- **MySQL**
  - 任务与用户等业务数据的持久化（最终状态以 DB 为准）。
- **Nacos**
  - Gateway/Master 的服务注册发现（HTTP 层），支撑灰度路由（rl-master / rl-master-canary）。

### 2.2 请求链路（HTTP / 鉴权 / 可观测性）
1. 浏览器访问入口 `http://localhost:8081`（Gateway）。
2. Gateway 生成/复用 `X-Trace-Id`，并在响应回写该 header。
3. Gateway 校验 JWT（Cookie/Bearer），成功后透传 `X-User-*`，并对关键 header + timestamp 进行 HMAC 签名。
4. Master 端验证网关签名头（可配置为“强制要求来自网关”），并建立认证上下文。
5. Master 处理业务请求，日志中输出 `[traceId]`，便于按请求检索。

### 2.3 调度链路（Master-Worker / 队列 / 恢复）
1. 提交训练 -> Master 写 DB（PENDING）并返回 taskId。
2. 若有空闲 Worker -> Redis Lua 原子抢占成功 -> Master 通过 Netty 下发 `ExecuteTaskRequest`。
3. 若无空闲 Worker -> taskId 入队（去重）等待。
4. Worker 心跳上报 `currentTaskId`；Master 在 Worker 空闲/任务结束时从队列 pop 并下发下一任务。
5. Worker 启动 Python 进程，推送日志流与状态上报，Master 写日志并通过 WebSocket 推送前端。
6. Master 定时对账：
  - PENDING 对账：DB 中 PENDING 且存在 idle Worker 时，只补入队列（不直接 dispatch），避免任务被“遗忘”。
  - RUNNING 回收：Worker 失联/租约消失时，将任务回收为 PENDING 并入队，避免 RUNNING 永久卡死。

### 2.4 栅栏 token（attemptId，方案 A：Redis currentAttempt）
目标是防止“重复执行时旧结果覆盖新结果”：
- Master 下发任务前，Redis `INCR task:<taskId>:currentAttempt` 分配 attempt，并随请求下发给 Worker。
- Worker 在 `TaskStatusReport` 中携带 attempt。
- Master 仅当 `report.attempt == Redis.currentAttempt` 时才更新 DB/推送；否则丢弃旧 attempt 的结果。

---

## 3. 技术栈与实现架构 (Tech Stack & Design)
| 模块 | 技术选型 | 关键用途 |
| :--- | :--- | :--- |
| Web（Master） | Spring Boot 4 + MVC + Thymeleaf | 页面渲染与 HTTP API |
| 安全 | Spring Security + JWT | 登录认证、用户隔离、网关信任边界 |
| 网关 | Spring Cloud Gateway（WebFlux/Netty） | 入口鉴权、限流、降级、灰度、TraceId |
| 注册发现 | Nacos | Gateway/Master 服务发现（rl-master/rl-master-canary） |
| RPC | Netty | Master-Worker 长连接任务下发/日志/状态上报 |
| 序列化 | Protobuf | RPC 消息结构化、低开销传输 |
| 存储 | MySQL + MyBatis-Plus | 任务、用户等业务数据 |
| 协调/缓存 | Redis + Lua | 抢占原子性、租约/队列、attempt |
| 可观测性 | WebSocket(STOMP) + LogManager + TraceId | 实时状态/日志推送与排障关联 |
| 脚本执行 | ProcessBuilder + Python（uv 管理依赖） | 启动真实训练脚本、日志采集 |

---

## 4. Phase 总结（从 Demo 到 Phase 10）

### Phase 1-5（单体 Demo 基建）
- **功能**：提交任务、异步执行 Python、MySQL 持久化、基础 UI、分页查询。
- **实现要点**：`@Async` 执行、任务状态机、ProcessBuilder 捕获 stdout/stderr、日志落盘。

### Phase 6（认证与授权）
- **功能**：Spring Security + JWT、多用户隔离（用户维度的数据隔离）。
- **实现要点**：JWT 过滤器解析/注入认证上下文；Service 层按 userId 查询隔离。

### Phase 7（实时交互）
- **功能**：WebSocket 推送任务状态，前端无需轮询。
- **实现要点**：STOMP topic `/topic/tasks`；Master 在状态更新时广播。

### Phase 8（实时日志监控）
- **功能**：日志聚合 + Tailer + WebSocket 分 topic 推送，支持历史回填。
- **实现要点**：按 taskId 分发日志流，前端控制台实时追加。

### Phase 9（分布式调度）
- **功能**：Master-Worker 架构，Netty+Protobuf 下发任务/上传日志/上报状态。
- **实现要点**：
  - Redis Lua 原子抢占 Worker
  - Worker 侧执行 Python 并推送日志/状态
  - Master 侧 LogManager 解耦 Netty EventLoop 与 IO
  - 任务结束释放占用 Key 并触发队列继续下发

### Phase 10（网关与流量治理）
- **功能**：
  - 统一入口（Gateway）+ Nacos 服务发现
  - JWT 鉴权（网关）+ Master 信任网关签名头
  - 限流（Redis）/超时降级（友好错误页）/TraceId
  - 灰度路由（Header/白名单/百分比）到 `rl-master-canary`
  - Actuator 暴露收敛（网关）
- **实现要点**：
  - 网关对 Header 进行 HMAC 签名，Master 校验后才信任 `X-User-*`
  - 统一错误页排除转发/鉴权，避免重定向循环
  - 通过响应头标注 canary/上游服务，方便验证分流效果

---

## 5. 关键 Bug 复盘（已修复）
- **网关降级页重定向过多**：错误页路径被路由转发或被鉴权拦截，导致 fallback -> redirect 无限循环。修复：错误页从转发与鉴权白名单中排除，并排除 fallback 处理。
- **任务“被遗忘”**：所有 Worker 忙导致新任务 PENDING，后续 Worker 空闲也不再调度。修复：引入 Redis 队列 + “空闲/完成触发 pop 下发” + DB PENDING 对账补入队列。
- **RUNNING 卡死**：Worker 宕机导致 task 永远 RUNNING。修复：租约 + owner 映射 + RUNNING 回收器，将孤儿任务回收为 PENDING 并入队。
- **Master 重启后 Worker 连不上**：RPC Server 被禁用或 Worker 连接地址错误。修复：RPC enabled 开关明确、Worker 支持通过 env/args 配置 host/port，并实现断线自动重连。
- **测试环境定时任务误触发**：H2 未建表导致 scheduled 查询报错。修复：测试 profile 中关闭队列/对账/回收定时任务。

---

## 6. 目录结构（当前）
```text
gateway/                         # Spring Cloud Gateway（独立 Maven 工程）
  src/main/java/...              # 过滤器：JWT/TraceId/限流/降级/灰度路由
  src/main/resources/application.yaml

src/main/java/org/sgj/rljobscheduler/
  master/                        # Web + 调度中心 + Netty Server
  worker/                        # WorkerAgent + Netty Client + Python 执行
  common/                        # Netty 协议与 Protobuf 生成类

src/main/proto/protocol.proto    # Protobuf 协议（心跳/下发/日志/状态/attempt）
logs/                            # Master 日志与任务日志
server_log/                      # Worker 本地任务日志（模拟目录）
```

---

## 7. 未来完善方向 (Future Work)
- **MDC 传播（可观测性增强）**：在 `@Async`/队列消费线程等异步边界传播 traceId 或显式携带，串联“请求 -> 调度 -> 执行 -> 上报”全链路；同时避免 ThreadLocal 泄漏串台。
- **Master 冷启动（warming-up）**：启动初期只入队不调度，待 Worker 心跳稳定后再开始调度，减少窗口期误派发。
- **RPC 发现与高可用**：Worker 增加多 Master 地址轮询或接入 Nacos 发现；进一步做 Master 单活/选主，避免 Worker 分裂连接。
- **attempt 生命周期收敛**：任务结束后清理/缩短 `currentAttempt` TTL；为日志/监控补充 attempt 维度排障能力。
- **治理与监控**：网关限流/降级增加更标准的可观测指标（Prometheus/Micrometer），对队列长度、回收次数、重试次数等建立仪表盘与报警。
