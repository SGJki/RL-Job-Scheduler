# MasterHandler EventExecutorGroup 隔离改进

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 MasterHandler 从 Netty EventLoop 隔离到独立业务线程池，消除 Redis/MySQL 同步 IO 对 EventLoop 的阻塞。

**Architecture:** 在 MasterNettyServer 中创建 DefaultEventExecutorGroup，通过 `ch.pipeline().addLast(bizGroup, masterHandler)` 将 handler 业务交给线程池执行。EventLoop 只负责接收数据和编解码，耗时的 Redis/MySQL 操作在 bizGroup 线程执行。

**Tech Stack:** Netty DefaultEventExecutorGroup, LinkedBlockingQueue

---

## Files Modified

- `src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java`

---

## Task 1: 添加 EventExecutorGroup 字段

**Files:**
- Modify: `src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java:37-41`

- [ ] **Step 1: 添加 imports**

在文件顶部添加：

```java
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
```

- [ ] **Step 2: 添加 bizGroup 字段**

在现有字段区（`private EventLoopGroup bossGroup;` 等下方）添加：

```java
private EventExecutorGroup bizGroup;
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java
git commit -m "refactor(master): add EventExecutorGroup field declaration"
```

---

## Task 2: 初始化 bizGroup 并改造 Pipeline

**Files:**
- Modify: `src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java:44-76`

- [ ] **Step 1: 在 start() 方法中初始化 bizGroup**

在 `bossGroup = new NioEventLoopGroup(1);` 后、`workerGroup = new NioEventLoopGroup();` 前添加：

```java
bizGroup = new DefaultEventExecutorGroup(
    8,                                    // corePoolSize
    16,                                   // maximumPoolSize
    60, TimeUnit.SECONDS,                 // keepAliveTime
    new java.util.concurrent.LinkedBlockingQueue<>(1000)  // queue capacity
);
```

完整 start() 方法改造后应为：

```java
@PostConstruct
public void start() {
    if (!enabled) {
        LOG.info(">>> Master Netty Server 已禁用");
        return;
    }
    new Thread(() -> {
        bossGroup = new NioEventLoopGroup(1);
        bizGroup = new DefaultEventExecutorGroup(
            8,
            16,
            60, TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(1000)
        );
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new MessageDecoder());
                            ch.pipeline().addLast(new MessageEncoder());
                            ch.pipeline().addLast(bizGroup, masterHandler);
                        }
                    });

            LOG.info(">>> Master Netty Server 正在启动，监听端口: {}", port);
            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            LOG.error(">>> Master Netty Server 异常", e);
        } finally {
            stop();
        }
    }, "Netty-Server-Thread").start();
}
```

- [ ] **Step 2: 验证 build**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS（无输出）

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java
git commit -m "feat(master): apply DefaultEventExecutorGroup to MasterHandler pipeline"
```

---

## Task 3: 添加 shutdownGracefully

**Files:**
- Modify: `src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java:79-82`

- [ ] **Step 1: 在 stop() 方法中添加 bizGroup 关闭**

将现有 stop() 方法：

```java
@PreDestroy
public void stop() {
    if (bossGroup != null) bossGroup.shutdownGracefully();
    if (workerGroup != null) workerGroup.shutdownGracefully();
}
```

改为：

```java
@PreDestroy
public void stop() {
    if (bizGroup != null) bizGroup.shutdownGracefully();
    if (bossGroup != null) bossGroup.shutdownGracefully();
    if (workerGroup != null) workerGroup.shutdownGracefully();
}
```

- [ ] **Step 2: 验证 build**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java
git commit -m "fix(master): add bizGroup shutdownGracefully on destroy"
```

---

## Task 4: 验证

**Files:**
- None (observational verification only)

- [ ] **Step 1: 启动 Master 服务**

Run: `./mvnw spring-boot:run`
Expected: 日志中出现 `DefaultEventExecutorGroup-1-1`、`DefaultEventExecutorGroup-1-2` 等线程名（非 NioEventLoop 线程）

- [ ] **Step 2: 观察日志验证线程分离**

启动后搜索日志确认：
- `NioEventLoopGroup-2-N` — EventLoop 线程（IO）
- `DefaultEventExecutorGroup-1-N` — bizGroup 线程（handler 业务）
- `Master-LogManager-Thread` — 日志消费线程

- [ ] **Step 3: 确认 worker 连接和心跳正常**

连接 worker，触发心跳，确认 `handleHeartbeat` 在 bizGroup 线程执行

---

## Self-Review Checklist

- [ ] Spec coverage: 所有 spec 要求都有对应 task 实现
- [ ] Placeholder scan: 无 "TBD"、"TODO"、未填写的步骤
- [ ] Type consistency: DefaultEventExecutorGroup 构造参数顺序和类型与代码一致
- [ ] 文件路径准确: `src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java`
- [ ] 线程池参数: core=8, max=16, keepAlive=60s, queue=1000 与 spec 一致
