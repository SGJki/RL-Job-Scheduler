# Master-Worker 弹性恢复修复文档

**修复日期：** 2026-04-03
**影响范围：** Worker 重连机制、Master 宕机恢复、任务状态一致性
**状态：** 部分完成（见下方说明）

## 修复进度

| 模块 | 状态 | 说明 |
|------|------|------|
| Part 1: Worker 重连指数退避 | ✅ 已实施 | 1秒稳定期连接确认机制已加 |
| Part 1: 连接稳定确认 | ⚠️ **待继续** | `isSuccess() = true` 后等待 1s 的修复在测试中未见效，Worker 重连仍持续以 60s 间隔重试，根因未完全定位 |
| Part 2: 任务所有权立即持久化 | ✅ 已解决 | 僵尸任务（RUNNING 永久悬停）问题已解决 |
| Part 3: Master 重启恢复逻辑增强 | ✅ 已解决 | 三段式恢复 + 心跳处理器通知丢失检测已生效 |
| Part 4: Master 启动状态重建 | ✅ 已解决 | `@PostConstruct` 启动时重建已生效 |

> ⚠️ **未完成项：** Worker 重连机制在 `isSuccess() = true` 后等待 1 秒稳定的修复仍无法解决问题。后续需要进一步排查：closeFuture 是否在 1 秒稳定期内已触发？`isReconnecting` 守卫是否有效？建议在 connect 成功后打印更细粒度的时序日志（TCP 握手完成时刻、1秒定时器触发时刻、closeFuture 触发时刻）以定位真实时序。

---

## 一、问题描述

### 1.1 故障场景

当 Worker 正在执行训练任务时，如果 Master 突然宕机会出现以下问题：

1. **Worker 无法重连**：Worker 使用固定 5 秒延迟重试，但 Master 重启后 Worker 持续重连失败，形成"惊群效应"（thundering herd）
2. **任务永久悬停**：任务在数据库中保持 `RUNNING` 状态，Worker 继续训练但 Master 无法感知
3. **资源无法释放**：Worker 的 `worker:{workerId}:task` 在 Redis 中被永久占用，导致其他任务无法分配
4. **状态不一致**：Master 重启后无法区分"任务已完成但通知丢失"和"任务仍在运行"

### 1.2 影响分析

| 影响项 | 严重程度 | 说明 |
|--------|----------|------|
| Worker 重连失效 | 高 | TCP 连接成功但应用层状态未建立 |
| 任务状态不一致 | 高 | DB=Running, Redis=无对应 Worker |
| 资源泄漏 | 中 | Redis key 永不过期，Worker 无法接新任务 |
| 恢复时间长 | 中 | 惊群效应导致 Master 启动压力增大 |

---

## 二、根因分析

### 2.1 Worker 重连问题

**原代码（WorkerAgent.java）：**

```java
channel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
    LOG.warn(">>> 与 Master 连接断开，5秒后重连...");
    closeFuture.channel().eventLoop().schedule(() -> connect(b), 5, TimeUnit.SECONDS);
});
```

**问题：**
- 固定 5 秒延迟，多个 Worker 同时重连产生惊群效应
- 无重连状态跟踪，可能出现并发重连
- 无指数退避，Master 完全启动前反复失败

### 2.2 任务所有权时间差

Master 调度任务的执行顺序：

```
1. Master 写入 task:{taskId}:workerId → Redis ✓
2. Master 发送 ExecuteTaskRequest → Netty
3. Worker 接收，设置 currentTaskId，返回确认
4. Worker 发送 ExecuteTaskResponse
5. Worker 在下次心跳（10秒间隔）时才写入 worker:{workerId}:task → Redis ✗
```

**时间差窗口：** 如果 Master 在步骤 1-4 之间宕机，`task:{taskId}:workerId` 已在 Redis 中，但 `worker:{workerId}:task` 尚未写入。恢复程序无法判断任务状态。

### 2.3 恢复盲点

原 `RunningTaskRecovery` 逻辑：

```java
// 原逻辑：只检查 taskOwnerKey 是否存在
String workerId = redisTemplate.opsForValue().get(taskWorkerKey(taskId));
if (workerId == null || workerId.isBlank()) {
    continue;  // 无 owner → 跳过（实际上这是调度中断，应标记 PENDING）
}
Boolean hbAlive = redisTemplate.hasKey("worker:" + workerId + ":hb");
if ((hbAlive != null && hbAlive) && (taskKeyExists != null && taskKeyExists)) {
    continue;  // Worker 存活且 taskKey 存在 → 跳过（但无法判断任务是否真的在运行）
}
```

**盲点：**
- `taskOwnerKey` 存在但 `taskKey` 不存在 → 无法判断（已完成？中断？）
- `taskOwnerKey` 不存在 → 原逻辑直接跳过（实际是调度中断，应重置）
- Master 重启后不知道 Worker 的 `currentTaskId` 是否是重启前的残留

### 2.4 僵尸任务问题

当任务完成但 `TASK_STATUS_REPORT` 丢失时：

```
Worker: currentTaskId = ""（已清空）
Worker: 发送心跳（currentTaskId = ""）
Master: 看到空闲 Worker，尝试分配新任务
问题: 原任务可能分配给其他 Worker，而原任务仍在 Redis 中有记录
```

---

## 三、诊断过程

### 3.1 诊断方法

1. **日志追踪**：分析 Worker 日志中重连行为的时序
2. **Redis 键值分析**：检查宕机前后 Redis 中的 key 状态
3. **代码路径分析**：追踪 Master 宕机时各模块的执行状态
4. **状态机分析**：绘制 Worker-Master-Netty-Redis 交互时序图

### 3.2 Redis Key 分析

| Key 格式 | TTL | 创建时机 | 说明 |
|----------|-----|----------|------|
| `worker:{workerId}:hb` | 30s | 心跳时 | Worker 存活标志 |
| `worker:{workerId}:task` | 120s | 下次心跳时（原来）→ 任务接收时（修复后） | Worker 当前任务 |
| `task:{taskId}:workerId` | 120s | Master 调度时 | 任务所属 Worker |

### 3.3 关键时间点分析

```
T0: Master 写入 taskOwnerKey（Redis）
T0+100ms: Netty 消息发送
T0+200ms: Worker 接收，设置 currentTaskId
T0+10s: Worker 发送心跳（写入 workerTaskKey）

Gap [T0, T0+10s]: taskOwnerKey 存在，workerTaskKey 不存在
→ 修复后 persistTaskStart() 在 T0+205ms（任务接收时）立即写入 workerTaskKey
→ Gap 缩小到 ~0ms（原子性）
```

---

## 四、执行方案

### 4.1 方案设计原则

1. **消除时间差**：Worker 接收任务时立即写入 Redis（不在下次心跳时）
2. **指数退避**：防止惊群效应，给予 Master 充分启动时间
3. **单次重连**：防止并发重连浪费资源
4. **双重确认**：同时检查 `taskOwnerKey` 和 `workerTaskKey` 判断任务真实状态
5. **启动即恢复**：Master 启动时主动重建状态，不依赖定时扫描

### 4.2 方案对比

| 方案 | 优点 | 缺点 |
|------|------|------|
| A: 只修 Worker 重连 | 简单 | 任务状态不一致问题未解决 |
| B: 只修恢复逻辑 | 无需改 Worker | 无法处理 workerTaskKey 缺失的边界情况 |
| **C: 两者都修（采用）** | 彻底解决重连和恢复问题 | 改动量较大 |

---

## 五、实际修改逻辑

### 5.1 Part 1: Worker 重连指数退避（WorkerAgent.java）

**修改常量：**

```java
private static final long INITIAL_RECONNECT_DELAY_SECONDS = 1;    // 初始延迟 1 秒
private static final long MAX_RECONNECT_DELAY_SECONDS = 60;       // 最大 60 秒
private static final double BACKOFF_MULTIPLIER = 2.0;            // 2 倍退避
private static final double JITTER_FACTOR = 0.25;                // ±25% 抖动
```

**延迟序列（无抖动示例）：**

```
第1次: 1s → 2s → 4s → 8s → 16s → 32s → 60s → 60s → ...
```

**核心逻辑：**

```java
private void connect(Bootstrap b) {
    if (isReconnecting) {
        return;  // 防止并发重连
    }
    isReconnecting = true;
    b.connect(masterHost, masterPort).addListener((ChannelFutureListener) future -> {
        if (future.isSuccess()) {
            currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;  // 成功后重置
            isReconnecting = false;
            // ... 连接成功处理
        } else {
            scheduleReconnect(b);  // 指数退避重连
        }
    });
}

private void scheduleReconnect(Bootstrap b) {
    long delayWithJitter = calculateDelayWithJitter(currentReconnectDelay);
    currentReconnectDelay = Math.min(
            (long) (currentReconnectDelay * BACKOFF_MULTIPLIER),
            MAX_RECONNECT_DELAY_SECONDS
    );
    channel.eventLoop().schedule(() -> {
        isReconnecting = false;
        connect(b);
    }, delayWithJitter, TimeUnit.SECONDS);
}
```

### 5.1.1 补充修复：连接稳定确认机制

**问题现象：** `isSuccess() = true` 仅表示 TCP 三次握手完成，不代表应用层就绪。Master 重启时，TCP 监听已开放但 Spring/Netty 尚未完成初始化，导致 Worker 连接后立即被远程关闭，触发 `closeFuture`，进而以指数退避重复重连，最终稳定在 60s 延迟。

**问题时序：**

```
Worker: connect() → SYN
Master: TCP 监听就绪，SYN-ACK 返回
Worker: isSuccess() = true → 注册 closeFuture
        → channel = future.channel()
        → isReconnecting = false
Master: Spring 上下文初始化中，Netty pipeline 未就绪
        → 心跳无法处理或 NPE
        → TCP RST 发送给 Worker
Worker: closeFuture 触发 → scheduleReconnect(currentReconnectDelay=60)
        → 连接被关闭后立即以 60s 延迟重试
        → 循环...
```

**修复后的连接确认逻辑：**

```java
private void connect(Bootstrap b) {
    if (isReconnecting) {
        return;
    }
    isReconnecting = true;
    final Channel[] pendingChannel = new Channel[1];  // 数组包装避免 lambda 捕获

    b.connect(masterHost, masterPort).addListener((ChannelFutureListener) future -> {
        if (!future.isSuccess()) {
            scheduleReconnect(b, 0);
            return;
        }

        // TCP 握手成功，等待 1 秒确认应用层已就绪
        pendingChannel[0] = future.channel();
        LOG.info(">>> TCP 握手成功，等待应用层就绪...");

        pendingChannel[0].eventLoop().schedule(() -> {
            if (!pendingChannel[0].isActive()) {
                // 1 秒内连接失效 → 立即重连，不等待指数退避
                LOG.warn(">>> 连接不稳定（1秒内失效），立即重连...");
                pendingChannel[0].close();
                scheduleReconnect(b, 0);
                return;
            }

            // 连接真正稳定，注册 closeFuture
            channel = pendingChannel[0];
            currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;
            isReconnecting = false;
            LOG.info(">>> 成功连接到 Master（连接已稳定）!");

            channel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
                if (isReconnecting) {
                    return; // 防止双重触发
                }
                scheduleReconnect(b, 0);
            });
        }, 1, TimeUnit.SECONDS);
    });
}
```

**关键改进：**

1. **1 秒稳定期**：`isSuccess()` 后等待 1 秒，让 Master 有充足时间完成 Spring 启动
2. **主动检测失效**：稳定期内如果 `isActive() = false`，立即重连而不是等到 `closeFuture`
3. **`isReconnecting` 守卫**：closeFuture 中检查 `isReconnecting` 标志，防止与 `scheduleReconnect` 双重触发

### 5.2 Part 2: 任务所有权立即持久化（WorkerHandler + RedisLeaseManager）

**修改点 1：WorkerHandler.handleExecuteTask()**

```java
private void handleExecuteTask(ChannelHandlerContext ctx, ExecuteTaskRequest req) {
    String taskId = req.getTaskId();
    this.lastTaskId = taskId;
    this.currentTaskId = taskId;
    this.currentAttempt = req.getAttempt();

    // ★ 在启动 Python 线程之前立即写入 Redis（原子性）
    if (leaseManager != null) {
        leaseManager.persistTaskStart(taskId);
    }

    // 1. 返回响应
    // 2. 异步执行 Python 脚本
    new Thread(() -> runPythonTask(ctx, req), "Task-Executor-" + taskId).start();
}
```

**修改点 2：WorkerHandler.reportStatus()**

```java
private void reportStatus(ChannelHandlerContext ctx, String taskId, String status, String errorMsg) {
    // ★ 立即清除 Redis 任务所有权
    if (leaseManager != null) {
        leaseManager.clearTask(taskId);
    }
    // 发送状态报告...
}
```

**修改点 3：RedisLeaseManager 新增方法**

```java
public void persistTaskStart(String taskId) {
    // 同时写入两个 key，保证双向一致性
    commands.setex(taskKey(), taskTtlSeconds, taskId);           // worker:{workerId}:task
    commands.setex(taskOwnerKey(taskId), taskTtlSeconds, workerId);  // task:{taskId}:workerId
}

public void clearTask(String taskId) {
    commands.del(taskKey());
    if (taskId != null && !taskId.isBlank()) {
        commands.del(taskOwnerKey(taskId));
    }
}
```

**效果：时间差从 ~10 秒降低到 ~0 秒**

### 5.3 Part 3: Master 重启恢复增强（RunningTaskRecovery + MasterHandler）

**RunningTaskRecovery 修复后的逻辑：**

```java
for (TrainingTask task : running) {
    String taskId = task.getId();
    String workerId = redisTemplate.opsForValue().get(taskWorkerKey(taskId));

    if (workerId == null || workerId.isBlank()) {
        // ★ 情况1: taskOwnerKey 不存在 → 调度中断，从未真正开始
        task.setStatus("PENDING");
        taskMapper.updateById(task);
        schedulerService.enqueueTask(taskId);
        LOG.info(">>> [Recovery] 任务 [{}] 从未分发（无 owner key），标记为 PENDING", taskId);
        continue;
    }

    Boolean hbAlive = redisTemplate.hasKey("worker:" + workerId + ":hb");
    if (hbAlive != null && hbAlive) {
        // ★ 情况2: Worker 存活 → 任务在运行或刚完成（通知可能丢失）
        // 保持 RUNNING，由心跳处理器判断
        continue;
    }

    // ★ 情况3: Worker 已死 → 任务孤立
    task.setStatus("PENDING");
    taskMapper.updateById(task);
    schedulerService.enqueueTask(taskId);
    schedulerService.releaseTaskOwner(taskId);
    LOG.info(">>> [Recovery] 任务 [{}] 的 Worker [{}] 已失活，标记为 PENDING", taskId, workerId);
}
```

**MasterHandler.handleHeartbeat() 增强：**

```java
private void handleHeartbeat(ChannelHandlerContext ctx, HeartbeatRequest req) {
    String currentTaskId = req.getCurrentTaskId();

    if (currentTaskId != null && !currentTaskId.isEmpty()) {
        // 续期 TTL
        schedulerService.renewTaskOwnerTtl(currentTaskId);

        // ★ 检查 taskOwnerKey 是否匹配（通知丢失检测）
        String ownerKey = "task:" + currentTaskId + ":workerId";
        String ownerWorkerId = redisTemplate.opsForValue().get(ownerKey);
        if (ownerWorkerId == null || !ownerWorkerId.equals(workerId)) {
            // taskOwnerKey 缺失 → 任务已完成但通知丢失
            TrainingTask task = taskMapper.selectById(currentTaskId);
            if (task != null && "RUNNING".equals(task.getStatus())) {
                task.setStatus("COMPLETED");
                task.setCompletedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                LOG.info(">>> [Heartbeat] 任务 [{}] 实际已完成（通知丢失），强制标记为 COMPLETED", currentTaskId);
                messagingTemplate.convertAndSend("/topic/tasks", task);
            }
            schedulerService.tryDispatchQueuedTaskToWorker(workerId);
        }
    } else {
        // ★ Worker 空闲时检查并修复孤儿 RUNNING 任务
        checkAndFixStaleRunningTasks(workerId);
        schedulerService.tryDispatchQueuedTaskToWorker(workerId);
    }
}
```

### 5.4 Part 4: Master 启动状态重建（SchedulerService）

```java
@PostConstruct
public void reconstructWorkerTasksFromRedis() {
    LOG.info(">>> [SchedulerService] 开始从 Redis 重建 Worker 任务状态...");

    Set<String> workerTaskKeys = redisTemplate.keys("worker:*:task");
    for (String workerTaskKey : workerTaskKeys) {
        String workerId = workerTaskKey.split(":")[1];
        String taskId = redisTemplate.opsForValue().get(workerTaskKey);

        // 检查 taskOwnerKey 是否匹配
        String ownerKey = taskWorkerKey(taskId);
        String ownerWorkerId = redisTemplate.opsForValue().get(ownerKey);
        if (ownerWorkerId == null || !ownerWorkerId.equals(workerId)) {
            // 调度被中断，标记为 PENDING 重新入队
            TrainingTask task = taskMapper.selectById(taskId);
            if (task != null && "RUNNING".equals(task.getStatus())) {
                task.setStatus("PENDING");
                taskMapper.updateById(task);
                enqueueIfEnabled(taskId);
                LOG.info(">>> [Recovery] 启动重建：任务 [{}] 调度中断，标记为 PENDING", taskId);
            }
        }
    }
}
```

---

## 六、修改文件清单

| 文件路径 | 修改类型 | 修改内容 |
|----------|----------|----------|
| `worker/WorkerAgent.java` | 修改 | 指数退避重连 + isReconnecting 状态跟踪 + **1秒稳定期连接确认** |
| `worker/netty/WorkerHandler.java` | 修改 | 注入 LeaseManager，任务接收时立即持久化，报告时清除 |
| `worker/redis/RedisLeaseManager.java` | 修改 | 新增 persistTaskStart() 和 clearTask() 方法 |
| `master/service/RunningTaskRecovery.java` | 修改 | 三段式恢复逻辑（中断/存活/死亡） |
| `master/netty/MasterHandler.java` | 修改 | 心跳中检测通知丢失，新增 checkAndFixStaleRunningTasks() |
| `master/service/SchedulerService.java` | 修改 | 新增 @PostConstruct reconstructWorkerTasksFromRedis() |

---

## 七、Redis 数据流（修复后）

### 7.1 正常任务执行流程

```
Worker 接收任务
  → persistTaskStart(taskId)
    → SETEX worker:{workerId}:task TTL=120s
    → SETEX task:{taskId}:workerId TTL=120s
  → 启动 Python 训练线程
  → (训练中) heartbeat 每 10s 续期 TTL
  → (完成) reportStatus()
    → clearTask(taskId)
      → DEL worker:{workerId}:task
      → DEL task:{taskId}:workerId
    → 发送 TASK_STATUS_REPORT
```

### 7.2 Master 宕机恢复流程

```
Master 重启
  → @PostConstruct reconstructWorkerTasksFromRedis()
    → 扫描 worker:*:task
    → 检查 taskOwnerKey 是否匹配
    → 不匹配 → 标记 PENDING，重新入队

同时 RunningTaskRecovery 每 5s 扫描
  → taskOwnerKey 不存在 → 标记 PENDING
  → Worker hb 不存在 → 标记 PENDING，释放 owner

Worker 重连
  → 指数退避（1s → 2s → 4s → ...）
  → 发送心跳（携带 currentTaskId）
  → Master 检查 taskOwnerKey 匹配性
  → 匹配 → 任务正常运行
  → 不匹配 → 标记 COMPLETED（通知丢失）
```

---

## 八、测试用例

### 8.1 单元测试：指数退避计算

```java
@Test
void testExponentialBackoffWithJitter() {
    WorkerAgent agent = new WorkerAgent("localhost", 9000, "test-worker");
    long baseDelay = 1;

    // 计算 5 次延迟，验证指数增长
    long delay1 = calculateDelayWithJitter(1);
    long delay2 = calculateDelayWithJitter(2);
    long delay3 = calculateDelayWithJitter(4);
    long delay4 = calculateDelayWithJitter(8);

    // 抖动范围：delay * (1 - JITTER_FACTOR) <= actual <= delay * (1 + JITTER_FACTOR)
    assertTrue(delay1 >= 0.75 && delay1 <= 1.25);
    assertTrue(delay2 >= 1.5 && delay2 <= 2.5);
    assertTrue(delay3 >= 3.0 && delay3 <= 5.0);

    // 最大延迟不超过 MAX_RECONNECT_DELAY_SECONDS
    long delay60 = calculateDelayWithJitter(60);
    assertTrue(delay60 <= 75); // 60 * 1.25
}
```

### 8.2 单元测试：persistTaskStart 原子性

```java
@Test
void testPersistTaskStartWritesBothKeys() {
    RedisLeaseManager manager = new RedisLeaseManager("worker-1");

    manager.persistTaskStart("task-123");

    // 验证两个 key 都存在
    assertEquals("task-123", redis.get("worker:worker-1:task"));
    assertEquals("worker-1", redis.get("task:task-123:workerId"));

    manager.clearTask("task-123");

    // 验证两个 key 都被删除
    assertNull(redis.get("worker:worker-1:task"));
    assertNull(redis.get("task:task-123:workerId"));
}
```

### 8.3 集成测试：Master 宕机后任务恢复

```java
@Test
void testTaskRecoversAfterMasterRestart() {
    // 1. 提交任务，Worker 接收并开始训练
    String taskId = submitTask();
    worker.receiveTask(taskId);
    assertEquals("RUNNING", db.getTaskStatus(taskId));

    // 2. Master 宕机（模拟）
    master.stop();

    // 3. Worker 继续训练（currentTaskId 不变）
    assertEquals(taskId, worker.getCurrentTaskId());
    assertTrue(redis.exists("task:" + taskId + ":workerId"));
    assertTrue(redis.exists("worker:" + worker.getId() + ":task"));

    // 4. Master 重启
    master.restart();

    // 5. Worker 重连（指数退避后成功）
    worker.reconnectEventually();

    // 6. 验证任务仍被追踪为 RUNNING
    assertEquals("RUNNING", db.getTaskStatus(taskId));
    // taskOwnerKey 存在且匹配，任务保持 RUNNING
}
```

### 8.4 集成测试：调度中断场景

```java
@Test
void testTaskRequeuedWhenDispatchInterrupted() {
    // 1. Master 写入 taskOwnerKey（Redis）
    redis.set("task:task-X:workerId", "worker-1");

    // 2. Master 宕机（尚未发送 Netty 消息）
    master.stop();

    // 3. RunningTaskRecovery 扫描
    recovery.recoverOrphanRunningTasks();

    // 4. 验证：taskOwnerKey 存在但 taskKey 不存在
    // → 任务从未分发，标记为 PENDING
    assertEquals("PENDING", db.getTaskStatus("task-X"));
    assertTrue(queue.contains("task-X"));
}
```

### 8.5 集成测试：通知丢失场景

```java
@Test
void testTaskMarkedCompletedWhenNotificationLost() {
    // 1. Worker 完成任务，清除 Redis key
    worker.clearTask("task-Y");
    // 此时：DB=RUNNING, Redis taskOwnerKey=不存在

    // 2. Master 宕机（未收到 TASK_STATUS_REPORT）
    master.stop();

    // 3. Worker 发送心跳（currentTaskId = ""，已清空）
    worker.sendHeartbeat(currentTaskId: "");

    // 4. Master 重启，Worker 重连
    master.restart();
    worker.reconnect();

    // 5. RunningTaskRecovery 扫描
    // → taskOwnerKey 不存在 → 标记 PENDING

    // 6. Worker 空闲，Master 分发新任务
    // （旧任务标记 PENDING 后可被重新调度）
}
```

### 8.6 集成测试：惊群效应防止

```java
@Test
void testNoThunderingHerdOnMasterRestart() {
    // 1. 启动 10 个 Worker 连接 Master
    List<WorkerAgent> workers = startWorkers(10);
    workers.forEach(w -> assertTrue(w.isConnected()));

    // 2. Master 宕机
    master.stop();

    // 3. 记录每个 Worker 首次重连时间
    Map<WorkerAgent, Long> firstReconnectTimes = new HashMap<>();
    workers.forEach(w -> {
        w.onReconnectAttempt(() -> firstReconnectTimes.put(w, System.currentTimeMillis()));
    });

    // 4. Master 重启
    master.restart();

    // 5. 验证：10 个 Worker 的首次重连时间分散在 1s~4s 范围内
    // （指数退避 + 抖动，而非全部同时 1s）
    long minTime = firstReconnectTimes.values().stream().min(Long::compare).get();
    long maxTime = firstReconnectTimes.values().stream().max(Long::compare).get();
    assertTrue(maxTime - minTime > 500); // 至少有 500ms 分散
}
```

---

## 九、验证检查清单

- [ ] Worker 重连使用指数退避（1s → 2s → 4s → ... → 60s）
- [ ] 重连包含 ±25% 随机抖动
- [ ] `isReconnecting` 标志防止并发重连
- [ ] TCP 握手成功后等待 **1 秒稳定期**才注册 `closeFuture`
- [ ] 稳定期内连接失效时**立即重连**（不等 `closeFuture`）
- [ ] `persistTaskStart()` 在 Python 线程启动前调用
- [ ] `clearTask()` 在 `reportStatus()` 中调用
- [ ] `taskOwnerKey` 和 `workerTaskKey` 同时写入/删除
- [ ] `RunningTaskRecovery` 区分三种状态（中断/存活/死亡）
- [ ] `checkAndFixStaleRunningTasks()` 修复孤儿 RUNNING 任务
- [ ] `@PostConstruct` 在 Master 启动时执行状态重建
- [ ] 心跳处理器检测 taskOwnerKey 匹配性
- [ ] 编译通过（`./mvnw compile`）
- [ ] 测试通过（`./mvnw test`）
