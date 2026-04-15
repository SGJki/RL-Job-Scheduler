package org.sgj.rljobscheduler.master.netty;

import io.netty.channel.Channel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 管理与 Worker 的长连接通道
 */
@Component
public class ChannelManager {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelManager.class);

    // workerId -> Channel
    private final Map<String, Channel> workerChannels = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void startStaleChannelCleanup() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                Iterator<Map.Entry<String, Channel>> it = workerChannels.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Channel> entry = it.next();
                    if (!entry.getValue().isActive()) {
                        String workerId = entry.getKey();
                        it.remove();
                        LOG.warn(">>> ChannelManager: 清理失效 Channel [{}]", workerId);
                    }
                }
            } catch (Exception e) {
                LOG.error(">>> ChannelManager: 清理线程异常: {}", e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        cleanupScheduler.shutdown();
    }

    public void register(String workerId, Channel channel) {
        if (workerId == null || workerId.isBlank() || channel == null) {
            return;
        }
        workerChannels.put(workerId, channel);
    }

    public void unregister(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            return;
        }
        workerChannels.remove(workerId);
        LOG.info(">>> ChannelManager: Worker [{}] 已移除", workerId);
    }

    public Channel getChannel(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            return null;
        }
        return workerChannels.get(workerId);
    }

    public Map<String, Channel> getAllChannels() {
        return workerChannels;
    }
}
