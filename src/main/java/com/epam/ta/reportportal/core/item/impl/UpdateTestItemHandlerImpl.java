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

package com.epam.ta.reportportal.core.item.impl;

import com.epam.ta.reportportal.commons.ReportPortalUser;
import com.epam.ta.reportportal.commons.validation.BusinessRuleViolationException;
import com.epam.ta.reportportal.commons.validation.Suppliers;
import com.epam.ta.reportportal.core.analyzer.auto.LogIndexer;
import com.epam.ta.reportportal.core.analyzer.auto.impl.AnalyzerUtils;
import com.epam.ta.reportportal.core.events.MessageBus;
import com.epam.ta.reportportal.core.events.activity.ItemIssueTypeDefinedEvent;
import com.epam.ta.reportportal.core.events.activity.LinkTicketEvent;
import com.epam.ta.reportportal.core.item.UpdateTestItemHandler;
import com.epam.ta.reportportal.core.item.impl.status.StatusChangingStrategy;
import com.epam.ta.reportportal.dao.*;
import com.epam.ta.reportportal.entity.ItemAttribute;
import com.epam.ta.reportportal.entity.activity.ActivityAction;
import com.epam.ta.reportportal.entity.bts.Ticket;
import com.epam.ta.reportportal.entity.enums.StatusEnum;
import com.epam.ta.reportportal.entity.enums.TestItemIssueGroup;
import com.epam.ta.reportportal.entity.enums.TestItemTypeEnum;
import com.epam.ta.reportportal.entity.item.TestItem;
import com.epam.ta.reportportal.entity.item.issue.IssueEntity;
import com.epam.ta.reportportal.entity.item.issue.IssueType;
import com.epam.ta.reportportal.entity.launch.Launch;
import com.epam.ta.reportportal.entity.project.Project;
import com.epam.ta.reportportal.entity.project.ProjectRole;
import com.epam.ta.reportportal.entity.user.UserRole;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.util.ItemInfoUtils;
import com.epam.ta.reportportal.ws.converter.builders.IssueEntityBuilder;
import com.epam.ta.reportportal.ws.converter.builders.TestItemBuilder;
import com.epam.ta.reportportal.ws.converter.converters.IssueConverter;
import com.epam.ta.reportportal.ws.converter.converters.ItemAttributeConverter;
import com.epam.ta.reportportal.ws.converter.converters.TicketConverter;
import com.epam.ta.reportportal.ws.model.BulkInfoUpdateRQ;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.activity.TestItemActivityResource;
import com.epam.ta.reportportal.ws.model.issue.DefineIssueRQ;
import com.epam.ta.reportportal.ws.model.issue.Issue;
import com.epam.ta.reportportal.ws.model.issue.IssueDefinition;
import com.epam.ta.reportportal.ws.model.item.ExternalIssueRQ;
import com.epam.ta.reportportal.ws.model.item.LinkExternalIssueRQ;
import com.epam.ta.reportportal.ws.model.item.UnlinkExternalIssueRQ;
import com.epam.ta.reportportal.ws.model.item.UpdateTestItemRQ;
import com.epam.ta.reportportal.ws.model.project.AnalyzerConfig;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.epam.ta.reportportal.commons.Predicates.*;
import static com.epam.ta.reportportal.commons.validation.BusinessRule.expect;
import static com.epam.ta.reportportal.util.Predicates.ITEM_CAN_BE_INDEXED;
import static com.epam.ta.reportportal.ws.converter.converters.TestItemConverter.TO_ACTIVITY_RESOURCE;
import static com.epam.ta.reportportal.ws.model.ErrorType.*;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Default implementation of {@link UpdateTestItemHandler}
 *
 * @author Pavel Bortnik
 */
@Service
public class UpdateTestItemHandlerImpl implements UpdateTestItemHandler {

	public static final String INITIAL_STATUS_ATTRIBUTE_KEY = "initialStatus";

	private final ProjectRepository projectRepository;

	private final LaunchRepository launchRepository;

	private final TestItemRepository testItemRepository;

	private final LogRepository logRepository;

	private final TicketRepository ticketRepository;

	private final IssueTypeHandler issueTypeHandler;

	private final MessageBus messageBus;

	private final LogIndexer logIndexer;

	private final IssueEntityRepository issueEntityRepository;

	private final Map<StatusEnum, StatusChangingStrategy> statusChangingStrategyMapping;

	@Autowired
	public UpdateTestItemHandlerImpl(ProjectRepository projectRepository, LaunchRepository launchRepository,
			TestItemRepository testItemRepository, LogRepository logRepository, TicketRepository ticketRepository,
			IssueTypeHandler issueTypeHandler, MessageBus messageBus, LogIndexer logIndexer, IssueEntityRepository issueEntityRepository,
			Map<StatusEnum, StatusChangingStrategy> statusChangingStrategyMapping) {
		this.projectRepository = projectRepository;
		this.launchRepository = launchRepository;
		this.testItemRepository = testItemRepository;
		this.logRepository = logRepository;
		this.ticketRepository = ticketRepository;
		this.issueTypeHandler = issueTypeHandler;
		this.messageBus = messageBus;
		this.logIndexer = logIndexer;
		this.issueEntityRepository = issueEntityRepository;
		this.statusChangingStrategyMapping = statusChangingStrategyMapping;
	}

	@Override
	public List<Issue> defineTestItemsIssues(ReportPortalUser.ProjectDetails projectDetails, DefineIssueRQ defineIssue,
			ReportPortalUser user) {
		Project project = projectRepository.findById(projectDetails.getProjectId())
				.orElseThrow(() -> new ReportPortalException(PROJECT_NOT_FOUND, projectDetails.getProjectId()));
		AnalyzerConfig analyzerConfig = AnalyzerUtils.getAnalyzerConfig(project);

		List<String> errors = new ArrayList<>();
		List<IssueDefinition> definitions = defineIssue.getIssues();
		expect(CollectionUtils.isEmpty(definitions), equalTo(false)).verify(FAILED_TEST_ITEM_ISSUE_TYPE_DEFINITION);
		List<Issue> updated = new ArrayList<>(defineIssue.getIssues().size());
		List<ItemIssueTypeDefinedEvent> events = new ArrayList<>();

		// key - launch id, value - list of item ids
		Map<Long, List<Long>> logsToReindexMap = new HashMap<>();
		List<Long> logIdsToCleanIndex = new ArrayList<>();

		definitions.forEach(issueDefinition -> {
			try {
				TestItem testItem = testItemRepository.findById(issueDefinition.getId())
						.orElseThrow(() -> new BusinessRuleViolationException(Suppliers.formattedSupplier(
								"Cannot update issue type for test item '{}', cause it is not found.",
								issueDefinition.getId()
						).get()));

				verifyTestItem(testItem, issueDefinition.getId());
				TestItemActivityResource before = TO_ACTIVITY_RESOURCE.apply(testItem, projectDetails.getProjectId());

				Issue issue = issueDefinition.getIssue();
				IssueType issueType = issueTypeHandler.defineIssueType(projectDetails.getProjectId(), issue.getIssueType());

				IssueEntity issueEntity = new IssueEntityBuilder(testItem.getItemResults().getIssue()).addIssueType(issueType)
						.addDescription(issue.getComment())
						.addIgnoreFlag(issue.getIgnoreAnalyzer())
						.addAutoAnalyzedFlag(issue.getAutoAnalyzed())
						.get();

				ofNullable(issueDefinition.getIssue().getExternalSystemIssues()).ifPresent(issues -> {
					Set<Ticket> tickets = collectTickets(issues, user.getUsername());
					issueEntity.getTickets().removeIf(it -> !tickets.contains(it));
					issueEntity.getTickets().addAll(tickets);
					tickets.stream().filter(it -> CollectionUtils.isEmpty(it.getIssues())).forEach(it -> it.getIssues().add(issueEntity));
				});

				issueEntity.setTestItemResults(testItem.getItemResults());
				issueEntityRepository.save(issueEntity);
				testItem.getItemResults().setIssue(issueEntity);

				testItemRepository.save(testItem);

				if (ITEM_CAN_BE_INDEXED.test(testItem)) {
					Long launchId = testItem.getLaunchId();
					Long itemId = testItem.getItemId();
					if (logsToReindexMap.containsKey(launchId)) {
						logsToReindexMap.get(launchId).add(itemId);
					} else {
						List<Long> itemIds = Lists.newArrayList();
						itemIds.add(itemId);
						logsToReindexMap.put(launchId, itemIds);
					}
				} else {
					logIdsToCleanIndex.addAll(logRepository.findIdsByTestItemId(testItem.getItemId()));
				}

				updated.add(IssueConverter.TO_MODEL.apply(issueEntity));

				TestItemActivityResource after = TO_ACTIVITY_RESOURCE.apply(testItem, projectDetails.getProjectId());

				events.add(new ItemIssueTypeDefinedEvent(before, after, user.getUserId(), user.getUsername()));
			} catch (BusinessRuleViolationException e) {
				errors.add(e.getMessage());
			}
		});
		expect(errors.isEmpty(), equalTo(TRUE)).verify(FAILED_TEST_ITEM_ISSUE_TYPE_DEFINITION, errors.toString());
		if (!logsToReindexMap.isEmpty()) {
			logsToReindexMap.forEach((key, value) -> logIndexer.indexItemsLogs(project.getId(), key, value, analyzerConfig));
		}
		if (!logIdsToCleanIndex.isEmpty()) {
			logIndexer.cleanIndex(project.getId(), logIdsToCleanIndex);
		}
		events.forEach(messageBus::publishActivity);
		return updated;
	}

	@Override
	public OperationCompletionRS updateTestItem(ReportPortalUser.ProjectDetails projectDetails, Long itemId, UpdateTestItemRQ rq,
			ReportPortalUser user) {
		TestItem testItem = testItemRepository.findById(itemId)
				.orElseThrow(() -> new ReportPortalException(ErrorType.TEST_ITEM_NOT_FOUND, itemId));

		validate(projectDetails, user, testItem);

		Optional<StatusEnum> providedStatus = StatusEnum.fromValue(rq.getStatus());
		if (providedStatus.isPresent()) {
			expect(testItem.isHasChildren() && !testItem.getType().sameLevel(TestItemTypeEnum.STEP), equalTo(FALSE)).verify(INCORRECT_REQUEST,
					"Unable to change status on test item with children"
			);
			checkInitialStatusAttribute(testItem);
			StatusEnum actualStatus = testItem.getItemResults().getStatus();
			StatusChangingStrategy strategy = statusChangingStrategyMapping.get(actualStatus);
			expect(strategy, notNull()).verify(INCORRECT_REQUEST,
					"Actual status: " + actualStatus + " can not be changed to: " + providedStatus.get()
			);
			strategy.changeStatus(testItem, providedStatus.get(), user, projectDetails.getProjectId());
		}
		testItem = new TestItemBuilder(testItem).overwriteAttributes(rq.getAttributes()).addDescription(rq.getDescription()).get();
		testItemRepository.save(testItem);
		return new OperationCompletionRS("TestItem with ID = '" + testItem.getItemId() + "' successfully updated.");
	}

	@Override
	public List<OperationCompletionRS> processExternalIssues(ExternalIssueRQ request, ReportPortalUser.ProjectDetails projectDetails,
			ReportPortalUser user) {
		List<String> errors = new ArrayList<>();

		List<TestItem> testItems = testItemRepository.findAllById(request.getTestItemIds());
		List<TestItemActivityResource> before = testItems.stream()
				.map(it -> TO_ACTIVITY_RESOURCE.apply(it, projectDetails.getProjectId()))
				.collect(Collectors.toList());

		if (request.getClass().equals(LinkExternalIssueRQ.class)) {
			LinkExternalIssueRQ linkRequest = (LinkExternalIssueRQ) request;
			List<Ticket> existedTickets = collectExistedTickets(linkRequest.getIssues());
			Set<Ticket> ticketsFromRq = collectTickets(linkRequest.getIssues(), user.getUsername());
			linkIssues(testItems, existedTickets, ticketsFromRq, errors);
		}

		if (request.getClass().equals(UnlinkExternalIssueRQ.class)) {
			unlinkIssues(testItems, (UnlinkExternalIssueRQ) request, errors);
		}
		expect(errors.isEmpty(), equalTo(TRUE)).verify(FAILED_TEST_ITEM_ISSUE_TYPE_DEFINITION, errors.toString());
		testItemRepository.saveAll(testItems);
		List<TestItemActivityResource> after = testItems.stream()
				.map(it -> TO_ACTIVITY_RESOURCE.apply(it, projectDetails.getProjectId()))
				.collect(Collectors.toList());

		before.forEach(it -> messageBus.publishActivity(new LinkTicketEvent(it,
				after.stream().filter(t -> t.getId().equals(it.getId())).findFirst().get(),
				user.getUserId(),
				user.getUsername(),
				ActivityAction.LINK_ISSUE
		)));
		return testItems.stream()
				.map(testItem -> new OperationCompletionRS("TestItem with ID = '" + testItem.getItemId() + "' successfully updated."))
				.collect(toList());
	}

	private void checkInitialStatusAttribute(TestItem testItem) {
		Optional<ItemAttribute> statusAttribute = testItem.getAttributes()
				.stream()
				.filter(attribute -> INITIAL_STATUS_ATTRIBUTE_KEY.equalsIgnoreCase(attribute.getKey()) && attribute.isSystem())
				.findAny();
		if (!statusAttribute.isPresent()) {
			ItemAttribute initialStatusAttribute = new ItemAttribute(INITIAL_STATUS_ATTRIBUTE_KEY,
					testItem.getItemResults().getStatus().getExecutionCounterField(),
					true
			);
			initialStatusAttribute.setTestItem(testItem);
			testItem.getAttributes().add(initialStatusAttribute);
		}
	}

	private void linkIssues(List<TestItem> items, List<Ticket> existedTickets, Set<Ticket> ticketsFromRq, List<String> errors) {
		items.forEach(testItem -> {
			try {
				verifyTestItem(testItem, testItem.getItemId());
				IssueEntity issue = testItem.getItemResults().getIssue();
				issue.getTickets().addAll(existedTickets);
				issue.getTickets().addAll(ticketsFromRq);
				issue.setAutoAnalyzed(false);
			} catch (Exception e) {
				errors.add(e.getMessage());
			}
		});
	}

	private void unlinkIssues(List<TestItem> items, UnlinkExternalIssueRQ request, List<String> errors) {
		items.forEach(testItem -> {
			try {
				verifyTestItem(testItem, testItem.getItemId());
				IssueEntity issue = testItem.getItemResults().getIssue();
				if (issue.getTickets().removeIf(it -> request.getTicketIds().contains(it.getTicketId()))) {
					issue.setAutoAnalyzed(false);
				}
			} catch (BusinessRuleViolationException e) {
				errors.add(e.getMessage());
			}
		});
	}

	@Override
	public void resetItemsIssue(List<Long> itemIds, Long projectId, ReportPortalUser user) {
		itemIds.forEach(itemId -> {
			TestItem item = testItemRepository.findById(itemId).orElseThrow(() -> new ReportPortalException(TEST_ITEM_NOT_FOUND, itemId));
			TestItemActivityResource before = TO_ACTIVITY_RESOURCE.apply(item, projectId);

			IssueType issueType = issueTypeHandler.defineIssueType(projectId, TestItemIssueGroup.TO_INVESTIGATE.getLocator());
			IssueEntity issueEntity = new IssueEntityBuilder(issueEntityRepository.findById(itemId)
					.orElseThrow(() -> new ReportPortalException(ErrorType.ISSUE_TYPE_NOT_FOUND, itemId))).addIssueType(issueType)
					.addAutoAnalyzedFlag(false)
					.get();
			issueEntityRepository.save(issueEntity);
			item.getItemResults().setIssue(issueEntity);

			TestItemActivityResource after = TO_ACTIVITY_RESOURCE.apply(item, projectId);
			if (!StringUtils.equalsIgnoreCase(before.getIssueTypeLongName(), after.getIssueTypeLongName())) {
				ItemIssueTypeDefinedEvent event = new ItemIssueTypeDefinedEvent(before, after, user.getUserId(), user.getUsername());
				messageBus.publishActivity(event);
			}
		});
	}

	@Override
	public OperationCompletionRS bulkInfoUpdate(BulkInfoUpdateRQ bulkUpdateRq, ReportPortalUser.ProjectDetails projectDetails) {
		expect(projectRepository.existsById(projectDetails.getProjectId()), equalTo(TRUE)).verify(PROJECT_NOT_FOUND,
				projectDetails.getProjectId()
		);

		List<TestItem> items = testItemRepository.findAllById(bulkUpdateRq.getIds());
		items.forEach(it -> ItemInfoUtils.updateDescription(bulkUpdateRq.getDescription(), it.getDescription())
				.ifPresent(it::setDescription));

		bulkUpdateRq.getAttributes().forEach(it -> {
			switch (it.getAction()) {
				case DELETE: {
					items.forEach(item -> {
						ItemAttribute toDelete = ItemInfoUtils.findAttributeByResource(item.getAttributes(), it.getFrom());
						item.getAttributes().remove(toDelete);
					});
					break;
				}
				case UPDATE: {
					items.forEach(item -> ItemInfoUtils.updateAttribute(item.getAttributes(), it));
					break;
				}
				case CREATE: {
					items.stream().filter(item -> ItemInfoUtils.containsAttribute(item.getAttributes(), it.getTo())).forEach(item -> {
						ItemAttribute itemAttribute = ItemAttributeConverter.FROM_RESOURCE.apply(it.getTo());
						itemAttribute.setTestItem(item);
						item.getAttributes().add(itemAttribute);
					});
					break;
				}
			}
		});

		return new OperationCompletionRS("Attributes successfully updated");
	}

	/**
	 * Finds tickets that are existed in db and removes them from request.
	 *
	 * @param externalIssues {@link com.epam.ta.reportportal.ws.model.issue.Issue.ExternalSystemIssue}
	 * @return List of existed tickets in db.
	 */
	private List<Ticket> collectExistedTickets(Collection<Issue.ExternalSystemIssue> externalIssues) {
		if (CollectionUtils.isEmpty(externalIssues)) {
			return Collections.emptyList();
		}
		List<Ticket> existedTickets = ticketRepository.findByTicketIdIn(externalIssues.stream()
				.map(Issue.ExternalSystemIssue::getTicketId)
				.collect(toList()));
		List<String> existedTicketsIds = existedTickets.stream().map(Ticket::getTicketId).collect(toList());
		externalIssues.removeIf(it -> existedTicketsIds.contains(it.getTicketId()));
		return existedTickets;
	}

	/**
	 * TODO document this
	 *
	 * @param externalIssues {@link com.epam.ta.reportportal.ws.model.issue.Issue.ExternalSystemIssue}
	 * @param username       {@link com.epam.ta.reportportal.entity.user.User#login}
	 * @return {@link Set} of the {@link Ticket}
	 */
	private Set<Ticket> collectTickets(Collection<Issue.ExternalSystemIssue> externalIssues, String username) {
		if (CollectionUtils.isEmpty(externalIssues)) {
			return Collections.emptySet();
		}
		return externalIssues.stream().map(it -> {
			Ticket ticket;
			Optional<Ticket> ticketOptional = ticketRepository.findByTicketId(it.getTicketId());
			if (ticketOptional.isPresent()) {
				ticket = ticketOptional.get();
				ticket.setUrl(it.getUrl());
				ticket.setBtsProject(it.getBtsProject());
				ticket.setBtsUrl(it.getBtsUrl());
			} else {
				ticket = TicketConverter.TO_TICKET.apply(it);
			}
			ticket.setSubmitter(username);
			ticket.setSubmitDate(ofNullable(it.getSubmitDate()).map(millis -> LocalDateTime.ofInstant(Instant.ofEpochMilli(millis),
					ZoneOffset.UTC
			)).orElse(LocalDateTime.now()));
			return ticket;
		}).collect(toSet());
	}

	/**
	 * Validates test item access ability.
	 *
	 * @param projectDetails Project
	 * @param user           User
	 * @param testItem       Test Item
	 */
	private void validate(ReportPortalUser.ProjectDetails projectDetails, ReportPortalUser user, TestItem testItem) {
		Launch launch = launchRepository.findById(testItem.getLaunchId())
				.orElseThrow(() -> new ReportPortalException(LAUNCH_NOT_FOUND, testItem.getLaunchId()));
		if (user.getUserRole() != UserRole.ADMINISTRATOR) {
			expect(launch.getProjectId(), equalTo(projectDetails.getProjectId())).verify(ACCESS_DENIED,
					"Launch is not under the specified project."
			);
			if (projectDetails.getProjectRole().lowerThan(ProjectRole.PROJECT_MANAGER)) {
				expect(user.getUserId(), Predicate.isEqual(launch.getUserId())).verify(ACCESS_DENIED, "You are not a launch owner.");
			}
		}
	}

	/**
	 * Complex of domain verification for test item. Verifies that test item
	 * domain object could be processed correctly.
	 *
	 * @param id - test item id
	 * @throws BusinessRuleViolationException when business rule violation
	 */
	private void verifyTestItem(TestItem item, Long id) throws BusinessRuleViolationException {
		expect(item.getItemResults(),
				notNull(),
				Suppliers.formattedSupplier("Test item results were not found for test item with id = '{}", item.getItemId())
		).verify();

		expect(item.getItemResults().getStatus(), not(equalTo(StatusEnum.PASSED)), Suppliers.formattedSupplier(
				"Issue status update cannot be applied on {} test items, cause it is not allowed.",
				StatusEnum.PASSED.name()
		)).verify();

		expect(item.isHasChildren(),
				equalTo(FALSE),
				Suppliers.formattedSupplier(
						"It is not allowed to update issue type for items with descendants. Test item '{}' has descendants.",
						id
				)
		).verify();

		expect(item.getItemResults().getIssue(),
				notNull(),
				Suppliers.formattedSupplier(
						"Cannot update issue type for test item '{}', cause there is no info about actual issue type value.",
						id
				)
		).verify();

		expect(item.getItemResults().getIssue().getIssueType(),
				notNull(),
				Suppliers.formattedSupplier("Cannot update issue type for test item {}, cause it's actual issue type value is not provided.",
						id
				)
		).verify();
	}
}
