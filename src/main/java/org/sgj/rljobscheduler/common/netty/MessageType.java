package org.sgj.rljobscheduler.common.netty;

public enum MessageType {
    HEARTBEAT((byte) 1),
    EXECUTE_TASK((byte) 2),
    EXECUTE_TASK_RESPONSE((byte) 3),
    LOG_DATA((byte) 4),
    TASK_STATUS_REPORT((byte) 5);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
