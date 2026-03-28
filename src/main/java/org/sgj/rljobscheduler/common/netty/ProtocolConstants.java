package org.sgj.rljobscheduler.common.netty;

public class ProtocolConstants {
    public static final int MAGIC_NUMBER = 0xCAFEBABE;
    public static final byte VERSION = 1;
    public static final int HEADER_LENGTH = 4 + 1 + 4 + 1; // Magic(4) + Ver(1) + FullLen(4) + Type(1)
}
