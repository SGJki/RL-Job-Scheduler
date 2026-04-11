# RL-Job-Scheduler (强化学习训练调度平台)

> **Current Status**: Phase 10 (Gateway + Nacos + 流量治理 + 可观测性) 已完成第一轮落地与收尾硬化

这是一个基于 Spring Boot 构建的分布式后端系统，旨在管理和调度强化学习（RL）训练任务。采用 **Master-Worker** 架构，支持异步任务提交、分布式计算调度、实时日志监控。

---

## 目录

- [项目背景](#项目背景)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [可观测性](#可观测性)
- [灰度发布](#灰度发布)
- [开发路线图](#开发路线图)
- [API文档](#api文档)
- [Git操作指南](#git操作指南)

---

## 项目背景

### 核心业务痛点

在实验室环境中，RL实验管理常常面临以下问题：

| 问题 | 描述 |
|------|------|
| 🔴 参数无记录 | 手动SSH到服务器跑脚本，没人记录参数 |
| 🔴 资源冲突 | 多人抢GPU，经常误杀别人进程 |
| 🔴 结果分散 | TensorBoard logs、模型checkpoints散落各处，难以对比 |

### 解决方案

开发一个 **Web平台**，统一管理训练任务、调度计算资源、可视化结果。

**项目价值**：这是一个可以写在简历上的项目，证明你懂 **"后端架构"** 和 **"AI工程化"**。

---

## 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户浏览器                                       │
│                    (Thymeleaf + Bootstrap + WebSocket)                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          API Gateway (8081)                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ JWT 鉴权    │  │ 限流控制    │  │ 路由转发    │  │ TraceId注入 │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                          ┌─────────┴─────────┐
                          │   Nacos 服务发现   │
                          └─────────┬─────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
              ▼                     ▼                     ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  Master (8082)   │  │ Master Canary    │  │   Master N...    │
│  ┌────────────┐  │  │   (8083)         │  │                  │
│  │ Web MVC    │  │  │  (灰度实例)      │  │                  │
│  │ WebSocket  │  │  └──────────────────┘  └──────────────────┘
│  │ Scheduler  │  │
│  │ LogManager │  │
│  └────────────┘  │
└──────────────────┘
        │
        │ Netty RPC (9000)
        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Worker 集群 (计算节点)                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ Worker-1     │  │ Worker-2     │  │ Worker-3     │  │ Worker-N     │   │
│  │ ┌──────────┐ │  │ ┌──────────┐ │  │ ┌──────────┐ │  │ ┌──────────┐ │   │
│  │ │Netty连接 │ │  │ │Netty连接 │ │  │ │Netty连接 │ │  │ │Netty连接 │ │   │
│  │ │心跳续期  │ │  │ │心跳续期  │ │  │ │心跳续期  │ │  │ │心跳续期  │ │   │
│  │ │Python执行│ │  │ │Python执行│ │  │ │Python执行│ │  │ │Python执行│ │   │
│  │ └──────────┘ │  │ └──────────┘ │  │ └──────────┘ │  │ └──────────┘ │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          基础设施层                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ MySQL 8.0    │  │ Redis        │  │ Nacos Server │  │ Python Env   │   │
│  │ (持久化)     │  │ (调度/限流)  │  │ (服务发现)   │  │ (RL算法)     │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Master-Worker 通讯流程

```
┌─────────┐                                      ┌─────────┐
│ Master  │                                      │ Worker  │
└────┬────┘                                      └────┬────┘
     │                                                 │
     │  ◄────── Heartbeat Request (10s interval) ────►│
     │                                                 │
     │  ─────── ExecuteTaskRequest (Protobuf) ──────► │
     │                                                 │
     │  ◄────── LogData (实时日志流) ────────────────►│
     │                                                 │
     │  ◄────── TaskFinished (结果回传) ─────────────►│
     │                                                 │
```

### Worker 状态机

```
        ┌─────────┐
        │  IDLE   │ ◄── 心跳存在，无任务
        └────┬────┘
             │ 收到任务
             ▼
        ┌─────────┐
        │ RUNNING │ ◄── 心跳+任务Key均存在
        └────┬────┘
             │ 任务完成/超时
             ▼
        ┌─────────┐
        │ PENDING │ ◄── 心跳丢失，任务Key存在 (等待恢复)
        └────┬────┘
             │ 超时未恢复
             ▼
        ┌─────────┐
        │  DOWN   │ ◄── 心跳+任务Key均丢失
        └─────────┘
```

### 目录结构

```text
src/main/java/org/sgj/rljobscheduler/
├── common/             # [公共模块]
│   ├── proto/          # Protobuf 协议定义及生成的 Java 类
│   └── netty/          # 自定义 RPC 协议头、编解码器
├── master/             # [调度大脑] (Spring Boot 应用)
│   ├── config/         # 异步线程池、安全、WebSocket、Redis 配置
│   ├── controller/     # RESTful API、监控接口、Thymeleaf 路由
│   ├── service/        # 任务分发逻辑、LogManager (异步日志处理器)
│   ├── mapper/         # 数据库访问层
│   ├── entity/         # 数据库实体
│   └── dto/            # 数据传输对象
└── worker/             # [计算节点] (轻量级 Java Agent)
    ├── netty/          # RPC 连接管理、消息处理
    └── redis/          # 心跳续期、租约管理

gateway/                 # [API网关] (独立 Maven 工程)
scripts/                 # Python 训练脚本
└── train.py            # RL 训练模拟脚本
```

### 核心设计

| 设计点 | 实现方式 |
|--------|----------|
| **通讯协议** | Netty + Protobuf 自定义二进制协议 |
| **状态机** | IDLE / PENDING / RUNNING / DOWN 四状态流转 |
| **抢占机制** | Redis Lua 脚本原子性抢占 Worker |
| **性能优化** | LogManager 异步队列解耦 Netty EventLoop |
| **容错机制** | TaskIDKey 2分钟TTL + Worker续期 |

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行时 |
| Spring Boot | 4.0.3 | Web框架 |
| Spring Security | 6.x | 安全框架 |
| Spring Cloud Gateway | 2025.1.0 | API网关 |
| Nacos | 2025.1.0.0 | 服务发现 |
| MyBatis-Plus | 3.5.15 | ORM框架 |
| Netty | 4.1.101 | RPC通讯 |
| Protobuf | 3.25.1 | 序列化 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Thymeleaf | 3.x | 模板引擎 |
| Bootstrap | 5.x | UI框架 |
| jQuery | 3.6 | 交互增强 |
| SockJS + Stomp.js | - | WebSocket |

### 基础设施

| 技术 | 用途 |
|------|------|
| MySQL 8.0 | 数据持久化 |
| Redis | 调度/限流/心跳 |
| Nacos Server | 服务注册发现 |

---

## 快速开始

### 1. 环境准备

| 依赖 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | 必需 |
| Maven | 3.6+ | 必需 |
| MySQL | 8.0+ | 创建数据库 `testdb` |
| Redis | 任意版本 | 默认 `localhost:6379` |
| Nacos | 2.x | 建议端口 `8848` |

### 2. 启动依赖服务

```bash
# 1. 启动 MySQL
net start MySQL80

# 2. 启动 Nacos (单机模式)
startup.cmd -m standalone

# 3. 启动 Redis
redis-server
```

### 3. 启动应用服务

```bash
# 4. 启动 Master (调度中心)
./mvnw spring-boot:run

# 5. 启动 Gateway (API网关)
./mvnw -f gateway/pom.xml spring-boot:run

# 6. 启动 Worker (可选，分布式调度时需要)
java -cp target/classes:target/dependency/* org.sgj.rljobscheduler.worker.WorkerAgent
```

### 4. 访问应用

- **网关入口**: `http://localhost:8081/`
- **Nacos控制台**: `http://127.0.0.1:8848/nacos`
- **Master直连**: `http://localhost:8082/` (仅调试用)

### 5. Python环境 (可选)

```bash
# 同步 Python 依赖
uv sync
```

---

## 配置说明

### 端口配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `GATEWAY_PORT` | 8081 | 网关端口 |
| `MASTER_PORT` | 8082 | Master端口 |
| `rpc.server.port` | 9000 | Netty RPC端口 |

### Nacos配置

| 配置项 | 默认值 |
|--------|--------|
| `NACOS_ADDR` | `127.0.0.1:8848` |
| `NACOS_USERNAME` | `nacos` |
| `NACOS_PASSWORD` | `599500` |

### JWT配置

| 配置项 | 说明 |
|--------|------|
| `JWT_SECRET` | JWT密钥（生产环境必须设置） |

### 网关安全配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `GATEWAY_TRUST_HEADERS` | true | 接受网关签名Header |
| `GATEWAY_REQUIRE` | true | 强制要求签名Header |
| `GATEWAY_SHARED_SECRET` | - | 网关与Master共享密钥 |
| `GATEWAY_MAX_SKEW_SECONDS` | 300 | 时间戳允许误差 |

### 流量治理配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `JWT_ENFORCE` | true | JWT强制鉴权 |
| `RATE_LIMIT_ENABLED` | true | 限流开关 |
| `RATE_LIMIT_WINDOW_SECONDS` | 10 | 限流窗口(秒) |
| `RATE_LIMIT_MAX_REQUESTS` | 3 | 窗口内最大请求数 |
| `FALLBACK_ENABLED` | true | 降级开关 |
| `FALLBACK_TIMEOUT_MS` | 5000 | 降级超时(ms) |

### 配置文件位置

- Master: `src/main/resources/application.yaml`
- Gateway: `gateway/src/main/resources/application.yaml`

---

## 可观测性

### TraceId 链路追踪

```
请求 ──► Gateway生成X-Trace-Id ──► Master写入MDC ──► 日志文件
```

- Header: `X-Trace-Id`
- 日志文件: `logs/app.log`
- 日志格式: `yyyy-MM-dd HH:mm:ss.SSS [thread] level logger - [traceId] message`

### 任务日志关联

```
logs/<taskId>.log    第一行: TRACE_ID:<traceId>
```

用途：从任务日志反查HTTP TraceId，定位链路问题。

### Actuator端点

| 端点 | 说明 |
|------|------|
| `/gateway-actuator/health` | 网关健康状态 |
| `/gateway-actuator/gateway/routes` | 路由配置 |
| `/api/monitor/health` | 线程池状态 |

---

## 灰度发布

### 触发灰度

```bash
# 方式1: 请求头
curl -H "X-Canary: true" http://localhost:8081/api/tasks

# 方式2: 用户白名单 (配置 security.canary.user-ids)
# 方式3: 百分比分流 (配置 security.canary.percent)
```

### 启动灰度实例

```bash
# PowerShell
$env:MASTER_PORT=8083
$env:SPRING_APPLICATION_NAME="rl-master-canary"
$env:SCHEDULER_QUEUE_ENABLED="true"
./mvnw spring-boot:run
```

---

## 开发路线图

- [x] **Phase 1**: 基础骨架 (RESTful API)
- [x] **Phase 2**: 异步任务调度
- [x] **Phase 3**: 数据持久化 (MySQL + MyBatis-Plus)
- [x] **Phase 4**: 可视化交互 (Thymeleaf + Bootstrap)
- [x] **Phase 5**: 真实算法集成 (Python ProcessBuilder)
- [x] **Phase 6**: 统一认证与授权 (JWT + RBAC)
- [x] **Phase 7**: 实时交互优化 (WebSocket)
- [x] **Phase 8**: 实时日志监控 (Tailer + WebSocket)
- [x] **Phase 9**: 分布式调度 (Netty + Protobuf)
- [x] **Phase 10**: 微服务网关 (Gateway + Nacos)

---

## API文档

详细的API文档请参考: [docs/API.md](docs/API.md)

### 核心接口速览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/train` | 提交训练任务 |
| GET | `/api/tasks` | 查询任务列表 |
| GET | `/api/monitor/health` | 系统健康状态 |
| GET | `/api/monitor/logs/{taskId}` | 获取任务日志 |
| WS | `/ws` | WebSocket连接 |
| Subscribe | `/topic/tasks` | 订阅任务更新 |

---

## Git操作指南

> ⚠️ **重要原则**: 永远不要带着"未提交的修改"切换分支！

### 分支合并流程

```
dev分支 ──► master分支
```

#### 第一步：在当前分支 (dev) "结账"

在离开 dev 分支之前，处理手头改动：

**方式A（推荐）**: 正式提交
```bash
git add .
git commit -m "完成某某功能"
```

**方式B**: 临时储藏
```bash
git stash                    # 藏起改动
# ... 切换分支操作 ...
git stash pop               # 恢复改动
```

#### 第二步：切换到目标分支 (master)

```bash
git checkout master
```

#### 第三步：同步远程状态

```bash
git pull origin master
```

#### 第四步：执行合并

```bash
git merge dev
```

可能的情况：
- **Fast-forward**: 无冲突，合并瞬间完成
- **Conflict**: 需手动解决冲突，然后 `git add . && git commit`

#### 第五步：推送到远程

```bash
git push origin master
```

### 特殊场景：master上已有未提交改动

**方案A（推荐）**: 直接提交后合并
```bash
git add .
git commit -m "临时改动"
git merge dev
```

**方案B**: 储藏后合并
```bash
git stash
git merge dev
git stash pop
```

---

## 许可证

MIT License