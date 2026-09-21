package com.bj.marketdata.consumer;

public record ConsumerLogonResponse(long subscriptionId, String updatesHost, int updatesPort) {
    public String toWireMessage() {
        final StringBuilder builder = new StringBuilder(updatesHost.length() + 32);
        builder.append(subscriptionId)
                .append(',')
                .append(updatesHost)
                .append(':')
                .append(updatesPort)
                .append('\n');
        return builder.toString();
    }

    public static ConsumerLogonResponse fromWireMessage(final String wireMessage) {
        if (wireMessage == null || wireMessage.isBlank()) {
            throw new IllegalArgumentException("wireMessage must not be blank");
        }
        final String trimmed = wireMessage.trim();
        final int commaIndex = trimmed.indexOf(',');
        if (commaIndex <= 0 || commaIndex >= trimmed.length() - 1) {
            throw new IllegalArgumentException("Invalid logon response wire format: " + wireMessage);
        }
        final String subscriptionIdText = trimmed.substring(0, commaIndex).trim();
        final String hostPort = trimmed.substring(commaIndex + 1).trim();
        final int colonIndex = hostPort.lastIndexOf(':');
        if (colonIndex <= 0 || colonIndex >= hostPort.length() - 1) {
            throw new IllegalArgumentException("Invalid host:port in response: " + wireMessage);
        }
        final long parsedSubscriptionId = Long.parseLong(subscriptionIdText);
        final String host = hostPort.substring(0, colonIndex).trim();
        final int port = Integer.parseInt(hostPort.substring(colonIndex + 1).trim());
        return new ConsumerLogonResponse(parsedSubscriptionId, host, port);
    }
}
