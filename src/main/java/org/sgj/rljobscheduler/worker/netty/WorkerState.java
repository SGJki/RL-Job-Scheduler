package org.sgj.rljobscheduler.worker.netty;

public class WorkerState {
    private volatile String currentTaskId = "";
    private volatile String lastTaskId = "";
    private volatile int currentAttempt = 0;

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId == null ? "" : currentTaskId;
    }

    public String getLastTaskId() {
        return lastTaskId;
    }

    public void setLastTaskId(String lastTaskId) {
        this.lastTaskId = lastTaskId == null ? "" : lastTaskId;
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    public void setCurrentAttempt(int currentAttempt) {
        this.currentAttempt = currentAttempt;
    }
}
