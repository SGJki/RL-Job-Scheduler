# Fix Todo List（系统完善清单）

## 1. 修复 Master 宕机行为（Worker 仍在执行任务）

### (1) Worker 续租（租约由 Worker 维护）
- [x] Worker 运行期间主动续期 Redis 租约
  - `worker:<workerId>:hb`（存活租约）
  - `worker:<workerId>:task`（占用租约，值为 taskId）
  - `task:<taskId>:workerId`（任务归属租约，值为 workerId）
- [x] Worker 任务结束后清理占用租约（删除 `worker:<workerId>:task`、`task:<taskId>:workerId`）
- [x] 约定 TTL 与续租频率（例如 hb=30s / task=120s / renew=5~10s）
- [ ] 验证：Master 宕机期间，Redis 租约仍由 Worker 续期；Master 重启后不会误判 worker 空闲

### (2) Worker 自动重连 + 自报状态（控制面恢复）
- [x] Worker 检测连接断开后自动重连 Master（指数退避或固定间隔均可）
- [x] Worker 重连成功后立刻发送心跳/注册包，携带 `currentTaskId`
- [x] Master 收到心跳后恢复 ChannelManager，并把该 worker 视为 busy（如果 currentTaskId 非空）
- [ ] 验证：Master 重启后，Worker 很快收到 `currentTaskId`

### (3) 栅栏 token / attemptId（最终保险，防重复执行）
- [ ] Redis 维护 `task:<taskId>:currentAttempt`（INCR 分配 attempt，TTL 足够长或任务结束清理）
- [x] 下发给 Worker 的 ExecuteTaskRequest 增加字段：`attempt`
- [x] Worker 上报 TaskStatusReport 时携带 `attempt`（日志流不携带）
- [x] Master 只接受“当前 attempt”的状态上报，旧 attempt 直接丢弃
- [ ] 验证：重复执行存在时，旧 attempt 的 COMPLETED/FAILED 不会覆盖最新 attempt 的最终状态/结果

### (4) Master 冷启动（减少窗口期误调度）
- [ ] Master 启动进入 warming-up（例如 10~30s），期间只入队不调度
- [ ] warming-up 条件：收到至少 N 个 worker 心跳 / 或 Redis 观测到 hb key 数量达到阈值
- [ ] warming-up 结束后启动正常调度（队列 + 对账 + 回收）
- [ ] 验证：Master 刚启动时不会把任务误发给正在执行旧任务的 worker

---

## 2. 修复“任务被遗忘”（Worker 全忙导致 PENDING 永久不动）

### (1) 调度队列（顺序执行语义）
- [ ] 调度失败时将 taskId 入队（去重）
- [ ] worker 空闲/任务结束时触发队列 pop 并下发

### (2) PENDING 对账（只入队，不直接 dispatch）
- [ ] 定时扫描 DB 的 PENDING（按 createdAt 升序），在存在 idle worker 时把 taskId 补入队列
- [ ] 目标：即使队列因异常/重启丢失，也能靠 DB 自愈把 PENDING 重新纳入队列

---

## 3. RUNNING 回收（Worker 宕机/失联导致 RUNNING 卡死）

### (1) 任务归属映射（可回收依据）
- [ ] 维护 `task:<taskId>:workerId`（带 TTL，随心跳续期）

### (2) RUNNING 回收器（只回收 + 入队）
- [ ] 定时扫描 DB RUNNING
- [ ] 若检测归属 worker 不再存活/占用租约消失，则将任务改回 PENDING 并入队
- [ ] 目标：RUNNING 不会永久卡死

---

## 4. RPC 连接与发现（避免 Worker 绑死单一 Master）

### (1) 明确连接模型（推荐：Worker -> Master 长连接）
- [ ] 约束：Master 不主动“连接 Worker”，只能在已建立连接的 Worker 池里选择 idle 并下发任务
- [ ] Worker 保持自动重连 + 自报 currentTaskId（已实现），用于 Master 重启后的控制面恢复

### (2) Worker 发现 Master（避免只连固定 host）
- [ ] 方案 A：Worker 支持 `MASTER_RPC_HOSTS`（逗号分隔多地址），断线/连接失败时轮询/退避重连
- [ ] 方案 B：Worker 接入 Nacos Discovery（非 Spring 方式也可），动态获取 `rl-master` 的可用实例并选择其 RPC 地址

### (3) Master 高可用（可选）
- [ ] 选主/单活：同一时刻只允许一个 Master 提供 RPC（避免 Worker 分裂连接）
- [ ] 统一入口：为 RPC 增加稳定入口（VIP/LB/DNS），Worker 永远连同一个入口
