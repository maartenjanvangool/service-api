package com.epam.ta.reportportal.core.dashboard;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.entity.user.UserRole;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public interface DeleteDashboardHandler {

	/**
	 * Delete {@link Dashboard} instance with specified id
	 *
	 * @param dashboardId
	 * @param userName
	 * @param projectName
	 * @return {@link OperationCompletionRS}
	 * @throws ReportPortalException
	 */
	OperationCompletionRS deleteDashboard(Long dashboardId, ReportPortalUser.ProjectDetails projectDetails, ReportPortalUser user);
}
