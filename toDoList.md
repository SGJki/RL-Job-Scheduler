# RL-Job-Scheduler 项目执行路线图 (Phase 7 - Phase 10)

本文档详细编排了项目从 Phase 7 到 Phase 10 的工作计划、技术栈选型及实现细节。

---

## 📅 项目环境概览
- **Java 版本**: 17
- **Spring Boot 版本**: 4.0.3 (注意：需确保所有添加的依赖与此版本及 Java 17 兼容)
- **核心框架**: MyBatis-Plus, Spring Security (JWT), WebSocket (STOMP)

---

## 🚀 Phase 7: 实时交互优化 (Real-time Interaction)
> **目标**: 消除前端 AJAX 轮询，实现低延迟的任务状态同步。

### 1. 技术栈
- **后端**: `spring-boot-starter-websocket` (STOMP 协议)
- **前端**: `SockJS-client`, `Stomp.js`

### 2. 实现细节
- [x] **后端配置**: 创建 `WebSocketConfig`，启用 STOMP 消息代理，注册 `/ws` 端点。
- [x] **消息推送**: 在 `TrainingExecutor` 中注入 `SimpMessagingTemplate`，当任务状态变更时，向 `/topic/tasks` 发送实时 JSON 数据。
- [x] **前端集成**: 在 `index.html` 中建立连接，订阅频道，收到消息后通过 jQuery 局部更新 DOM 元素（Status/Reward）。
- [x] **验证**: 任务启动及完成后，前端无需刷新即可看到状态变化。项目已在 MySQL 环境下稳定运行在 8080 端口。

---

## 📺 Phase 8: 实时日志监控 (Log Streaming)
> **目标**: 在网页上实现类似 `tail -f` 的效果，实时监控训练进度。

### 1. 技术栈
- **日志监听**: `Apache Commons IO (Tailer)`
- **推送通道**: WebSocket (复用 Phase 7 基础设施)
- **前端渲染**: 自定义控制台 UI (带自动滚动功能)

### 2. 实现细节
- [x] **依赖引入**: 添加了 `commons-io:2.15.1`。
- [x] **全局监听**: 实现了 `GlobalLogTailerListener`，单线程高效监听 `logs/training.log`。
- [x] **正则过滤**: 通过正则表达式提取 `[TaskID]`，实现日志与任务频道的精准匹配。
- [x] **WebSocket 频道**: 建立动态频道 `/topic/logs/{taskId}`。
- [x] **前端控制台**: 在 `index.html` 中集成日志模态框，支持实时追加、着色及自动滚动。
- [x] **稳定性增强**: 为 Python 进程添加了 2 小时超时强制杀灭机制，防止僵尸进程。
- [x] **系统监控**: 实现了 `/api/monitor/health` 接口，支持实时查看训练线程池状态。

---

## ☁️ Phase 9: 分布式调度 (Distributed Scheduling & RPC)
> **目标**: 算力扩容，支持将计算任务分发到不同的 GPU 物理节点执行。

### 1. 技术栈
- **网络通信**: **Netty** (高性能 NIO 框架)
- **序列化**: **Protobuf** (极致传输性能)
- **服务发现**: **Redis** (轻量级) 或 **Nacos** (企业级)
- **调度算法**: 简单的 Load Balancer (如 Round Robin 或 GPU 负载优先)。

### 2. 实现细节
- [ ] **Worker Agent 开发**: 编写一个独立的 Java 应用，负责接收 Master 指令并执行本地 Python 脚本。
- [ ] **Master-Worker 通讯**: 基于 Netty 实现自定义 RPC，定义 `ExecuteTaskRequest` 和 `LogHeartbeat` 协议。
- [ ] **注册中心接入**: Worker 启动时向注册中心上报可用算力（GPU 数量、内存等）。
- [ ] **任务分发逻辑**: Master 接收请求 -> 负载均衡选择空闲 Worker -> 发送任务 -> 异步接收执行结果。

---

## 🌐 Phase 10: 微服务网关重构 (Microservices Gateway)
> **目标**: 当集群规模扩大后，实现统一入口治理、鉴权及全链路异步化。

### 1. 技术栈
- **API 网关**: **Spring Cloud Gateway**
- **鉴权中心**: **Spring Security OAuth2 / OpenID Connect**
- **服务发现**: **Nacos**
- **配置中心**: **Spring Cloud Config / Nacos Config**

### 2. 实现细节
- [ ] **架构拆分**: 将 `Auth` (认证)、`Job` (核心业务)、`Worker` (计算) 拆分为独立的微服务。
- [ ] **网关接入**: 所有外部请求（Web/API）统一经过 Gateway。
- [ ] **统一鉴权**: Gateway 负责校验 JWT/OAuth2 Token，并在 Header 中透传用户信息。
- [ ] **限流与熔断**: 配置 Sentinel 或 Resilience4j，防止单个任务堆积导致全系统崩溃。
- [ ] **全链路监控**: 集成 SkyWalking 或 Zipkin，监控分布式环境下的请求调用链路。

---

## 📊 总体进度检查清单 (Checklist)
- [x] Phase 1-6: 核心业务、持久化与安全认证
- [x] Phase 7: WebSocket 状态推送
- [ ] Phase 8: 实时日志监控
- [ ] Phase 9: 分布式 Netty 集群
- [ ] Phase 10: 微服务网关重构
