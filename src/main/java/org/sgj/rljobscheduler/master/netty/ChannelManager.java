package org.sgj.rljobscheduler.master.netty;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理与 Worker 的长连接通道
 */
@Component
public class ChannelManager {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelManager.class);

    // workerId -> Channel
    private final Map<String, Channel> workerChannels = new ConcurrentHashMap<>();

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
