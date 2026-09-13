package com.api.fespot.domain.plan.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class VisitPlanNotificationSchedulerTest {

    @Test
    void delegatesNotificationProcessingToTheService() {
        VisitPlanNotificationService service =
                mock(VisitPlanNotificationService.class);
        VisitPlanNotificationScheduler scheduler =
                new VisitPlanNotificationScheduler(service);

        scheduler.processVisitPlanNotifications();

        verify(service).processNotifications();
    }
}
