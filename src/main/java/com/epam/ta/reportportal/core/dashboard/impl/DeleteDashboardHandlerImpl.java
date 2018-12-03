package com.epam.ta.reportportal.core.dashboard.impl;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.commons.Predicates;
import com.epam.ta.reportportal.commons.validation.BusinessRule;
import com.epam.ta.reportportal.core.dashboard.DeleteDashboardHandler;
import com.epam.ta.reportportal.core.events.MessageBus;
import com.epam.ta.reportportal.core.events.activity.DashboardDeletedEvent;
import com.epam.ta.reportportal.dao.DashboardRepository;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.entity.dashboard.Dashboard;
import com.epam.ta.reportportal.entity.project.ProjectRole;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.converter.converters.DashboardConverter;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Service
public class DeleteDashboardHandlerImpl implements DeleteDashboardHandler {

	private final DashboardRepository dashboardRepository;

	private final ProjectRepository projectRepository;

	private final MessageBus messageBus;

	@Autowired
	public DeleteDashboardHandlerImpl(DashboardRepository dashboardRepository, ProjectRepository projectRepository, MessageBus messageBus) {
		this.dashboardRepository = dashboardRepository;
		this.projectRepository = projectRepository;
		this.messageBus = messageBus;
	}

	@Override
	public OperationCompletionRS deleteDashboard(Long dashboardId, ReportPortalUser.ProjectDetails projectDetails, ReportPortalUser user) {
		Dashboard dashboard = dashboardRepository.findById(dashboardId)
				.orElseThrow(() -> new ReportPortalException(ErrorType.DASHBOARD_NOT_FOUND, dashboardId));
		projectDetails.getProjectRole();
		Map<String, ProjectRole> roles = projectRepository.findProjectRoles(userName);
		AclUtils.isAllowedToEdit(dashboard.getAcl(), userName, roles, dashboard.getName(), userRole);
		BusinessRule.expect(dashboard.getProjectName(), Predicates.equalTo(projectName)).verify(ErrorType.ACCESS_DENIED);

		try {
			dashboardRepository.deleteById(dashboardId);
		} catch (Exception e) {
			throw new ReportPortalException("Error during deleting dashboard item", e);
		}

		messageBus.publishActivity(new DashboardDeletedEvent(DashboardConverter.TO_ACTIVITY_RESOURCE.apply(dashboard), user.getUserId()));
		OperationCompletionRS response = new OperationCompletionRS();
		String msg = "Dashboard with ID = '" + dashboardId + "' successfully deleted.";
		response.setResultMessage(msg);

		return response;
	}
}
