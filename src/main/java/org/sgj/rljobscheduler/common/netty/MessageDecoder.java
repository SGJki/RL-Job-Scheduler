package org.sgj.rljobscheduler.common.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.sgj.rljobscheduler.common.proto.*;

import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < ProtocolConstants.HEADER_LENGTH) {
            return;
        }

        in.markReaderIndex();
        int magic = in.readInt();
        if (magic != ProtocolConstants.MAGIC_NUMBER) {
            in.resetReaderIndex();
            throw new RuntimeException("Invalid magic number: " + magic);
        }

        byte version = in.readByte();
        int fullLength = in.readInt();
        byte typeCode = in.readByte();

        int bodyLen = fullLength - ProtocolConstants.HEADER_LENGTH;
        if (in.readableBytes() < bodyLen) {
            in.resetReaderIndex();
            return;
        }

        byte[] bodyBytes = new byte[bodyLen];
        in.readBytes(bodyBytes);

        MessageType type = MessageType.fromCode(typeCode);
        Object body = null;

        if (type != null) {
            switch (type) {
                case HEARTBEAT:
                    body = HeartbeatRequest.parseFrom(bodyBytes);
                    break;
                case EXECUTE_TASK:
                    body = ExecuteTaskRequest.parseFrom(bodyBytes);
                    break;
                case EXECUTE_TASK_RESPONSE:
                    body = ExecuteTaskResponse.parseFrom(bodyBytes);
                    break;
                case LOG_DATA:
                    body = LogDataRequest.parseFrom(bodyBytes);
                    break;
                case TASK_STATUS_REPORT:
                    body = TaskStatusReport.parseFrom(bodyBytes);
                    break;
            }
        }

        MessageHeader header = new MessageHeader(fullLength, typeCode);
        header.setVersion(version);
        out.add(new NettyMessage(header, body));
    }
}
