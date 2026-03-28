package org.sgj.rljobscheduler.common.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import com.google.protobuf.MessageLite;

public class MessageEncoder extends MessageToByteEncoder<NettyMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, NettyMessage msg, ByteBuf out) throws Exception {
        MessageHeader header = msg.getHeader();
        out.writeInt(header.getMagicNumber());
        out.writeByte(header.getVersion());
        
        byte[] bodyBytes = null;
        if (msg.getBody() instanceof MessageLite) {
            bodyBytes = ((MessageLite) msg.getBody()).toByteArray();
        }

        int bodyLen = (bodyBytes == null) ? 0 : bodyBytes.length;
        out.writeInt(ProtocolConstants.HEADER_LENGTH + bodyLen);
        out.writeByte(header.getMessageType());

        if (bodyBytes != null) {
            out.writeBytes(bodyBytes);
        }
    }
}
