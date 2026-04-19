# Plan Index

| # | File | Problem Solved |
|---|------|----------------|
| 1 | master-worker-availability.md | 消除 Redis 单点故障，实现降级运行 + 最终一致性 |
| 2 | master-handler-eventexecutor-group.md | 将 MasterHandler 从 EventLoop 隔离到独立线程池，消除 Redis/MySQL 同步 IO 阻塞 |
