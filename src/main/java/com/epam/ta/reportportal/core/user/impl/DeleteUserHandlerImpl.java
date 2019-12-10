/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.ta.reportportal.core.user.impl;

import com.epam.ta.reportportal.auth.acl.ShareableObjectsHandler;
import com.epam.ta.reportportal.commons.Predicates;
import com.epam.ta.reportportal.commons.ReportPortalUser;
import com.epam.ta.reportportal.commons.validation.BusinessRule;
import com.epam.ta.reportportal.core.project.DeleteProjectHandler;
import com.epam.ta.reportportal.core.user.DeleteUserHandler;
import com.epam.ta.reportportal.dao.UserRepository;
import com.epam.ta.reportportal.entity.project.ProjectUtils;
import com.epam.ta.reportportal.entity.user.User;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.*;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Delete user handler
 *
 * @author Aliaksandr_Kazantsau
 * @author Andrei_Ramanchuk
 */
@Service
public class DeleteUserHandlerImpl implements DeleteUserHandler {

	private final UserRepository userRepository;

	private final DeleteProjectHandler deleteProjectHandler;

	private final ShareableObjectsHandler shareableObjectsHandler;

	@Autowired
	public DeleteUserHandlerImpl(UserRepository userRepository, DeleteProjectHandler deleteProjectHandler,
			ShareableObjectsHandler shareableObjectsHandler) {
		this.userRepository = userRepository;
		this.deleteProjectHandler = deleteProjectHandler;
		this.shareableObjectsHandler = shareableObjectsHandler;
	}

	@Override
	public OperationCompletionRS deleteUser(Long userId, ReportPortalUser loggedInUser) {
		User user = userRepository.findById(userId).orElseThrow(() -> new ReportPortalException(ErrorType.USER_NOT_FOUND, userId));
		BusinessRule.expect(Objects.equals(userId, loggedInUser.getUserId()), Predicates.equalTo(false))
				.verify(ErrorType.INCORRECT_REQUEST, "You cannot delete own account");
		ArrayList<Long> projectIdsToDelete = Lists.newArrayList();

		user.getProjects().forEach(userProject -> {
			Long projectId = userProject.getId().getProjectId();
			if (ProjectUtils.isPersonalForUser(userProject.getProject().getProjectType(),
					userProject.getProject().getName(),
					user.getLogin()
			)) {
				projectIdsToDelete.add(projectId);
			} else {
				shareableObjectsHandler.preventSharedObjects(projectId, user.getLogin());
				ProjectUtils.excludeProjectRecipients(Lists.newArrayList(user), userProject.getProject());
			}
		});

		userRepository.delete(user);
		if (CollectionUtils.isNotEmpty(projectIdsToDelete)) {
			deleteProjectHandler.deleteProjects(new DeleteBulkRQ(projectIdsToDelete));
		}
		return new OperationCompletionRS("User with ID = '" + userId + "' successfully deleted.");
	}

	@Override
	public DeleteBulkRS deleteUsers(DeleteBulkRQ deleteBulkRQ, ReportPortalUser currentUser) {
		List<ReportPortalException> exceptions = Lists.newArrayList();
		List<Long> deleted = Lists.newArrayList();
		deleteBulkRQ.getIds().forEach(userId -> {
			try {
				deleteUser(userId, currentUser);
				deleted.add(userId);
			} catch (ReportPortalException rp) {
				exceptions.add(rp);
			}
		});
		return new DeleteBulkRS(deleted, Collections.emptyList(), exceptions.stream().map(ex -> {
			ErrorRS errorResponse = new ErrorRS();
			errorResponse.setErrorType(ex.getErrorType());
			errorResponse.setMessage(ex.getMessage());
			return errorResponse;
		}).collect(Collectors.toList()));
	}
}
