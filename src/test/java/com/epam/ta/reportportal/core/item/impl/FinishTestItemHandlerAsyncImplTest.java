package com.epam.ta.reportportal.core.item.impl;

import com.epam.ta.reportportal.commons.ReportPortalUser;
import com.epam.ta.reportportal.entity.project.ProjectRole;
import com.epam.ta.reportportal.entity.user.UserRole;
import com.epam.ta.reportportal.util.ReportingQueueService;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpTemplate;

import static com.epam.ta.reportportal.ReportPortalUserUtil.getRpUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * @author Konstantin Antipin
 */

@ExtendWith(MockitoExtension.class)
class FinishTestItemHandlerAsyncImplTest {

    @Mock
    AmqpTemplate amqpTemplate;

    @Mock
    ReportingQueueService reportingQueueService;

    @InjectMocks
    FinishTestItemHandlerAsyncImpl finishTestItemHandlerAsync;

    @Test
    void finishTestItem() {
        FinishTestItemRQ request = new FinishTestItemRQ();
        ReportPortalUser user = getRpUser("test", UserRole.ADMINISTRATOR, ProjectRole.PROJECT_MANAGER, 1L);

        finishTestItemHandlerAsync.finishTestItem(user, user.getProjectDetails().get("test_project"), "123", request);
        verify(amqpTemplate).convertAndSend(any(), any(), any(), any());
        verify(reportingQueueService).getReportingQueueKey(any());
    }

}