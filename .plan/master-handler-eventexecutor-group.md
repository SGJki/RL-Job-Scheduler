# MasterHandler EventExecutorGroup 隔离改进

**Problem solved**: MasterHandler 运行在 Netty EventLoop 上，其同步 IO 操作（Redis/MySQL）会阻塞 EventLoop，导致同一 WorkerGroup 上的所有 channel 全部被拖累。

## Goal

将 MasterHandler 的业务执行从 EventLoop 线程隔离到独立线程池，EventLoop 只负责快速分发消息。

## Current State

```
NioEventLoopGroup (workerGroup)
  ├── MessageDecoder  ← EventLoop（快，无阻塞）
  ├── MessageEncoder  ← EventLoop（快，无阻塞）
  └── MasterHandler   ← EventLoop（慢！Redis/MySQL 同步 IO 阻塞）
```

## Design

### Architecture

```
Worker 发送数据
    ↓
EventLoop 接收数据，decode 成 NettyMessage
    ↓
DefaultEventExecutorGroup[0-N] 取走 MasterHandler.channelRead0()
    ↓
EventLoop 被释放，可处理其他 channel 的 IO
    ↓
MasterHandler 在 bizGroup 线程执行 → Redis/MySQL 等阻塞操作
```

### Pipeline 改造

**Before:**
```java
ch.pipeline().addLast(new MessageDecoder());
ch.pipeline().addLast(new MessageEncoder());
ch.pipeline().addLast(masterHandler);
```

**After:**
```java
ch.pipeline().addLast(new MessageDecoder());
ch.pipeline().addLast(new MessageEncoder());
ch.pipeline().addLast(bizGroup, masterHandler);
```

### 线程池配置

```java
EventExecutorGroup bizGroup = new DefaultEventExecutorGroup(
    8,                                    // corePoolSize
    16,                                   // maximumPoolSize (保守：CPU × 2)
    60, TimeUnit.SECONDS,                 // keepAliveTime
    new LinkedBlockingQueue<>(1000)        // 任务队列容量
);
```

配置说明：
- **corePoolSize=8**：常态下保持 8 个线程
- **maximumPoolSize=16**：突发情况下最多扩到 16 线程（保守方案）
- **keepAliveTime=60s**：空闲线程 60 秒后回收
- **queue=1000**：任务堆积上限，超出后 DefaultEventExecutorGroup 默认拒绝

### Spring Bean 上下文说明

- `masterHandler` 是 Spring `@Component`，持有 `@Autowired` 注入的 Bean 实例引用（`taskMapper`、`redisTemplate` 等）
- Bean 实例是单例，**线程安全**，在任何线程调用均无问题
- 不使用 `@Transactional`，各操作本来就是独立 AutoCommit，不受影响

## Implementation Steps

- [ ] 在 `MasterNettyServer` 中声明 `EventExecutorGroup bizGroup` 字段
- [ ] 在 `start()` 方法中初始化 `bizGroup`（new DefaultEventExecutorGroup 配置）
- [ ] 在 Pipeline 配置中改为 `ch.pipeline().addLast(bizGroup, masterHandler)`
- [ ] 在 `stop()` 方法中添加 `bizGroup.shutdownGracefully()`
- [ ] 验证：重启 Master，观察日志确认 bizGroup 线程名（如 `DefaultEventExecutorGroup-1-1`）出现在 handler 日志中

## Files Changed

- `src/main/java/org/sgj/rljobscheduler/master/netty/MasterNettyServer.java`

## Verification

1. 启动 Master 服务
2. 连接 Worker，触发心跳和任务状态上报
3. 观察日志线程名：
   - `NettyServer-Thread` — ServerBootstrap 线程
   - `DefaultEventExecutorGroup-1-N` — bizGroup 线程（handler 业务执行）
   - `NioEventLoopGroup-2-N` — workerGroup 线程（IO 处理）
