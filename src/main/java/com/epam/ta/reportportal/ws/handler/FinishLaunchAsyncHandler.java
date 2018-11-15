package com.epam.ta.reportportal.ws.handler;

import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;

public interface FinishLaunchAsyncHandler {

    OperationCompletionRS finishLaunch(FinishExecutionRQ finishLaunchRQ, Long launchId, String projectName, String username);
}
