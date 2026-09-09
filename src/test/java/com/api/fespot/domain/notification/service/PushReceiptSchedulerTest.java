package com.api.fespot.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class PushReceiptSchedulerTest {

    private final PushReceiptService pushReceiptService =
            mock(PushReceiptService.class);

    private final PushReceiptScheduler pushReceiptScheduler =
            new PushReceiptScheduler(pushReceiptService);

    @Test
    void delegatesReceiptProcessingToService() {
        pushReceiptScheduler.processPendingReceipts();

        verify(pushReceiptService).processPendingReceipts();
    }

    @Test
    void doesNotPropagateServiceFailure() {
        willThrow(new RuntimeException("receipt processing failed"))
                .given(pushReceiptService)
                .processPendingReceipts();

        assertThatCode(
                pushReceiptScheduler::processPendingReceipts
        ).doesNotThrowAnyException();

        verify(pushReceiptService).processPendingReceipts();
    }
}
