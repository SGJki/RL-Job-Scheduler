package org.sgj.rljobscheduler.master.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.sgj.rljobscheduler.common.netty.MessageDecoder;
import org.sgj.rljobscheduler.common.netty.MessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Master 端的 Netty 服务端
 * 监听端口，接收 Worker 的连接与数据
 */
@Component
public class MasterNettyServer {

    private static final Logger LOG = LoggerFactory.getLogger(MasterNettyServer.class);

    @Value("${rpc.server.enabled:${RPC_SERVER_ENABLED:true}}")
    private boolean enabled;

    @Value("${rpc.server.port:9000}")
    private int port;

    @Autowired
    private MasterHandler masterHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @PostConstruct
    public void start() {
        if (!enabled) {
            LOG.info(">>> Master Netty Server 已禁用");
            return;
        }
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new MessageDecoder());
                                ch.pipeline().addLast(new MessageEncoder());
                                ch.pipeline().addLast(masterHandler);
                            }
                        });

                LOG.info(">>> Master Netty Server 正在启动，监听端口: {}", port);
                ChannelFuture f = b.bind(port).sync();
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                LOG.error(">>> Master Netty Server 异常", e);
            } finally {
                stop();
            }
        }, "Netty-Server-Thread").start();
    }

    @PreDestroy
    public void stop() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
