package org.sgj.rljobscheduler.common.netty;

public class NettyMessage {
    private MessageHeader header;
    private Object body;

    public NettyMessage() {}

    public NettyMessage(MessageHeader header, Object body) {
        this.header = header;
        this.body = body;
    }

    public MessageHeader getHeader() { return header; }
    public void setHeader(MessageHeader header) { this.header = header; }
    public Object getBody() { return body; }
    public void setBody(Object body) { this.body = body; }
}
