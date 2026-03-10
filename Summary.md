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
