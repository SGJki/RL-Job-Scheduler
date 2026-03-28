package org.sgj.rljobscheduler.worker;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.sgj.rljobscheduler.common.netty.*;
import org.sgj.rljobscheduler.common.proto.*;
import org.sgj.rljobscheduler.worker.netty.WorkerHandler;
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

    public WorkerAgent(String host, int port, String workerId) {
        this.masterHost = host;
        this.masterPort = port;
        this.workerId = workerId;
    }

    public void start() {
        group = new NioEventLoopGroup();
        workerHandler = new WorkerHandler(workerId);
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
                startHeartbeat();
            } else {
                LOG.warn(">>> 连接失败，5秒后重试...");
                future.channel().eventLoop().schedule(() -> connect(b), 5, TimeUnit.SECONDS);
            }
        });
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (channel != null && channel.isActive()) {
                HeartbeatRequest hb = HeartbeatRequest.newBuilder()
                        .setWorkerId(workerId)
                        .setAvailableGpus(1) // 模拟
                        .setCpuUsage(0.5)    // 模拟
                        .setCurrentTaskId(workerHandler.getCurrentTaskId())
                        .build();
                
                NettyMessage message = new NettyMessage();
                message.setHeader(new MessageHeader(0, MessageType.HEARTBEAT.getCode()));
                message.setBody(hb);
                channel.writeAndFlush(message);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 9000;
        String id = "worker-" + System.currentTimeMillis() % 1000;
        
        if (args.length > 0) id = args[0];
        
        new WorkerAgent(host, port, id).start();
    }
}
