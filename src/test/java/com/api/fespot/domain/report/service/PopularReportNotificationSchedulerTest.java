package com.api.fespot.domain.report.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class PopularReportNotificationSchedulerTest {

    @Test
    void delegatesNotificationProcessingToTheService() {
        PopularReportNotificationService service =
                mock(PopularReportNotificationService.class);
        PopularReportNotificationScheduler scheduler =
                new PopularReportNotificationScheduler(service);

        scheduler.processPopularReportNotifications();

        verify(service).processNotifications();
    }
}
