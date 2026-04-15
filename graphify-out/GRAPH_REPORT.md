# Graph Report - .  (2026-04-14)

## Corpus Check
- Corpus is ~22,108 words - fits in a single context window. You may not need a graph.

## Summary
- 442 nodes · 463 edges · 72 communities detected
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 3 edges (avg confidence: 0.75)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_AI Interview Agent|AI Interview Agent]]
- [[_COMMUNITY_TrainingTask Entity|TrainingTask Entity]]
- [[_COMMUNITY_Scheduler Service|Scheduler Service]]
- [[_COMMUNITY_Rate Limit Config|Rate Limit Config]]
- [[_COMMUNITY_User Entity|User Entity]]
- [[_COMMUNITY_Canary Routing Config|Canary Routing Config]]
- [[_COMMUNITY_Netty Message Header|Netty Message Header]]
- [[_COMMUNITY_AOP Logging Aspect|AOP Logging Aspect]]
- [[_COMMUNITY_TrainingRequest DTO|TrainingRequest DTO]]
- [[_COMMUNITY_Master Netty Handler|Master Netty Handler]]
- [[_COMMUNITY_Worker Netty Handler|Worker Netty Handler]]
- [[_COMMUNITY_Redis Lease Manager|Redis Lease Manager]]
- [[_COMMUNITY_Fallback Config|Fallback Config]]
- [[_COMMUNITY_Worker Agent Entry|Worker Agent Entry]]
- [[_COMMUNITY_JWT Gateway Filter|JWT Gateway Filter]]
- [[_COMMUNITY_TrainingResult DTO|TrainingResult DTO]]
- [[_COMMUNITY_Log Manager|Log Manager]]
- [[_COMMUNITY_Web Controller|Web Controller]]
- [[_COMMUNITY_Training Executor|Training Executor]]
- [[_COMMUNITY_Worker State|Worker State]]
- [[_COMMUNITY_Rate Limit Filter|Rate Limit Filter]]
- [[_COMMUNITY_Netty Message|Netty Message]]
- [[_COMMUNITY_JWT Utils|JWT Utils]]
- [[_COMMUNITY_AOP Logging Infrastructure|AOP Logging Infrastructure]]
- [[_COMMUNITY_Upstream Fallback Filter|Upstream Fallback Filter]]
- [[_COMMUNITY_Interview Controller|Interview Controller]]
- [[_COMMUNITY_Auth Request DTO|Auth Request DTO]]
- [[_COMMUNITY_Time Response DTO|Time Response DTO]]
- [[_COMMUNITY_Channel Manager|Channel Manager]]
- [[_COMMUNITY_Training Service|Training Service]]
- [[_COMMUNITY_Signature Utils|Signature Utils]]
- [[_COMMUNITY_Infra Startup Script|Infra Startup Script]]
- [[_COMMUNITY_JWT Auth Filter|JWT Auth Filter]]
- [[_COMMUNITY_Auth Controller|Auth Controller]]
- [[_COMMUNITY_Hello Controller|Hello Controller]]
- [[_COMMUNITY_Pending Task Reconciler|Pending Task Reconciler]]
- [[_COMMUNITY_Running Task Recovery|Running Task Recovery]]
- [[_COMMUNITY_Gateway Routes Config|Gateway Routes Config]]
- [[_COMMUNITY_TraceId Filter|TraceId Filter]]
- [[_COMMUNITY_Message Type Enum|Message Type Enum]]
- [[_COMMUNITY_Security Config|Security Config]]
- [[_COMMUNITY_TraceId MDC Filter|TraceId MDC Filter]]
- [[_COMMUNITY_WebSocket Config|WebSocket Config]]
- [[_COMMUNITY_Monitor Controller|Monitor Controller]]
- [[_COMMUNITY_Training Controller|Training Controller]]
- [[_COMMUNITY_Master Netty Server|Master Netty Server]]
- [[_COMMUNITY_Auth Service|Auth Service]]
- [[_COMMUNITY_Global Log Tailer Listener|Global Log Tailer Listener]]
- [[_COMMUNITY_Task Log Tailer Listener|Task Log Tailer Listener]]
- [[_COMMUNITY_WorkerHandler Multiplicity Test|WorkerHandler Multiplicity Test]]
- [[_COMMUNITY_Gateway Application|Gateway Application]]
- [[_COMMUNITY_Jackson Config|Jackson Config]]
- [[_COMMUNITY_Redis Config|Redis Config]]
- [[_COMMUNITY_Message Decoder|Message Decoder]]
- [[_COMMUNITY_Message Encoder|Message Encoder]]
- [[_COMMUNITY_Master Application|Master Application]]
- [[_COMMUNITY_Async Config|Async Config]]
- [[_COMMUNITY_MyBatis Plus Config|MyBatis Plus Config]]
- [[_COMMUNITY_Object Mapper Config|Object Mapper Config]]
- [[_COMMUNITY_Application Tests|Application Tests]]
- [[_COMMUNITY_Worker Core Components|Worker Core Components]]
- [[_COMMUNITY_Task Persistence|Task Persistence]]
- [[_COMMUNITY_Training Script Entry|Training Script Entry]]
- [[_COMMUNITY_Protocol Constants|Protocol Constants]]
- [[_COMMUNITY_TrainingTask Mapper|TrainingTask Mapper]]
- [[_COMMUNITY_User Mapper|User Mapper]]
- [[_COMMUNITY_Recovery Patterns|Recovery Patterns]]
- [[_COMMUNITY_Channel Manager Singleton|Channel Manager Singleton]]
- [[_COMMUNITY_Loggable Annotation|Loggable Annotation]]
- [[_COMMUNITY_Test Controller|Test Controller]]
- [[_COMMUNITY_Project Root|Project Root]]
- [[_COMMUNITY_MySQL Service|MySQL Service]]

## God Nodes (most connected - your core abstractions)
1. `TrainingTask` - 22 edges
2. `SchedulerService` - 16 edges
3. `RateLimitProperties` - 13 edges
4. `User` - 12 edges
5. `CanaryProperties` - 11 edges
6. `MessageHeader` - 10 edges
7. `LoggingAspect` - 10 edges
8. `TrainingRequest` - 10 edges
9. `MasterHandler` - 10 edges
10. `WorkerHandler` - 10 edges

## Surprising Connections (you probably didn't know these)
- `Master` --uses--> `WebSocket`  [EXTRACTED]
  CLAUDE.md → README.md
- `Master` --implemented_with--> `Spring Boot`  [EXTRACTED]
  CLAUDE.md → README.md
- `TraceId Tracking` --propagated_to--> `Master`  [EXTRACTED]
  levelUpJava.md → CLAUDE.md
- `Master Cold Start` --affects--> `Master`  [EXTRACTED]
  Summary.md → CLAUDE.md
- `Worker` --uses--> `Exponential Backoff Reconnect`  [EXTRACTED]
  CLAUDE.md → docs/master-worker-resilience-fix.md

## Hyperedges (group relationships)
- **Master-Worker Architecture** — master_component, worker_component, netty_rpc, redis_service, channel_manager [EXTRACTED 0.90]
- **AOP Logging Infrastructure** — loggable_annotation, logging_aspect, training_service, scheduler_service [EXTRACTED 0.85]
- **Worker Resilience Pattern** — exponential_backoff, persist_task_start, clear_task, lease_renewal, running_task_recovery [EXTRACTED 0.85]

## Communities

### Community 0 - "AI Interview Agent"
Cohesion: 0.11
Nodes (24): AI Interview Agent, Attempt Barrier Token, Exponential Backoff Reconnect, Gateway, Gateway Signature Trust, JWT Authentication, LangGraph Agent, Master Cold Start (+16 more)

### Community 1 - "TrainingTask Entity"
Cohesion: 0.09
Nodes (1): TrainingTask

### Community 2 - "Scheduler Service"
Cohesion: 0.27
Nodes (1): SchedulerService

### Community 3 - "Rate Limit Config"
Cohesion: 0.14
Nodes (1): RateLimitProperties

### Community 4 - "User Entity"
Cohesion: 0.15
Nodes (1): User

### Community 5 - "Canary Routing Config"
Cohesion: 0.17
Nodes (1): CanaryProperties

### Community 6 - "Netty Message Header"
Cohesion: 0.18
Nodes (1): MessageHeader

### Community 7 - "AOP Logging Aspect"
Cohesion: 0.31
Nodes (1): LoggingAspect

### Community 8 - "TrainingRequest DTO"
Cohesion: 0.18
Nodes (1): TrainingRequest

### Community 9 - "Master Netty Handler"
Cohesion: 0.27
Nodes (1): MasterHandler

### Community 10 - "Worker Netty Handler"
Cohesion: 0.25
Nodes (1): WorkerHandler

### Community 11 - "Redis Lease Manager"
Cohesion: 0.33
Nodes (1): RedisLeaseManager

### Community 12 - "Fallback Config"
Cohesion: 0.2
Nodes (1): FallbackProperties

### Community 13 - "Worker Agent Entry"
Cohesion: 0.33
Nodes (1): WorkerAgent

### Community 14 - "JWT Gateway Filter"
Cohesion: 0.33
Nodes (1): JwtPassthroughFilter

### Community 15 - "TrainingResult DTO"
Cohesion: 0.22
Nodes (1): TrainingResult

### Community 16 - "Log Manager"
Cohesion: 0.31
Nodes (1): LogManager

### Community 17 - "Web Controller"
Cohesion: 0.43
Nodes (1): WebController

### Community 18 - "Training Executor"
Cohesion: 0.43
Nodes (1): TrainingExecutor

### Community 19 - "Worker State"
Cohesion: 0.25
Nodes (1): WorkerState

### Community 20 - "Rate Limit Filter"
Cohesion: 0.38
Nodes (1): RateLimitFilter

### Community 21 - "Netty Message"
Cohesion: 0.29
Nodes (1): NettyMessage

### Community 22 - "JWT Utils"
Cohesion: 0.38
Nodes (1): JwtUtils

### Community 23 - "AOP Logging Infrastructure"
Cohesion: 0.29
Nodes (7): LogManager, @Loggable Annotation, LoggingAspect, MasterHandler, RunningTaskRecovery, SchedulerService, TrainingService

### Community 24 - "Upstream Fallback Filter"
Cohesion: 0.4
Nodes (1): UpstreamFallbackFilter

### Community 25 - "Interview Controller"
Cohesion: 0.53
Nodes (1): InterviewController

### Community 26 - "Auth Request DTO"
Cohesion: 0.33
Nodes (1): AuthRequest

### Community 27 - "Time Response DTO"
Cohesion: 0.33
Nodes (1): TimeResponse

### Community 28 - "Channel Manager"
Cohesion: 0.33
Nodes (1): ChannelManager

### Community 29 - "Training Service"
Cohesion: 0.4
Nodes (1): TrainingService

### Community 30 - "Signature Utils"
Cohesion: 0.4
Nodes (1): SignatureUtils

### Community 31 - "Infra Startup Script"
Cohesion: 0.7
Nodes (4): Start-MySQL(), Start-Nacos(), Start-Redis(), Write-Banner()

### Community 32 - "JWT Auth Filter"
Cohesion: 0.6
Nodes (1): JwtAuthenticationFilter

### Community 33 - "Auth Controller"
Cohesion: 0.4
Nodes (1): AuthController

### Community 34 - "Hello Controller"
Cohesion: 0.4
Nodes (1): HelloController

### Community 35 - "Pending Task Reconciler"
Cohesion: 0.5
Nodes (1): PendingTaskReconciler

### Community 36 - "Running Task Recovery"
Cohesion: 0.5
Nodes (1): RunningTaskRecovery

### Community 37 - "Gateway Routes Config"
Cohesion: 0.67
Nodes (1): GatewayRoutesConfig

### Community 38 - "TraceId Filter"
Cohesion: 0.5
Nodes (1): TraceIdFilter

### Community 39 - "Message Type Enum"
Cohesion: 0.5
Nodes (0): 

### Community 40 - "Security Config"
Cohesion: 0.5
Nodes (1): SecurityConfig

### Community 41 - "TraceId MDC Filter"
Cohesion: 0.5
Nodes (1): TraceIdMdcFilter

### Community 42 - "WebSocket Config"
Cohesion: 0.5
Nodes (1): WebSocketConfig

### Community 43 - "Monitor Controller"
Cohesion: 0.5
Nodes (1): MonitorController

### Community 44 - "Training Controller"
Cohesion: 0.5
Nodes (1): TrainingController

### Community 45 - "Master Netty Server"
Cohesion: 0.67
Nodes (1): MasterNettyServer

### Community 46 - "Auth Service"
Cohesion: 0.5
Nodes (1): AuthService

### Community 47 - "Global Log Tailer Listener"
Cohesion: 0.5
Nodes (1): GlobalLogTailerListener

### Community 48 - "Task Log Tailer Listener"
Cohesion: 0.5
Nodes (1): TaskLogTailerListener

### Community 49 - "WorkerHandler Multiplicity Test"
Cohesion: 0.5
Nodes (1): WorkerHandlerMultiplicityTest

### Community 50 - "Gateway Application"
Cohesion: 0.67
Nodes (1): GatewayApplication

### Community 51 - "Jackson Config"
Cohesion: 0.67
Nodes (1): JacksonConfig

### Community 52 - "Redis Config"
Cohesion: 0.67
Nodes (1): RedisConfig

### Community 53 - "Message Decoder"
Cohesion: 0.67
Nodes (1): MessageDecoder

### Community 54 - "Message Encoder"
Cohesion: 0.67
Nodes (1): MessageEncoder

### Community 55 - "Master Application"
Cohesion: 0.67
Nodes (1): RlJobSchedulerApplication

### Community 56 - "Async Config"
Cohesion: 0.67
Nodes (1): AsyncConfig

### Community 57 - "MyBatis Plus Config"
Cohesion: 0.67
Nodes (1): MybatisPlusConfig

### Community 58 - "Object Mapper Config"
Cohesion: 0.67
Nodes (1): ObjectMapperConfig

### Community 59 - "Application Tests"
Cohesion: 0.67
Nodes (1): RlJobSchedulerApplicationTests

### Community 60 - "Worker Core Components"
Cohesion: 0.67
Nodes (3): WorkerAgent, WorkerHandler, WorkerState

### Community 61 - "Task Persistence"
Cohesion: 0.67
Nodes (3): clearTask, persistTaskStart, RedisLeaseManager

### Community 62 - "Training Script Entry"
Cohesion: 1.0
Nodes (0): 

### Community 63 - "Protocol Constants"
Cohesion: 1.0
Nodes (1): ProtocolConstants

### Community 64 - "TrainingTask Mapper"
Cohesion: 1.0
Nodes (1): TrainingTaskMapper

### Community 65 - "User Mapper"
Cohesion: 1.0
Nodes (1): UserMapper

### Community 66 - "Recovery Patterns"
Cohesion: 1.0
Nodes (2): Lease Renewal, RUNNING Recovery

### Community 67 - "Channel Manager Singleton"
Cohesion: 1.0
Nodes (1): ChannelManager

### Community 68 - "Loggable Annotation"
Cohesion: 1.0
Nodes (0): 

### Community 69 - "Test Controller"
Cohesion: 1.0
Nodes (0): 

### Community 70 - "Project Root"
Cohesion: 1.0
Nodes (1): RL-Job-Scheduler

### Community 71 - "MySQL Service"
Cohesion: 1.0
Nodes (1): MySQL

## Knowledge Gaps
- **26 isolated node(s):** `ProtocolConstants`, `TrainingTaskMapper`, `UserMapper`, `RL-Job-Scheduler`, `Nacos` (+21 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Training Script Entry`** (2 nodes): `train.py`, `main()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Protocol Constants`** (2 nodes): `ProtocolConstants`, `ProtocolConstants.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `TrainingTask Mapper`** (2 nodes): `TrainingTaskMapper.java`, `TrainingTaskMapper`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `User Mapper`** (2 nodes): `UserMapper.java`, `UserMapper`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Recovery Patterns`** (2 nodes): `Lease Renewal`, `RUNNING Recovery`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Channel Manager Singleton`** (1 nodes): `ChannelManager`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Loggable Annotation`** (1 nodes): `Loggable.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Test Controller`** (1 nodes): `TestController.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Project Root`** (1 nodes): `RL-Job-Scheduler`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `MySQL Service`** (1 nodes): `MySQL`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What connects `ProtocolConstants`, `TrainingTaskMapper`, `UserMapper` to the rest of the system?**
  _26 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `AI Interview Agent` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `TrainingTask Entity` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `Rate Limit Config` be split into smaller, more focused modules?**
  _Cohesion score 0.14 - nodes in this community are weakly interconnected._