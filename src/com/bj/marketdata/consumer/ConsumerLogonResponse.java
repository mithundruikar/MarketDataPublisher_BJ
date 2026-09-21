package com.bj.marketdata.consumer;

public record ConsumerLogonResponse(String updatesHost, int updatesPort) {
    public String toWireMessage() {
        return updatesHost + ":" + updatesPort + "\n";
    }
}
