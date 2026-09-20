package com.bj.marketdata.admin;

public record ProcessingProgress(
        long receivedUpdates,
        long publishedUpdates,
        long rejectedUpdates
) {
}
