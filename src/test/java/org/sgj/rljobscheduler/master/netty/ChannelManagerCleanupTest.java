package org.sgj.rljobscheduler.master.netty;

import io.netty.channel.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelManagerCleanupTest {

    @Mock
    private Channel activeChannel;

    @Mock
    private Channel inactiveChannel;

    @Test
    void register_storesChannelByWorkerId() {
        ChannelManager manager = new ChannelManager();
        manager.register("worker-1", activeChannel);
        assertThat(manager.getChannel("worker-1")).isEqualTo(activeChannel);
    }

    @Test
    void unregister_removesChannelAndReturnsNull() {
        ChannelManager manager = new ChannelManager();
        manager.register("worker-1", activeChannel);
        assertThat(manager.getChannel("worker-1")).isNotNull();
        manager.unregister("worker-1");
        assertThat(manager.getChannel("worker-1")).isNull();
    }

    @Test
    void getChannel_returnsNullForUnknownWorker() {
        ChannelManager manager = new ChannelManager();
        assertThat(manager.getChannel("unknown")).isNull();
    }
}
