package org.sgj.rljobscheduler.worker;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.sgj.rljobscheduler.common.netty.*;
import org.sgj.rljobscheduler.common.proto.*;
import org.sgj.rljobscheduler.worker.netty.WorkerHandler;
import org.sgj.rljobscheduler.worker.redis.RedisLeaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Worker Agent 启动类
 * 负责连接 Master 并执行训练任务
 */
public class WorkerAgent {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerAgent.class);

    private final String masterHost;
    private final int masterPort;
    private final String workerId;
    
    private EventLoopGroup group;
    private Channel channel;
    private WorkerHandler workerHandler;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean scheduledStarted = false;
    private RedisLeaseManager leaseManager;

    public WorkerAgent(String host, int port, String workerId) {
        this.masterHost = host;
        this.masterPort = port;
        this.workerId = workerId;
    }

    public void start() {
        group = new NioEventLoopGroup();
        workerHandler = new WorkerHandler(workerId);
        leaseManager = new RedisLeaseManager(workerId);
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new MessageDecoder());
                            ch.pipeline().addLast(new MessageEncoder());
                            ch.pipeline().addLast(workerHandler);
                        }
                    });

            LOG.info(">>> Worker Agent [{}] 正在连接 Master {}:{}...", workerId, masterHost, masterPort);
            startSchedulers();
            connect(b);
        } catch (Exception e) {
            LOG.error(">>> Worker Agent 启动异常", e);
        }
    }

    private void connect(Bootstrap b) {
        b.connect(masterHost, masterPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                LOG.info(">>> 成功连接到 Master!");
                channel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
                    LOG.warn(">>> 与 Master 连接断开，5秒后重连...");
                    closeFuture.channel().eventLoop().schedule(() -> connect(b), 5, TimeUnit.SECONDS);
                });
            } else {
                LOG.warn(">>> 连接失败，5秒后重试...");
                future.channel().eventLoop().schedule(() -> connect(b), 5, TimeUnit.SECONDS);
            }
        });
    }

    private void startSchedulers() {
        if (scheduledStarted) {
            return;
        }
        scheduledStarted = true;

        scheduler.scheduleAtFixedRate(() -> {
            String currentTaskId = workerHandler.getCurrentTaskId();
            String lastTaskId = workerHandler.getLastTaskId();
            if (leaseManager != null) {
                leaseManager.renew(currentTaskId, lastTaskId);
            }

            if (channel != null && channel.isActive()) {
                HeartbeatRequest hb = HeartbeatRequest.newBuilder()
                        .setWorkerId(workerId)
                        .setAvailableGpus(1)
                        .setCpuUsage(0.5)
                        .setCurrentTaskId(currentTaskId)
                        .build();

                NettyMessage message = new NettyMessage();
                message.setHeader(new MessageHeader(0, MessageType.HEARTBEAT.getCode()));
                message.setBody(hb);
                channel.writeAndFlush(message);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        String id = getEnv("WORKER_ID", "worker-" + System.currentTimeMillis() % 1000);
        String host = getEnv("MASTER_RPC_HOST", "127.0.0.1");
        int port = Integer.parseInt(getEnv("MASTER_RPC_PORT", "9000"));

        if (args.length > 0 && args[0] != null && !args[0].isBlank()) id = args[0];
        if (args.length > 1 && args[1] != null && !args[1].isBlank()) host = args[1];
        if (args.length > 2 && args[2] != null && !args[2].isBlank()) port = Integer.parseInt(args[2]);

        new WorkerAgent(host, port, id).start();
    }

    private static String getEnv(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v;
    }
}
