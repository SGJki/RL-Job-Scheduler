# RL-Job-Scheduler API 文档

> **版本**: v1.0  
> **基础路径**: `/api`  
> **网关入口**: `http://localhost:8081`

---

## 目录

- [认证接口](#认证接口)
- [训练任务接口](#训练任务接口)
- [监控接口](#监控接口)
- [页面路由](#页面路由)
- [WebSocket 接口](#websocket-接口)
- [测试接口](#测试接口)

---

## 认证接口

### 登录

**POST** `/api/auth/login`

用户登录，获取JWT Token。

#### 请求体

```json
{
  "username": "string",
  "password": "string"
}
```

#### 响应

**成功 (200)**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**失败 (400)**

```text
用户名或密码错误
```

#### 说明

- 登录成功后，JWT Token 会同时设置在：
  - 响应体 `token` 字段
  - HttpOnly Cookie `jwt_token`（有效期1天）

---

### 登出

**POST** `/api/auth/logout`

用户登出，清除JWT Cookie。

#### 响应

**成功 (200)**

```text
Logout successful
```

#### 说明

- 清除 `jwt_token` Cookie

---

### 注册

**POST** `/api/auth/register`

注册新用户。

#### 请求体

```json
{
  "username": "string",
  "password": "string"
}
```

#### 响应

**成功 (200)**

```json
{
  "id": 1,
  "username": "newuser",
  "role": "USER"
}
```

**失败 (400)**

```text
用户名已存在
```

---

## 训练任务接口

### 提交训练任务

**POST** `/api/train`

提交一个新的RL训练任务。

#### 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 是 | `Bearer <token>` |
| `X-Trace-Id` | 否 | 追踪ID（网关自动注入） |

#### 请求体

```json
{
  "algorithm": "PPO",
  "episodes": 1000,
  "learningRate": 0.001,
  "userId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `algorithm` | string | 是 | 算法名称 (PPO, DQN, A2C等) |
| `episodes` | int | 是 | 训练轮数 |
| `learningRate` | double | 是 | 学习率 |
| `userId` | long | 否 | 用户ID（可从Token推断） |

#### 响应

**成功 (200)**

```json
{
  "taskId": "task-uuid-12345",
  "status": "PENDING",
  "message": "训练任务已提交"
}
```

---

### 查询所有任务

**GET** `/api/tasks`

获取所有训练任务列表。

#### 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 是 | `Bearer <token>` |

#### 响应

**成功 (200)**

```json
[
  {
    "id": "task-uuid-12345",
    "algorithm": "PPO",
    "episodes": 1000,
    "learningRate": 0.001,
    "status": "COMPLETED",
    "finalReward": 95.5,
    "createdAt": "2026-04-03T10:00:00",
    "completedAt": "2026-04-03T10:30:00",
    "errorMessage": "",
    "userId": 1
  }
]
```

---

### 提交任务（表单）

**POST** `/submit`

通过表单提交训练任务（用于Thymeleaf页面）。

#### 请求体 (Form Data)

| 字段 | 类型 | 说明 |
|------|------|------|
| `algorithm` | string | 算法名称 |
| `episodes` | int | 训练轮数 |
| `learningRate` | double | 学习率 |

#### 响应

返回JSON格式的 `TrainingResult`。

---

### 分页查询任务片段

**GET** `/tasks/fragment`

获取任务列表的HTML片段（用于AJAX局部刷新）。

#### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 1 | 页码 |
| `size` | int | 14 | 每页数量 |

#### 响应

返回Thymeleaf片段 `index :: task-list`。

---

## 监控接口

### 系统健康状态

**GET** `/api/monitor/health`

获取线程池健康状态。

#### 响应

```json
{
  "poolName": "RL-Training-Pool",
  "activeCount": 2,
  "corePoolSize": 4,
  "maxPoolSize": 10,
  "poolSize": 4,
  "queueSize": 0,
  "completedTaskCount": 15,
  "status": "UP"
}
```

| 字段 | 说明 |
|------|------|
| `activeCount` | 当前活跃线程数 |
| `corePoolSize` | 核心线程数 |
| `maxPoolSize` | 最大线程数 |
| `poolSize` | 当前线程池大小 |
| `queueSize` | 队列中等待的任务数 |
| `completedTaskCount` | 已完成任务总数 |

---

### 获取任务日志

**GET** `/api/monitor/logs/{taskId}`

获取指定任务的历史日志。

#### 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `taskId` | string | 任务ID |

#### 响应

**成功**

```json
{
  "status": "SUCCESS",
  "content": [
    "TRACE_ID:abc123",
    "Episode 1/1000, Reward: 10.5",
    "Episode 2/1000, Reward: 15.2",
    "..."
  ]
}
```

**日志不存在**

```json
{
  "status": "NOT_FOUND",
  "content": []
}
```

---

## 页面路由

### 首页

**GET** `/`

渲染主页面，显示任务列表。

#### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 1 | 页码 |
| `size` | int | 14 | 每页数量 |

#### 响应

渲染 `index.html` 模板。

---

### 登录页面

**GET** `/login`

渲染登录页面。

#### 响应

渲染 `login.html` 模板。

---

## WebSocket 接口

### 连接端点

**WebSocket** `/ws`

建立WebSocket连接（支持SockJS回退）。

#### 连接示例

```javascript
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);
});
```

---

### 订阅任务更新

**Subscribe** `/topic/tasks`

订阅任务状态更新推送。

#### 消息格式

```json
{
  "id": "task-uuid-12345",
  "algorithm": "PPO",
  "status": "RUNNING",
  "finalReward": 0,
  "..."
}
```

---

### 订阅任务日志

**Subscribe** `/topic/logs/{taskId}`

订阅指定任务的实时日志流。

#### 消息格式

```json
{
  "taskId": "task-uuid-12345",
  "line": "Episode 100/1000, Reward: 45.2",
  "timestamp": "2026-04-03T10:15:30"
}
```

---

## 测试接口

### Hello

**GET** `/hello`

简单测试接口。

#### 响应

```text
Hello, Spring Boot!
```

---

### 时间

**GET** `/time`

获取服务器当前时间。

#### 响应

```text
当前服务器时间: 2026-04-03T10:00:00
```

---

### 个性化时间

**GET** `/time-custom`

获取个性化时间问候。

#### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | string | Guest | 用户名 |

#### 响应

```json
{
  "time": "2026年04月03日 10:00:00",
  "greeting": "早上好! 🌞",
  "name": "Guest"
}
```

---

## 错误响应

### 通用错误格式

```json
{
  "timestamp": "2026-04-03T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "具体错误信息",
  "path": "/api/train"
}
```

### 常见错误码

| 状态码 | 说明 |
|--------|------|
| 400 | 请求参数错误 |
| 401 | 未认证（需要登录） |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 503 | 服务不可用（降级） |

---

## 网关相关

### Actuator 端点

**Base Path**: `/gateway-actuator`

| 端点 | 说明 |
|------|------|
| `/health` | 网关健康状态 |
| `/info` | 网关信息 |
| `/gateway/routes` | 路由配置 |

#### 示例

```bash
curl http://localhost:8081/gateway-actuator/health
```

---

## 灰度路由

### 触发灰度

在请求头中添加 `X-Canary: true` 即可将流量转发到灰度实例。

```bash
curl -H "X-Canary: true" http://localhost:8081/api/tasks
```

---

## 限流配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `RATE_LIMIT_ENABLED` | true | 限流开关 |
| `RATE_LIMIT_WINDOW_SECONDS` | 10 | 限流窗口（秒） |
| `RATE_LIMIT_MAX_REQUESTS` | 3 | 窗口内最大请求数 |

超过限流阈值返回 **429 Too Many Requests**。