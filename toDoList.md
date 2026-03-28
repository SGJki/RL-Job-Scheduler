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
- **前端渲染**: 自定义控制台 UI (支持历史回填与自动滚动)

### 2. 实现细节
- [x] **依赖引入**: 添加了 `commons-io:2.15.1`。
- [x] **日志拆分**: 实现了 stdout 和 stderr 的分离持久化 (`{taskId}.log`, `{taskId}error.log`)。
- [x] **历史回填**: 新增 `GET /api/monitor/logs/{taskId}` 接口，支持弹窗重开时加载完整历史。
- [x] **WebSocket 推送**: `GlobalLogTailerListener` 实时解析聚合日志并精准广播。
- [x] **稳定性增强**: 引入自定义隔离线程池，添加 Python 进程 2 小时超时强制杀灭机制。
- [x] **系统监控**: 实现了 `/api/monitor/health` 接口，支持实时查看线程池负载。

---

## ☁️ Phase 9: 分布式调度 (Distributed Scheduling & RPC)
> **目标**: 将计算压力从 Web 服务器剥离，构建可横向扩展的算力集群。

### 1. 技术栈与方案细节 (商讨选定)
- **通讯框架**: **Netty** (高性能 NIO) + **Protobuf** (序列化)
- **连接策略**: **Worker 主动连接 Master** (解决内网穿透与防火墙问题)。
- **调试模式**: **单机多进程模拟** (Master 随 Spring 启动，Worker 作为独立 main 进程运行)。
- **项目结构重构**:
    - `common`: 协议定义、编解码器、基础工具类。
    - `master`: 调度大脑、Redis Lua 脚本、LogManager、Web 接口。
    - `worker`: Agent 实现、进程执行器、心跳续期。
- **核心逻辑**:
    - **状态机**: `IDLE` (心跳在), `PENDING` (心跳丢但任务 Key 在), `RUNNING` (心跳+任务均在), `DOWN` (均丢)。
    - **抢占机制**: Master 通过 **Lua 脚本**原子性判断“心跳存在”且“任务 Key 不存在”后占领 Worker。
    - **日志处理 (性能优化)**:
        - **Worker 端**: 本地存储 + RPC 异步推送。
        - **Master 端**: 引入 **`LogManager`**，内部维护 `BlockingQueue` 或 `Disruptor` 队列，解耦 Netty EventLoop。
        - **推送策略**: `LogManager` 消费者线程在写入本地文件的同时，**同步/异步并行**调用 WebSocket 推送，实现低延迟“旁路推送”。
    - **数据安全**: `TaskIDKey` 设置 2 分钟有效期，Worker 收到任务后定期 **续期 (Renew)**，防止因抖动被错杀。
- **自定义协议头**: 包含 `MagicNumber` (4B), `Version` (1B), `FullLen` (4B), `MsgType` (1B) 以处理粘包拆包。

### 2. 实现编排
- [x] **项目结构重构与依赖准备**
    - [x] 按照 `common/master/worker` 规划调整现有包结构。
    - [x] 引入 Netty 4.1.x 和 Protobuf 相关 Maven 依赖。
- [x] **RPC 基础构建**
    - [x] 定义 `.proto` 通讯协议（包含 `HEARTBEAT`, `EXECUTE_TASK`, `LOG_DATA`, `TASK_FINISHED` 等 MsgType）。
    - [x] 编写 Netty 自定义编解码器（处理协议头部信息与二进制流转换）。
- [x] **Worker Agent 开发**
    - [x] 实现心跳自动续期与基础 RPC 连接逻辑。
    - [x] 实现 `TaskIDKey` 首次握手注册与定期续期逻辑。
    - [x] 封装 Python 进程执行器，通过异步流监听将日志打包发回 Master，并本地存储至 `server_log/`。
- [x] **Master 调度大脑升级**
    - [x] 编写 Redis Lua 脚本处理“原子抢占”逻辑。
    - [x] 实现 `LogManager`：使用独立消费者线程处理“写本地文件”与“WebSocket 直接推送”。
    - [x] 完善状态监控：支持通过 WebSocket 实时向前端推送任务状态更新。
    - [x] 异常处理机制增强：增加对 Redis 连接异常的容错捕获，并实现任务结束后的 Worker 自动释放。

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
