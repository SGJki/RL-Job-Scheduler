package org.sgj.rljobscheduler.dto;

public class TimeResponse {
    private String formattedTime;
    private String message;
    private String requestedBy;

    // 必须有构造函数或 Setter 方法，Spring 才能赋值 (或者你手动赋值)
    public TimeResponse(String formattedTime, String message, String requestedBy) {
        this.formattedTime = formattedTime;
        this.message = message;
        this.requestedBy = requestedBy;
    }

    // 必须有 Getter 方法，Spring 转 JSON 时是根据 Getter 拿数据的！
    public String getFormattedTime() {
        return formattedTime;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestedBy() {
        return requestedBy;
    }
}
