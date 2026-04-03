# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RL-Job-Scheduler is a distributed backend system for managing and scheduling Reinforcement Learning training tasks. It uses a Master-Worker architecture with asynchronous task submission, distributed computing scheduling, and real-time log monitoring.

## Build Commands

```bash
# Build entire project (generates Protobuf code)
./mvnw clean package

# Build only root module
./mvnw clean package -DskipTests

# Build gateway module only
./mvnw -f gateway/pom.xml clean package

# Run tests
./mvnw test

# Run Master service
./mvnw spring-boot:run

# Run Gateway service
./mvnw -f gateway/pom.xml spring-boot:run
```

## Architecture

```
┌─────────────────────────────────────┐
│     Gateway (8081) - Spring Cloud   │
│  JWT Auth │ Rate Limit │ Canary     │
└───────────────┬────────────────────┘
                 │ Nacos Service Discovery
                 ▼
┌─────────────────────────────────────┐
│   Master (8082) - Spring Boot      │
│  REST API │ WebSocket │ Netty RPC  │
└───────────────┬────────────────────┘
                 │ Netty RPC (9000)
                 ▼
┌─────────────────────────────────────┐
│  Worker Cluster - Java Agent        │
│  Heartbeat │ Python Executor       │
└─────────────────────────────────────┘
```

### Module Structure

| Module | Port | Purpose |
|--------|------|---------|
| `gateway/` | 8081 | API Gateway with JWT auth, rate limiting, canary routing |
| `src/main/java/.../master/` | 8082 | Scheduler, task management, WebSocket, Netty server |
| `src/main/java/.../worker/` | Agent | Lightweight agent for Python execution |
| `src/main/java/.../common/` | - | Shared Protobuf definitions, Netty codecs |

### Key Technologies

- **Spring Boot 4.0.3** with Java 17
- **Spring Cloud Gateway 2025.1.0** for API gateway
- **Netty 4.1.101** for Master-Worker RPC communication
- **Protobuf 3.25.1** for binary serialization
- **Nacos** for service discovery
- **MyBatis-Plus 3.5.15** for database access
- **Redis** for rate limiting, heartbeat leases

## Configuration

Key configuration files:
- `src/main/resources/application.yaml` - Master configuration
- `gateway/src/main/resources/application.yaml` - Gateway configuration

Environment variables for infrastructure:
- `NACOS_ADDR` - Nacos server address (default: `127.0.0.1:8848`)
- `NACOS_USERNAME` / `NACOS_PASSWORD` - Nacos credentials
- `JWT_SECRET` - JWT signing secret

## Entry Points

- **Master Application**: `org.sgj.rljobscheduler.master.RlJobSchedulerApplication`
- **Gateway Application**: `org.sgj.rljobscheduler.gateway.GatewayApplication`
- **Worker Agent**: `org.sgj.rljobscheduler.worker.WorkerAgent`

## Key Services

| Service | Class | Description |
|---------|-------|-------------|
| Scheduler | `SchedulerService` | Task queue management and worker assignment |
| Training | `TrainingService` / `TrainingExecutor` | Task execution pipeline |
| Auth | `AuthService` | JWT token generation and validation |
| Log | `LogManager` | Async log processing from workers |
| Netty Server | `MasterNettyServer` / `MasterHandler` | RPC server handling worker connections |
| Channel Manager | `ChannelManager` | Tracks active worker Netty channels |

## Worker State Machine

Workers cycle through: `IDLE` → `RUNNING` → `PENDING` (if heartbeat lost) → `DOWN`

## Testing

Tests are in `src/test/java/`. Key test files:
- `RlJobSchedulerApplicationTests.java` - Application context test
- `TestController.java` - Controller tests

## Development Notes

- Protobuf files in `src/main/proto/` generate Java classes via `protobuf-maven-plugin`
- JWT tokens use JJWT library (io.jsonwebtoken)
- WebSocket endpoints at `/ws` with STOMP for real-time task updates
- Task logs stored in `logs/<taskId>.log` with traceId correlation
