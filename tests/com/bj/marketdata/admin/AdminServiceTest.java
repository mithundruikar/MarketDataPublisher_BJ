package com.bj.marketdata.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminServiceTest {
    @Test
    void shouldExposeProcessingProgressValues() {
        final ProcessingProgress progress = new ProcessingProgress(10L, 7L, 3L);
        assertEquals(10L, progress.receivedUpdates());
        assertEquals(7L, progress.publishedUpdates());
        assertEquals(3L, progress.rejectedUpdates());
    }
}
