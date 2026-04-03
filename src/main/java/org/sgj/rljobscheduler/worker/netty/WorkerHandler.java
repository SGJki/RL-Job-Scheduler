package org.sgj.rljobscheduler.worker.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.sgj.rljobscheduler.common.netty.*;
import org.sgj.rljobscheduler.common.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Worker 端的业务处理器
 */
public class WorkerHandler extends SimpleChannelInboundHandler<NettyMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerHandler.class);
    private final String workerId;
    private volatile String currentTaskId = "";
    private volatile String lastTaskId = "";
    private volatile int currentAttempt = 0;

    public WorkerHandler(String workerId) {
        this.workerId = workerId;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public String getLastTaskId() {
        return lastTaskId;
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyMessage msg) throws Exception {
        byte typeCode = msg.getHeader().getMessageType();
        MessageType type = MessageType.fromCode(typeCode);

        if (type == MessageType.EXECUTE_TASK) {
            handleExecuteTask(ctx, (ExecuteTaskRequest) msg.getBody());
        }
    }

    private void handleExecuteTask(ChannelHandlerContext ctx, ExecuteTaskRequest req) {
        String taskId = req.getTaskId();
        LOG.info(">>> 收到训练任务: taskId={}, algo={}", taskId, req.getAlgorithm());
        this.lastTaskId = taskId;
        this.currentTaskId = taskId;
        this.currentAttempt = req.getAttempt();

        // 1. 立即返回响应 (确认收到)
        ExecuteTaskResponse resp = ExecuteTaskResponse.newBuilder()
                .setTaskId(taskId)
                .setAccepted(true)
                .setMessage("Task received by worker " + workerId)
                .build();
        
        NettyMessage nettyResp = new NettyMessage();
        nettyResp.setHeader(new MessageHeader(0, MessageType.EXECUTE_TASK_RESPONSE.getCode()));
        nettyResp.setBody(resp);
        ctx.writeAndFlush(nettyResp);

        // 2. 异步执行 Python 脚本 (简化版实现，后续可封装为 ProcessExecutor)
        new Thread(() -> runPythonTask(ctx, req), "Task-Executor-" + taskId).start();
    }

    private void runPythonTask(ChannelHandlerContext ctx, ExecuteTaskRequest req) {
        String taskId = req.getTaskId();
        File workerLogDir = new File("server_log");
        if (!workerLogDir.exists()) workerLogDir.mkdirs();
        File workerLogFile = new File(workerLogDir, taskId + ".log");

        try (PrintWriter workerFileWriter = new PrintWriter(new FileWriter(workerLogFile, true))) {
            if (workerLogFile.length() == 0) {
                String traceId = req.getTraceId();
                if (traceId == null || traceId.isBlank()) {
                    traceId = "unknown";
                }
                workerFileWriter.println("TRACE_ID:" + traceId);
                workerFileWriter.flush();
            }

            List<String> command = new ArrayList<>();
            command.add("uv");
            command.add("run");
            command.add("python");
            command.add("scripts/train.py");
            command.add("--taskId"); command.add(taskId);
            command.add("--algo"); command.add(req.getAlgorithm());
            command.add("--episodes"); command.add(String.valueOf(req.getEpisodes()));
            command.add("--lr"); command.add(String.valueOf(req.getLearningRate()));

            LOG.info(">>> [Worker] 启动 Python 进程: {}, 目录: {}", String.join(" ", command), System.getProperty("user.dir"));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 1. 写入 Worker 本地模拟目录 (server_log)
                    workerFileWriter.println(line);
                    workerFileWriter.flush();

                    // 2. 推送给 Master
                    sendLog(ctx, taskId, line);
                }
            }

            int exitCode = process.waitFor();
            LOG.info(">>> [Worker] Python 进程结束, taskId={}, exitCode={}", taskId, exitCode);
            reportStatus(ctx, taskId, exitCode == 0 ? "COMPLETED" : "FAILED", exitCode == 0 ? "" : "Process exited with code " + exitCode);
            
            if (taskId.equals(this.currentTaskId)) {
                this.currentTaskId = "";
                this.currentAttempt = 0;
            }

        } catch (Exception e) {
            LOG.error(">>> [Worker] 执行 Python 任务失败: {}", taskId, e);
            reportStatus(ctx, taskId, "FAILED", e.getMessage());
            if (taskId.equals(this.currentTaskId)) {
                this.currentTaskId = "";
                this.currentAttempt = 0;
            }
        }
    }

    private void sendLog(ChannelHandlerContext ctx, String taskId, String line) {
        LogDataRequest log = LogDataRequest.newBuilder()
                .setTaskId(taskId)
                .setLogLine(line)
                .setTimestamp(System.currentTimeMillis())
                .build();
        
        NettyMessage msg = new NettyMessage();
        msg.setHeader(new MessageHeader(0, MessageType.LOG_DATA.getCode()));
        msg.setBody(log);
        ctx.writeAndFlush(msg);
    }

    private void reportStatus(ChannelHandlerContext ctx, String taskId, String status, String errorMsg) {
        int attempt = taskId != null && taskId.equals(this.currentTaskId) ? this.currentAttempt : 0;
        TaskStatusReport report = TaskStatusReport.newBuilder()
                .setTaskId(taskId)
                .setStatus(status)
                .setErrorMessage(errorMsg != null ? errorMsg : "")
                .setAttempt(attempt)
                .build();
        
        NettyMessage msg = new NettyMessage();
        msg.setHeader(new MessageHeader(0, MessageType.TASK_STATUS_REPORT.getCode()));
        msg.setBody(report);
        ctx.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error(">>> Worker Handler 异常: {}", cause.getMessage());
        ctx.close();
    }
}
