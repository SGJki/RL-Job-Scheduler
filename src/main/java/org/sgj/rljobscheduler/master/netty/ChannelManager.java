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
            Iterator<Map.Entry<String, Channel>> it = workerChannels.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Channel> entry = it.next();
                if (!entry.getValue().isActive()) {
                    String workerId = entry.getKey();
                    it.remove();
                    LOG.warn(">>> ChannelManager: 清理失效 Channel [{}]", workerId);
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        cleanupScheduler.shutdown();
    }

    public void register(String workerId, Channel channel) {
        workerChannels.put(workerId, channel);
    }

    public void unregister(String workerId) {
        workerChannels.remove(workerId);
    }

    public Channel getChannel(String workerId) {
        return workerChannels.get(workerId);
    }

    public Map<String, Channel> getAllChannels() {
        return workerChannels;
    }
}
