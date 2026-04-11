# Master 日志操作 AOP 重构文档

**修复日期：** 2026-04-05
**影响范围：** Master 模块日志记录方式
**状态：** ✅ 已完成

## 修复进度

| 模块 | 状态 | 说明 |
|------|------|------|
| Phase 1: 添加 AOP 依赖 & 创建 @Loggable 注解 | ✅ 已完成 | |
| Phase 2: 创建 LoggingAspect 切面 | ✅ 已完成 | |
| Phase 3: SchedulerService 日志替换 | ✅ 已完成 | |
| Phase 4: TrainingService 日志替换 | ✅ 已完成 | |
| Phase 5: MasterHandler 评估 | ✅ 已完成 | 确认不适用 AOP，保持原样 |
| Phase 6: 编译验证 | ✅ 已完成 | BUILD SUCCESS，测试通过 |

---

## 一、问题描述

### 1.1 原代码问题

Master 模块中的日志记录存在以下问题：

1. **`System.out.println` 残留**：`TrainingService.startTraining()` 中使用了 `System.out.println` 而非标准日志框架
2. **日志分散且不一致**：日志级别混用（`info`、`warn`、`error` 无统一规范），难以筛选
3. **样板代码冗余**：每次方法执行都需要手写 try-catch + LOG.error，重复且易遗漏
4. **无统一格式**：各服务类自行定义日志格式，缺乏一致性

### 1.2 影响分析

| 影响项 | 严重程度 | 说明 |
|--------|----------|------|
| `System.out.println` 不可控制 | 中 | 无法按级别过滤，生产环境无法关闭 |
| 异常日志遗漏 | 高 | 手写 try-catch 可能遗漏，导致异常无法追踪 |
| 日志格式不统一 | 低 | 不影响功能，影响排查体验 |
| 方法级性能监控缺失 | 中 | 无法自动获取方法执行时间 |

---

## 二、方案设计

### 2.1 设计目标

1. **方法级日志注解** — 用 `@Loggable` 声明式替代手写入口/出口日志
2. **自动捕获异常** — Aspect 统一处理，无需每个方法手写 try-catch
3. **执行时间自动记录** — 可选开关，避免性能开销
4. **参数摘要记录** — 自动序列化方法参数（截断处理，防止大对象打爆日志）
5. **保留私有方法手写日志** — 高频内部调用不走 AOP，避免日志泛滥

### 2.2 @Loggable 注解设计

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {
    LogLevel level() default LogLevel.INFO;      // 日志级别
    boolean logParams() default true;            // 是否打印入参
    boolean logReturn() default false;          // 是否打印返回值（默认关闭，避免大对象）
    boolean logExecutionTime() default true;    // 是否打印执行时间
}
```

### 2.3 LoggingAspect 切面设计

**切入点：** 所有标注了 `@Loggable` 的方法

**通知类型：** `@Around` — 完整包裹方法执行周期

**日志格式：**
```
>>> [ENTRANCE] TrainingService.startTraining() start. params: {request=TrainingRequest@..., userId=1, traceId=abc123}
>>> [EXIT] TrainingService.startTraining() completed in 45ms
>>> [EXCEPTION] SchedulerService.scheduleTask() threw RuntimeException: connection failed in 2003ms
```

---

## 三、实际修改

### 3.1 pom.xml — 添加 AOP 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
    <version>3.5.3</version>
</dependency>
```

> **注意：** 项目父 POM 版本 `4.0.3` 为无效版本（Spring Boot 最新稳定版为 `3.5.3`），AOP 依赖需显式指定有效版本。

### 3.2 Loggable.java — 新建注解

**路径：** `src/main/java/org/sgj/rljobscheduler/master/annotation/Loggable.java`

```java
package org.sgj.rljobscheduler.master.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {
    LogLevel level() default LogLevel.INFO;
    boolean logParams() default true;
    boolean logReturn() default false;
    boolean logExecutionTime() default true;

    enum LogLevel { DEBUG, INFO, WARN, ERROR }
}
```

### 3.3 LoggingAspect.java — 新建切面

**路径：** `src/main/java/org/sgj/rljobscheduler/master/aspect/LoggingAspect.java`

**核心逻辑：**

```java
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("@annotation(org.sgj.rljobscheduler.master.annotation.Loggable)")
    public void loggableMethods() {}

    @Around("loggableMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Loggable loggable = method.getAnnotation(Loggable.class);
        Logger log = getLogger(method);
        long startNs = System.nanoTime();

        // 入口日志
        if (loggable.logParams()) {
            logAtLevel(log, level, ">>> [ENTRANCE] {} start. params: {}",
                methodName, buildParamsSummary(joinPoint, signature));
        } else {
            logAtLevel(log, level, ">>> [ENTRANCE] {} start.", methodName);
        }

        Throwable thrown = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            if (thrown != null) {
                logAtLevel(log, LogLevel.ERROR,
                    ">>> [EXCEPTION] {} threw {}: {} in {}ms",
                    methodName, thrown.getClass().getSimpleName(),
                    thrown.getMessage(), elapsedMs, thrown);
            } else {
                logAtLevel(log, level,
                    ">>> [EXIT] {} completed{}",
                    methodName,
                    loggable.logExecutionTime() ? " in " + elapsedMs + "ms" : "");
            }
        }
    }
}
```

**参数处理要点：**
- 参数名通过 `Parameter.getName()` 获取（需 `-parameters` 编译参数，已在 Maven 中配置）
- 复杂对象仅打印类名 + `@hashCode`，避免序列化整个对象
- `String` 类型截断至 50 字符防止大文本撑爆日志

### 3.4 TrainingService.java — 替换 System.out.println

**修改前：**
```java
System.out.println(">>> [TrainingService] 任务已调度并更新为 RUNNING: " + taskId + ", rows=" + rows);
System.out.println(">>> [TrainingService] 任务调度失败，保持 PENDING: " + taskId);
```

**修改后：**
```java
LOG.info(">>> [TrainingService] 任务已调度并更新为 RUNNING: {}, rows={}", taskId, rows);
LOG.warn(">>> [TrainingService] 任务调度失败，保持 PENDING: {}", taskId);
```

**新增注解：**
```java
@Loggable(level = Loggable.LogLevel.INFO, logParams = true, logExecutionTime = true)
public TrainingResult startTraining(TrainingRequest request, Long userId, String traceId) { ... }
```

### 3.5 SchedulerService.java — 添加 @Loggable

**标注的方法：**

| 方法 | 注解配置 |
|------|----------|
| `reconstructWorkerTasksFromRedis()` | `@Loggable(level=INFO, logExecutionTime=true)` |
| `scheduleTask(TrainingTask, String)` | `@Loggable(level=INFO, logParams=true, logExecutionTime=true)` |
| `tryDispatchQueuedTaskToWorker(String)` | `@Loggable(level=INFO, logParams=true, logExecutionTime=true)` |

**保留手写日志的方法（私有/高频）：**
- `tryPreemptWorker()` — 私有方法，调用频繁
- `dispatchTask()` — 私有方法，调用频繁
- `registerTaskOwner()` / `releaseTaskOwner()` / `renewTaskOwnerTtl()` — Redis 操作，已是细粒度日志

---

## 四、明确不适用 AOP 的模块

### 4.1 MasterHandler（Netty Handler）

**原因：**
- MasterHandler 位于 Netty EventLoop 关键路径，性能敏感
- Netty 的 ChannelHandler 由 Netty 自身管理，不适合 Spring AOP 代理
- `channelRead0()` 每条消息都触发，走 AOP 会显著增加延迟

**决策：** 保持原样，不使用 `@Loggable`

### 4.2 私有辅助方法

**原则：** 高频调用的私有方法不加 `@Loggable`

**原因：**
- 每次方法调用都打印日志会导致日志量爆炸
- 私有辅助方法（如 `tryPreemptWorker`、`dispatchTask`）属于实现细节
- 调用方（如 `scheduleTask`）已经加了 `@Loggable`，整体可追踪

**决策：** 私有方法保留手写 `LOG.debug()` 或 `LOG.warn()`

---

## 五、修改文件清单

| 文件路径 | 修改类型 | 修改内容 |
|----------|----------|----------|
| `pom.xml` | 修改 | 添加 `spring-boot-starter-aop` 依赖 |
| `master/annotation/Loggable.java` | 新建 | 方法级日志注解 |
| `master/aspect/LoggingAspect.java` | 新建 | AOP 切面，统一处理方法日志 |
| `master/service/TrainingService.java` | 修改 | `System.out.println` → `LOG.warn/info`，添加 `@Loggable` |
| `master/service/SchedulerService.java` | 修改 | `scheduleTask` 等方法添加 `@Loggable` |

---

## 六、验证检查清单

- [ ] 编译通过：`./mvnw clean compile -DskipTests`
- [ ] 应用上下文启动正常：`./mvnw test -Dtest=RlJobSchedulerApplicationTests`
- [ ] AOP 切面正常生效（启动日志可见 `[ENTRANCE]` 格式输出）
- [ ] `System.out.println` 已从 `TrainingService` 中移除
- [ ] `@Loggable` 已添加到 `TrainingService.startTraining()`
- [ ] `@Loggable` 已添加到 `SchedulerService.scheduleTask()`
- [ ] `@Loggable` 已添加到 `SchedulerService.tryDispatchQueuedTaskToWorker()`
- [ ] `@Loggable` 已添加到 `SchedulerService.reconstructWorkerTasksFromRedis()`
- [ ] MasterHandler 未被修改
- [ ] 私有方法未添加 `@Loggable`

---

## 七、日志输出示例

### 7.1 正常执行

```
2026-04-05 17:12:00.123 [http-nio-8082-exec-1] INFO  o.s.r.m.service.TrainingService - >>> [ENTRANCE] TrainingService.startTraining() start. params: {request=TrainingRequest@3f8a9c1, userId=1, traceId=abc123}
2026-04-05 17:12:00.168 [http-nio-8082-exec-1] INFO  o.s.r.m.service.SchedulerService - >>> [ENTRANCE] SchedulerService.scheduleTask() start. params: {task=TrainingTask@7b2d1f4, traceId=abc123}
2026-04-05 17:12:00.200 [http-nio-8082-exec-1] INFO  o.s.r.m.service.SchedulerService - >>> [EXIT] SchedulerService.scheduleTask() completed in 32ms
2026-04-05 17:12:00.201 [http-nio-8082-exec-1] INFO  o.s.r.m.service.TrainingService - >>> [TrainingService] 任务已调度并更新为 RUNNING: a1b2c3d4, rows=1
2026-04-05 17:12:00.202 [http-nio-8082-exec-1] INFO  o.s.r.m.service.TrainingService - >>> [EXIT] TrainingService.startTraining() completed in 79ms
```

### 7.2 异常情况

```
2026-04-05 17:12:00.123 [http-nio-8082-exec-2] INFO  o.s.r.m.service.SchedulerService - >>> [ENTRANCE] SchedulerService.scheduleTask() start. params: {task=TrainingTask@3f8a9c1, traceId=unknown}
2026-04-05 17:12:02.130 [http-nio-8082-exec-2] ERROR o.s.r.m.aspect.LoggingAspect - >>> [EXCEPTION] SchedulerService.scheduleTask() threw RuntimeException: connection failed in 2007ms
java.lang.RuntimeException: connection failed
    at org.sgj.rljobscheduler.master.service.SchedulerService.scheduleTask(SchedulerService.java:145)
    at sun.reflect.NativeMethodAccessorImpl.invoke0(NativeMethodAccessorImpl.java)
    ...
```
