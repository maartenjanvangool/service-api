package com.epam.ta.reportportal.ws.controller.async;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.ws.handler.FinishTestItemAsyncHandler;
import com.epam.ta.reportportal.ws.handler.StartTestItemAsyncHandler;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.epam.ta.reportportal.auth.permissions.Permissions.ALLOWED_TO_REPORT;
import static com.epam.ta.reportportal.auth.permissions.Permissions.ASSIGNED_TO_PROJECT;
import static com.epam.ta.reportportal.util.ProjectExtractor.extractProjectDetails;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/{projectName}/item/async")
@PreAuthorize(ASSIGNED_TO_PROJECT)
public class TestItemAsyncController {

    private final StartTestItemAsyncHandler startTestItemHandler;
    private final FinishTestItemAsyncHandler finishTestItemHandler;

    public TestItemAsyncController(StartTestItemAsyncHandler startTestItemHandler, FinishTestItemAsyncHandler finishTestItemHandler) {
        this.startTestItemHandler = startTestItemHandler;
        this.finishTestItemHandler = finishTestItemHandler;
    }

    @Transactional
    @PostMapping
    @ResponseStatus(CREATED)
    @ApiOperation("Start a root test item")
    @PreAuthorize(ALLOWED_TO_REPORT)
    public EntryCreatedRS startRootItem(@PathVariable String projectName, @AuthenticationPrincipal ReportPortalUser user,
                                        @RequestBody @Validated StartTestItemRQ startTestItemRQ) {
        return startTestItemHandler.startRootItemAsync(user, projectName, startTestItemRQ);
    }


    @Transactional
    @PostMapping("/{parentId}")
    @ResponseStatus(CREATED)
    @ApiOperation("Start a child test item")
    @PreAuthorize(ALLOWED_TO_REPORT)
    public EntryCreatedRS startChildItem(@PathVariable String projectName, @AuthenticationPrincipal ReportPortalUser user,
                                         @PathVariable Long parentId, @RequestBody @Validated StartTestItemRQ startTestItemRQ) {
        return startTestItemHandler.startChildItemAsync(user, projectName, startTestItemRQ, parentId);
    }

    @Transactional
    @PutMapping("/{testItemId}")
    @ResponseStatus(OK)
    @ApiOperation("Finish test item")
    @PreAuthorize(ALLOWED_TO_REPORT)
    public OperationCompletionRS finishTestItem(@PathVariable String projectName, @AuthenticationPrincipal ReportPortalUser user,
                                                @PathVariable Long testItemId, @RequestBody @Validated FinishTestItemRQ finishExecutionRQ) {
        return finishTestItemHandler.finishTestItem(finishExecutionRQ, testItemId, projectName, user.getUsername());
    }

}
