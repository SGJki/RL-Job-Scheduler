package org.sgj.rljobscheduler.common.netty;

public class MessageHeader {
    private int magicNumber = ProtocolConstants.MAGIC_NUMBER;
    private byte version = ProtocolConstants.VERSION;
    private int fullLength;
    private byte messageType;

    public MessageHeader() {}

    public MessageHeader(int fullLength, byte messageType) {
        this.fullLength = fullLength;
        this.messageType = messageType;
    }

    // Getters and Setters
    public int getMagicNumber() { return magicNumber; }
    public void setMagicNumber(int magicNumber) { this.magicNumber = magicNumber; }
    public byte getVersion() { return version; }
    public void setVersion(byte version) { this.version = version; }
    public int getFullLength() { return fullLength; }
    public void setFullLength(int fullLength) { this.fullLength = fullLength; }
    public byte getMessageType() { return messageType; }
    public void setMessageType(byte messageType) { this.messageType = messageType; }
}
