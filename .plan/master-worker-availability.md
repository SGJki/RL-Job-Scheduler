# Master-Worker 可用性改进方案

**Problem solved**: 消除 Redis 单点故障导致的系统冻结问题，实现降级运行 + 最终一致性。当 Redis 可用时系统以 Redis 为加速层；当 Redis 故障时系统降级为纯 DB 操作；Redis 恢复后通过增量同步恢复完整功能。

---

## 1. 架构定位

| 组件 | 角色 | 说明 |
|------|------|------|
| Redis | 加速层 | 协调、队列、心跳、缓存 |
| DB | 真相源 | 持久化、兜底 |
| dirty_tasks | 同步追踪表 | 记录需要从 DB 同步到 Redis 的任务 |

**设计目标**: 性能优先（Redis-first），故障时可降级，最终一致性。

---

## 2. 数据模型

### 2.1 新增表: dirty_tasks

```sql
CREATE TABLE dirty_tasks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     VARCHAR(64) NOT NULL COMMENT '任务ID',
    sync_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                 COMMENT 'PENDING-待同步, SYNCING-同步中, COMPLETED-已同步',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sync_status (sync_status),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脏任务同步表';
```

**设计说明**:
- `sync_status`: PENDING 表示待同步，SYNCING 表示正在同步，COMPLETED 表示已同步（可清理）
- `updated_at`: 用于冲突解决，比较 Redis 键的 TTL 或最后更新时间

### 2.2 现有表保持不变

- `TrainingTask` 表: 任务主表，存储完整任务信息
- Redis Key 结构保持不变: `worker:{id}:task`, `task:{taskId}:workerId`, `worker:{id}:hb`

---

## 3. 正常路径 (Redis-first)

### 3.1 写路径

```
TrainingService.submitTask()
    │
    ├─► 1. taskMapper.insert(task)        → DB: PENDING
    │
    ├─► 2. Redis 写 (tryPreemptWorker + registerTaskOwner)
    │       ├─ 成功 → 继续
    │       └─ 失败 → 插入 dirty_tasks (sync_status=PENDING)，继续（不阻塞）
    │
    ├─► 3. dispatchTask() → Netty 消息发给 Worker
    │
    └─► 4. taskMapper.updateById(status=RUNNING)
            ├─ 成功 → 继续
            └─ 失败 → 插入 dirty_tasks (sync_status=PENDING)，继续
```

**关键点**:
- Redis 写失败不阻塞主流程，只标记 dirty
- DB 写失败会阻塞（因为 DB 是持久化的底线）
- 任务完成时同样模式: 先写 DB，再写 Redis，失败则 dirty

### 3.2 读路径 (Cache-Aside + Fallback)

```
读任务状态:
    │
    ├─► 1. Redis 读 → 命中则返回
    │
    └─► 2. Redis 未命中 → DB 读 + 回填 Redis
                ├─ 回填成功 → 返回
                └─ 回填失败 → 插入 dirty_tasks，返回 DB 数据
```

### 3.3 心跳处理

```
handleHeartbeat():
    │
    ├─► 写 Redis: worker:{id}:hb = alive (TTL 30s)
    │
    ├─► Redis 写失败? → 降级检查: 读写 DB 的 task:{taskId}:workerId
    │                   (此时无需写 dirty，因为是读操作)
    │
    └─► 其他逻辑保持不变 (续期 taskOwnerKey, 检查 stale 任务等)
```

---

## 4. 降级路径 (Redis 不可用)

### 4.1 检测机制

```java
// Redis 连接失败时自动触发降级
public class RedisConnectionListener implements FailureListener {
    @Override
    public void onFailure(RedisConnectionException e) {
        redisAvailable.set(false);
        LOG.warn(">>> [Redis] 连接失败，切换到降级模式");
    }
}

// Spring Data Redis 自动重连成功后恢复
@EventListener
public void onRedisReconnected(RedisConnectionRecoveredEvent e) {
    redisAvailable.set(true);
    LOG.info(">>> [Redis] 已恢复，触发增量同步");
    dirtyTaskSyncService.triggerFullSync();
}
```

### 4.2 降级行为

```
降级模式开启后:
    │
    ├─► 写操作: 直接写 DB，插入 dirty_tasks
    │
    ├─► 读操作: 直接读 DB
    │
    ├─► 队列操作: 使用 DB 模拟队列 (SELECT ... ORDER BY created_at LIMIT 1)
    │
    └─► 心跳: 只更新 DB，不写 Redis
```

### 4.3 性能影响

| 操作 | 正常延迟 | 降级延迟 |
|------|---------|---------|
| 写任务 | ~2ms | ~20ms |
| 读任务 | ~1ms | ~20ms |
| 调度 | ~5ms | ~25ms |

---

## 5. 恢复路径 (Redis 恢复)

### 5.1 触发时机

1. **立即触发**: Redis 重连成功后，立即执行全量同步
2. **定时兜底**: 每 30 秒扫描 dirty_tasks 表，处理积压

### 5.2 同步流程

```
RedisConnectionListener.onRedisReconnected()
    │
    └─► dirtyTaskSyncService.syncAll()
            │
            ├─► 1. 查询 dirty_tasks WHERE sync_status IN ('PENDING', 'SYNCING')
            │       ORDER BY updated_at ASC
            │       LIMIT 100
            │
            ├─► 2. 逐条处理:
            │       │
            │       ├─► 读取 DB 任务数据
            │       │
            │       ├─► 读取 Redis 当前值 (如果存在)
            │       │
            │       ├─► 冲突解决:
            │       │   ├─ Redis 不存在 → 直接写入
            │       │   ├─ Redis 存在且 updated_at ≥ DB.updated_at → 跳过
            │       │   └─ Redis 存在且 updated_at < DB.updated_at → 覆盖
            │       │
            │       └─► 更新 dirty_tasks.sync_status = 'COMPLETED'
            │
            └─► 3. 如果还有积压，schedule下一个 batch (30ms 后)
```

### 5.3 冲突解决策略 C (时间戳比较)

```java
private boolean shouldSync(TaskRecord dbRecord, String redisValue) {
    if (redisValue == null) {
        return true; // Redis 不存在，必须同步
    }

    TaskRecord redisRecord = parseTaskRecord(redisValue);
    if (redisRecord == null) {
        return true; // Redis 数据损坏，必须同步
    }

    // 比较更新时间，Redis 更旧才同步
    return dbRecord.getUpdatedAt().isAfter(redisRecord.getUpdatedAt());
}
```

---

## 6. 组件改动清单

### 6.1 新增组件

| 组件 | 职责 | 文件位置 |
|------|------|---------|
| dirty_tasks 表 | 同步追踪 | schema.sql |
| DirtyTaskMapper | dirty_tasks DB 操作 | master/mapper/DirtyTaskMapper.java |
| DirtyTaskService | dirty_tasks 业务逻辑 | master/service/DirtyTaskService.java |
| RedisConnectionListener | Redis 故障/恢复监听 | master/redis/RedisConnectionListener.java |
| DirtyTaskSyncService | 后台同步进程 | master/service/DirtyTaskSyncService.java |

### 6.2 改动组件

| 组件 | 改动点 |
|------|--------|
| SchedulerService | Redis-first 写 + dirty 追踪 + 降级读 |
| TrainingService | 写时 dirty 标记 |
| MasterHandler | 心跳时降级处理 |
| RedisLeaseManager | 写失败时返回状态 |

### 6.3 保留组件（适配）

| 组件 | 说明 |
|------|------|
| RunningTaskRecovery | 保持现有逻辑，只在 Redis 不可用时调整行为 |
| PendingTaskReconciler | 降级时使用 DB 队列 |
| 3 Key 机制 | 保留，作为 Redis 层实现 |

---

## 7. 配置项

```yaml
scheduler:
  redis:
    fallback:
      enabled: true                    # 是否启用降级模式
      sync-batch-size: 100             # 同步批次大小
      sync-interval-ms: 30000          # 兜底同步间隔
      redis-key-ttl-seconds: 120       # Redis Key TTL (用于冲突比较)
```

---

## 8. 故障场景覆盖

| 故障场景 | 检测方式 | 恢复机制 | 恢复时间 |
|---------|---------|---------|---------|
| Worker 宕机 | 心跳 TTL (30s) | RunningTaskRecovery | ~35s |
| Master 宕机 | @PostConstruct | reconstructWorkerTasksFromRedis | ~120s |
| Redis 宕机 | Redis 连接失败 | 降级运行 + Redis 恢复后 sync | ~0 (立即降级) |
| DB 宕机 | DB 连接失败 | 写入阻塞 | 取决于 DB 恢复 |
| Worker + Redis | 心跳 TTL | RunningTaskRecovery | ~35s |
| Master + Redis | 重启 + 连接检测 | reconstruct + sync | ~120s |
| Redis + DB | DB 连接失败 | **人工介入** | 不确定 |

---

## 9. 迁移策略

### 阶段 1: 基础设施 (无破坏性改动)
- [ ] 创建 dirty_tasks 表
- [ ] 实现 RedisConnectionListener
- [ ] 实现 DirtyTaskService

### 阶段 2: 写路径改造
- [ ] SchedulerService 改造: Redis-first + dirty 追踪
- [ ] TrainingService 改造: 写失败时标记 dirty

### 阶段 3: 读路径 + 降级
- [ ] 读路径改造: cache-aside + fallback
- [ ] 心跳降级处理
- [ ] RunningTaskRecovery 适配

### 阶段 4: 后台同步
- [ ] DirtyTaskSyncService 实现
- [ ] 定时扫描兜底

### 阶段 5: 测试 + 验证
- [ ] 单元测试
- [ ] Redis 故障模拟测试
- [ ] 降级运行验证

---

## 10. 未解决的问题

| 问题 | 说明 | 状态 |
|------|------|------|
| channelInactive TODO | Worker 断开时未触发清理 | 待处理 |
| Redis 消息队列升级 | 未来用 Redis 队列替代内存队列 | Future |
| 多 Master 支持 | 当前单节点，未来需分布式协调 | Future |

---

## 附录: Redis Key TTL 设计

用于冲突解决的 TTL 设计:

| Key | TTL | 用途 |
|-----|-----|------|
| `worker:{id}:task` | 120s | 续期用于冲突判断 |
| `task:{taskId}:workerId` | 120s | 续期用于冲突判断 |
| `worker:{id}:hb` | 30s | 心跳检测 |

**冲突解决逻辑**:
- 每次续期时，Redis 的 TTL 刷新意味着 "这个键被最近更新过"
- 同步时比较 `dirty_tasks.updated_at` vs `Redis key TTL 剩余时间`
- 如果 Redis 键是新鲜的（TTL > 某个阈值），认为它比 DB 更新，跳过同步
