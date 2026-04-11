package org.sgj.rljobscheduler.worker.netty;

import io.netty.channel.ChannelPipelineException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerHandlerMultiplicityTest {

    @Test
    void shouldThrowWhenReusingNonSharableHandlerInstanceAcrossChannels() {
        WorkerState state = new WorkerState();
        WorkerHandler handler = new WorkerHandler("w1", state);

        EmbeddedChannel ch1 = new EmbeddedChannel();
        ch1.pipeline().addLast(handler);

        EmbeddedChannel ch2 = new EmbeddedChannel();
        assertThrows(ChannelPipelineException.class, () -> ch2.pipeline().addLast(handler));
    }

    @Test
    void shouldAllowNewHandlerPerChannelEvenIfSharingState() {
        WorkerState state = new WorkerState();

        assertDoesNotThrow(() -> {
            EmbeddedChannel ch1 = new EmbeddedChannel();
            ch1.pipeline().addLast(new WorkerHandler("w1", state));

            EmbeddedChannel ch2 = new EmbeddedChannel();
            ch2.pipeline().addLast(new WorkerHandler("w1", state));
        });
    }
}

