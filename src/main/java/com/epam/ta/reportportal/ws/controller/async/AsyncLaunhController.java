package com.epam.ta.reportportal.ws.controller.async;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.ws.handler.FinishLaunchAsyncHandler;
import com.epam.ta.reportportal.ws.handler.StartLaunchAsyncHandler;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.epam.ta.reportportal.auth.permissions.Permissions.ALLOWED_TO_REPORT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/{projectName}/launch/async")
@PreAuthorize(ALLOWED_TO_REPORT)
public class AsyncLaunhController {

    private final StartLaunchAsyncHandler startLaunchHandler;
    private final FinishLaunchAsyncHandler finishLaunchHandler;

    public AsyncLaunhController(StartLaunchAsyncHandler startLaunchHandler, FinishLaunchAsyncHandler finishLaunchHandler) {
        this.startLaunchHandler = startLaunchHandler;
        this.finishLaunchHandler = finishLaunchHandler;
    }

    @PostMapping
    @ResponseStatus(CREATED)
    @ApiOperation("Starts launch for specified project")
    public StartLaunchRS startLaunch(@PathVariable String projectName,
                                     @ApiParam(value = "Start launch request body", required = true) @RequestBody @Validated StartLaunchRQ startLaunchRQ,
                                     @AuthenticationPrincipal ReportPortalUser user) {
        return startLaunchHandler.startLaunchAsync(user, projectName, startLaunchRQ);
    }

    @PutMapping(value = "/{launchId}/finish")
    @ResponseStatus(OK)
    @ApiOperation("Finish launch for specified project")
    public OperationCompletionRS finishLaunch(@PathVariable String projectName,
                                              @PathVariable Long launchId,
                                              @RequestBody @Validated FinishExecutionRQ finishLaunchRQ,
                                              @AuthenticationPrincipal ReportPortalUser user) {
        return finishLaunchHandler.finishLaunch(finishLaunchRQ, launchId, projectName, user.getUsername());
    }

}
