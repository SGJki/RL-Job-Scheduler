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

### 1. 方案商讨中...
- **通讯框架**: 待定 (Netty vs gRPC)
- **注册中心**: 待定 (Redis vs Nacos)
- **核心逻辑**: Master 任务分发、Worker 状态上报、日志远程回传。

### 2. 待编排实现细节
- [ ] 架构详细方案设计
- [ ] Worker Agent 模块创建
- [ ] RPC 通讯协议定义
- [ ] 负载均衡调度算法实现
- [ ] 分布式日志收集与转发

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
