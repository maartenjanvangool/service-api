package com.epam.ta.reportportal.ws.handler;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;

public interface StartLaunchAsyncHandler {

    StartLaunchRS startLaunchAsync(ReportPortalUser user, String projectName, StartLaunchRQ startLaunchRQ);

    void startLaunch(ReportPortalUser user, String projectName, StartLaunchRQ startLaunchRQ, Long launchId);
}
