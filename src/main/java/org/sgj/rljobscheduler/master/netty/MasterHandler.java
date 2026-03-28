package org.sgj.rljobscheduler.master.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.sgj.rljobscheduler.common.netty.MessageType;
import org.sgj.rljobscheduler.common.netty.NettyMessage;
import org.sgj.rljobscheduler.common.proto.*;
import io.netty.util.AttributeKey;
import org.sgj.rljobscheduler.master.entity.TrainingTask;
import org.sgj.rljobscheduler.master.mapper.TrainingTaskMapper;
import org.sgj.rljobscheduler.master.service.SchedulerService;
import org.sgj.rljobscheduler.master.service.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Master 端的业务处理器 (Sharable)
 */
@Component
@ChannelHandler.Sharable
public class MasterHandler extends SimpleChannelInboundHandler<NettyMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(MasterHandler.class);
    private static final AttributeKey<String> WORKER_ID_KEY = AttributeKey.valueOf("workerId");

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LogManager logManager;

    @Autowired
    private ChannelManager channelManager;

    @Autowired
    private TrainingTaskMapper taskMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SchedulerService schedulerService;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOG.info(">>> 有新的 Worker 连接: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // TODO: 从 ChannelManager 中移除，但这需要知道 workerId
        // 可以在处理心跳时绑定属性到 Channel
        LOG.info(">>> Worker 连接断开: {}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyMessage msg) throws Exception {
        byte typeCode = msg.getHeader().getMessageType();
        MessageType type = MessageType.fromCode(typeCode);

        if (type == null) return;

        switch (type) {
            case HEARTBEAT:
                handleHeartbeat(ctx, (HeartbeatRequest) msg.getBody());
                break;
            case EXECUTE_TASK_RESPONSE:
                handleTaskResponse((ExecuteTaskResponse) msg.getBody());
                break;
            case LOG_DATA:
                handleLogData((LogDataRequest) msg.getBody());
                break;
            case TASK_STATUS_REPORT:
                handleStatusReport(ctx, (TaskStatusReport) msg.getBody());
                break;
            default:
                LOG.warn(">>> Master 收到未知消息类型: {}", type);
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, HeartbeatRequest req) {
        String workerId = req.getWorkerId();
        // 绑定 workerId 到 Channel 属性
        ctx.channel().attr(WORKER_ID_KEY).set(workerId);
        
        // 注册到 ChannelManager
        channelManager.register(workerId, ctx.channel());
        
        // 存储心跳到 Redis，有效期 30 秒
        String hbKey = "worker:" + workerId + ":hb";
        redisTemplate.opsForValue().set(hbKey, "alive", 30, TimeUnit.SECONDS);
        
        // 如果有正在运行的任务，续期 TaskIDKey (2 分钟)
        String currentTaskId = req.getCurrentTaskId();
        if (currentTaskId != null && !currentTaskId.isEmpty()) {
            String taskKey = "worker:" + workerId + ":task";
            redisTemplate.expire(taskKey, 120, TimeUnit.SECONDS);
        } else {
            schedulerService.tryDispatchQueuedTaskToWorker(workerId);
        }
        
        // 记录 Worker 元数据 (可选)
        String metaKey = "worker:" + workerId + ":meta";
        redisTemplate.opsForValue().set(metaKey, String.format("GPUs:%d, CPU:%.2f", req.getAvailableGpus(), req.getCpuUsage()));
    }

    private void handleTaskResponse(ExecuteTaskResponse resp) {
        LOG.info(">>> 收到任务响应: taskId={}, accepted={}", resp.getTaskId(), resp.getAccepted());
        // Worker 确认收到任务，TaskIDKey 的设置由 Worker 在 Redis 中完成，这里仅记录日志
    }

    private void handleLogData(LogDataRequest logData) {
        // 将日志交给 LogManager 异步处理 (解耦 EventLoop)
        logManager.enqueueLog(logData);
    }

    private void handleStatusReport(ChannelHandlerContext ctx, TaskStatusReport report) {
        String taskId = report.getTaskId();
        String status = report.getStatus();
        LOG.info(">>> 收到任务状态报告: taskId={}, status={}", taskId, status);
        
        // 更新数据库
        TrainingTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            if ("COMPLETED".equals(status)) {
                task.setFinalReward(report.getFinalReward());
                task.setCompletedAt(LocalDateTime.now());
            } else if ("FAILED".equals(status)) {
                task.setErrorMessage(report.getErrorMessage());
                task.setCompletedAt(LocalDateTime.now());
            }
            taskMapper.updateById(task);
            LOG.info(">>> 任务数据库状态已更新: taskId={}, status={}", taskId, status);
            
            // 推送 WebSocket 状态更新到前端
            messagingTemplate.convertAndSend("/topic/tasks", task);
        } else {
            LOG.warn(">>> 收到状态报告但任务不存在: taskId={}", taskId);
        }

        // 如果任务结束，清理 Redis 中的 TaskIDKey
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            String workerId = ctx.channel().attr(WORKER_ID_KEY).get();
            if (workerId != null) {
                String taskKey = "worker:" + workerId + ":task";
                redisTemplate.delete(taskKey);
                LOG.info(">>> 任务结束，已释放 Worker [{}]", workerId);
                schedulerService.tryDispatchQueuedTaskToWorker(workerId);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error(">>> Master Handler 异常: {}", cause.getMessage());
        ctx.close();
    }
}
