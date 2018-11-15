package com.epam.ta.reportportal.ws.handler;

import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;

public interface FinishTestItemAsyncHandler {

    OperationCompletionRS finishTestItem(FinishTestItemRQ finishExecutionRQ, Long testItemId, String projectName, String username);
}
