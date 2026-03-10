this is a web Service for RL training manager 

这个项目不仅仅是用来练手的，它的目标是 能够写在简历上，证明你懂“后端架构”和“AI工程化” 。
1. 核心业务痛点 (你的故事线)
   你发现在实验室里，大家跑 RL 实验非常混乱：

- 手动 SSH 到服务器跑脚本，没人记录参数。
- 多个人抢 GPU，经常把别人的进程 kill 掉。
- 跑完的结果（TensorBoard logs、模型 checkpoints）散落在各处，很难对比。
  解决方案 ：开发一个 Web 平台，统一管理训练任务、调度计算资源、可视化结果。

# RL Training Platform (强化学习训练平台)

> **Current Status**: Phase 4 Completed (Interactive Frontend & Visualization)

这是一个基于 Spring Boot 构建的后端系统，旨在管理和调度强化学习（RL）训练任务。目前支持异步任务提交、MySQL 持久化存储、以及基于 Web 的可视化交互界面。

## 🚀 项目进度 (Roadmap)

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

- [ ] **Phase 5: 真实算法集成 (Real RL Integration)**
    - Java `ProcessBuilder` 调用 Python 脚本
    - 实时日志流处理

## 🛠️ 技术栈 (Tech Stack)

- **Backend**: Java 17, Spring Boot 4.0.3
- **Database**: MySQL 8.0.33
- **ORM**: MyBatis-Plus 3.5.15
- **Frontend**: Thymeleaf, Bootstrap 5, jQuery 3.6
- **Build Tool**: Maven

## 📦 快速开始 (Quick Start)

### 1. 环境准备
- JDK 1.17+
- Maven 3.6+
- MySQL 8.0+ (创建数据库 `testdb`)

### 2. 数据库配置
修改 `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/testdb?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=your_password
```

### 3. 运行项目
```bash
mvn spring-boot:run
```

### 4. 访问页面
打开浏览器访问: [http://localhost:8081](http://localhost:8080)

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
```

## 📝 API 文档 (Legacy)
虽然现在有了 Web 界面，但原来的 REST API 依然可用：

- **提交训练任务**
    - `POST /api/train`
    - Body: `{ "algorithm": "PPO", "episodes": 1000, "learningRate": 0.001 }`

- **查询所有任务**
    - `GET /api/tasks`
