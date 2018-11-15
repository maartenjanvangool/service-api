package com.epam.ta.reportportal.ws.handler;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;

public interface StartTestItemAsyncHandler {
    EntryCreatedRS startRootItemAsync(ReportPortalUser user, String projectName, StartTestItemRQ startTestItemRQ);

    void startRootItem(StartTestItemRQ rq, Long itemId);

    EntryCreatedRS startChildItemAsync(ReportPortalUser user, String projectName, StartTestItemRQ startTestItemRQ, Long parentId);

    void startChildItem(StartTestItemRQ rq, Long itemId, Long parentId);
}
