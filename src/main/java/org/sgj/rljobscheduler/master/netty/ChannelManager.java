package org.sgj.rljobscheduler.master.netty;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理与 Worker 的长连接通道
 */
@Component
public class ChannelManager {

    // workerId -> Channel
    private final Map<String, Channel> workerChannels = new ConcurrentHashMap<>();

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
