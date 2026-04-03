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

    private static final long INITIAL_RECONNECT_DELAY_SECONDS = 1;
    private static final long MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final double JITTER_FACTOR = 0.25;

    private final String masterHost;
    private final int masterPort;
    private final String workerId;

    private EventLoopGroup group;
    private Channel channel;
    private WorkerHandler workerHandler;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean scheduledStarted = false;
    private RedisLeaseManager leaseManager;
    private volatile long currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;
    private volatile boolean isReconnecting = false;

    public WorkerAgent(String host, int port, String workerId) {
        this.masterHost = host;
        this.masterPort = port;
        this.workerId = workerId;
    }

    public void start() {
        group = new NioEventLoopGroup();
        workerHandler = new WorkerHandler(workerId);
        leaseManager = new RedisLeaseManager(workerId);
        workerHandler.setLeaseManager(leaseManager);
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
        if (isReconnecting) {
            return;
        }
        isReconnecting = true;

        // 用数组包装避免 lambda 捕获非 final 变量
        final Channel[] pendingChannel = new Channel[1];

        b.connect(masterHost, masterPort).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                LOG.warn(">>> TCP 连接失败，{}秒后重试...", currentReconnectDelay);
                scheduleReconnect(b, 0);
                return;
            }

            // TCP 三次握手成功，但应用层可能尚未就绪
            // 先持有 channel 引用，等待一小段时间确认连接真正可用
            pendingChannel[0] = future.channel();
            LOG.info(">>> TCP 握手成功，等待应用层就绪...");

            // 等待 1 秒让 Master Spring/Netty 完全初始化
            pendingChannel[0].eventLoop().schedule(() -> {
                if (!pendingChannel[0].isActive()) {
                    // 连接在稳定期内失效，立即重连（不等待指数退避）
                    LOG.warn(">>> 连接不稳定（1秒内失效），立即重连...");
                    pendingChannel[0].close();
                    scheduleReconnect(b, 0);
                    return;
                }

                // 连接稳定，isSuccess = true 的连接现在真正可用
                channel = pendingChannel[0];
                currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;
                isReconnecting = false;
                LOG.info(">>> 成功连接到 Master（连接已稳定）!");

                // 只注册一次 closeFuture，避免重复触发
                channel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
                    if (isReconnecting) {
                        return; // 防止 closeFuture 和 scheduleReconnect 双重触发
                    }
                    LOG.warn(">>> 与 Master 连接断开，{}秒后重连...", currentReconnectDelay);
                    scheduleReconnect(b, 0);
                });
            }, 1, TimeUnit.SECONDS);
        });
    }

    private void scheduleReconnect(Bootstrap b, int delayMultiplier) {
        // delayMultiplier: 0 = 立即（上次已达 delay），>0 = 使用当前 delay
        long delayWithJitter = calculateDelayWithJitter(
                delayMultiplier == 0 ? currentReconnectDelay
                        : (long) (currentReconnectDelay * delayMultiplier)
        );
        currentReconnectDelay = Math.min(
                (long) (currentReconnectDelay * BACKOFF_MULTIPLIER),
                MAX_RECONNECT_DELAY_SECONDS
        );

        if (channel != null && !channel.eventLoop().isShuttingDown()) {
            channel.eventLoop().schedule(() -> {
                isReconnecting = false;
                connect(b);
            }, delayWithJitter, TimeUnit.SECONDS);
        } else {
            // channel 已不可用，用 scheduler 的 EventExecutor
            scheduler.schedule(() -> {
                isReconnecting = false;
                connect(b);
            }, delayWithJitter, TimeUnit.SECONDS);
        }
    }

    private long calculateDelayWithJitter(long delay) {
        long jitterRange = (long) (delay * JITTER_FACTOR);
        long jitter = (long) (Math.random() * jitterRange * 2 - jitterRange);
        return Math.max(1, delay + jitter);
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
