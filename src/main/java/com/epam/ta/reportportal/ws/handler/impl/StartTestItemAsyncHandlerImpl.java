package com.epam.ta.reportportal.ws.handler.impl;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.commons.Preconditions;
import com.epam.ta.reportportal.core.item.UniqueIdGenerator;
import com.epam.ta.reportportal.dao.LaunchRepository;
import com.epam.ta.reportportal.dao.LogRepository;
import com.epam.ta.reportportal.dao.TestItemRepository;
import com.epam.ta.reportportal.entity.enums.StatusEnum;
import com.epam.ta.reportportal.entity.item.TestItem;
import com.epam.ta.reportportal.entity.launch.Launch;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.converter.builders.TestItemBuilder;
import com.epam.ta.reportportal.ws.handler.StartTestItemAsyncHandler;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.item.ItemCreatedRS;
import com.epam.ta.reportportal.ws.rabbit.MessageHeaders;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.epam.ta.reportportal.commons.EntityUtils.normalizeId;
import static com.epam.ta.reportportal.commons.Predicates.equalTo;
import static com.epam.ta.reportportal.commons.validation.BusinessRule.expect;
import static com.epam.ta.reportportal.commons.validation.Suppliers.formattedSupplier;
import static com.epam.ta.reportportal.core.configs.RabbitMqConfiguration.QUEUE_START_ITEM;
import static com.epam.ta.reportportal.util.ProjectExtractor.extractProjectDetails;
import static com.epam.ta.reportportal.ws.model.ErrorType.*;

@Component
public class StartTestItemAsyncHandlerImpl implements StartTestItemAsyncHandler {

    private final AmqpTemplate amqpTemplate;
    private final LaunchRepository launchRepository;
    private final TestItemRepository testItemRepository;
    private final LogRepository logRepository;
    private final UniqueIdGenerator uniqueIdGenerator;

    public StartTestItemAsyncHandlerImpl(AmqpTemplate amqpTemplate,
                                         LaunchRepository launchRepository,
                                         TestItemRepository testItemRepository,
                                         LogRepository logRepository,
                                         UniqueIdGenerator uniqueIdGenerator) {
        this.amqpTemplate = amqpTemplate;
        this.launchRepository = launchRepository;
        this.testItemRepository = testItemRepository;
        this.logRepository = logRepository;
        this.uniqueIdGenerator = uniqueIdGenerator;
    }

    @Override
    public EntryCreatedRS startRootItemAsync(ReportPortalUser user, String projectName, StartTestItemRQ startTestItemRQ) {

        Launch launch = launchRepository.findById(startTestItemRQ.getLaunchId())
                .orElseThrow(() -> new ReportPortalException(LAUNCH_NOT_FOUND, startTestItemRQ.getLaunchId().toString()));
        validate(extractProjectDetails(user, normalizeId(projectName)), startTestItemRQ, launch);

        Long itemId = testItemRepository.getNextId();

        amqpTemplate.convertAndSend(QUEUE_START_ITEM, startTestItemRQ, message -> {
            Map<String, Object> headers = message.getMessageProperties().getHeaders();
            headers.put(MessageHeaders.USERNAME, user.getUsername());
            headers.put(MessageHeaders.ITEM_ID, itemId);
            return message;
        });

        ItemCreatedRS itemCreatedRS = new ItemCreatedRS();
        itemCreatedRS.setId(itemId);
        return itemCreatedRS;
    }

    @Override
    public void startRootItem(StartTestItemRQ rq, Long itemId) {

        Launch launch = launchRepository.findById(rq.getLaunchId()).get();

        TestItem item = new TestItemBuilder().addStartItemRequest(rq).addLaunch(launch).get();
        item.setPath(String.valueOf(item.getItemId()));
        if (null == item.getUniqueId()) {
            item.setUniqueId(uniqueIdGenerator.generate(item, launch));
        }
        testItemRepository.save(item);
    }

    @Override
    public EntryCreatedRS startChildItemAsync(ReportPortalUser user, String projectName, StartTestItemRQ startTestItemRQ, Long parentId) {
        TestItem parentItem = testItemRepository.findById(parentId)
                .orElseThrow(() -> new ReportPortalException(TEST_ITEM_NOT_FOUND, parentId.toString()));

        Long launchId = startTestItemRQ.getLaunchId();
        if (!launchRepository.existsById(launchId)) {
            throw new ReportPortalException(LAUNCH_NOT_FOUND, launchId);
        }

        validate(startTestItemRQ, parentItem);

        Long itemId = testItemRepository.getNextId();

        amqpTemplate.convertAndSend(QUEUE_START_ITEM, startTestItemRQ, message -> {
            Map<String, Object> headers = message.getMessageProperties().getHeaders();
            headers.put(MessageHeaders.USERNAME, user.getUsername());
            headers.put(MessageHeaders.ITEM_ID, itemId);
            headers.put(MessageHeaders.PARENT_ID, parentId);
            return message;
        });


        ItemCreatedRS itemCreatedRS = new ItemCreatedRS();
        itemCreatedRS.setId(itemId);
        return itemCreatedRS;
    }

    @Override
    public void startChildItem(StartTestItemRQ rq, Long itemId, Long parentId) {
        TestItem parentItem = testItemRepository.findById(parentId).get();
        Launch launch = launchRepository.findById(rq.getLaunchId()).get();

        TestItem item = new TestItemBuilder().addStartItemRequest(rq).addLaunch(launch).addParent(parentItem).get();

        item.setPath(parentItem.getPath() + "." + item.getItemId());
        if (null == item.getUniqueId()) {
            item.setUniqueId(uniqueIdGenerator.generate(item, launch));
        }
        testItemRepository.save(item);
    }

    /**
     * Validate {@link ReportPortalUser} credentials, {@link Launch#status}
     * and {@link Launch} affiliation to the {@link com.epam.ta.reportportal.entity.project.Project}
     *
     * @param projectDetails {@link com.epam.ta.reportportal.auth.ReportPortalUser.ProjectDetails}
     * @param rq             {@link StartTestItemRQ}
     * @param launch         {@link Launch}
     */
    private void validate(ReportPortalUser.ProjectDetails projectDetails, StartTestItemRQ rq, Launch launch) {
        expect(projectDetails.getProjectId(), equalTo(launch.getProjectId())).verify(ACCESS_DENIED);
        expect(launch.getStatus(), equalTo(StatusEnum.IN_PROGRESS)).verify(START_ITEM_NOT_ALLOWED,
                formattedSupplier("Launch '{}' is not in progress", rq.getLaunchId())
        );
        expect(rq.getStartTime(), Preconditions.sameTimeOrLater(launch.getStartTime())).verify(CHILD_START_TIME_EARLIER_THAN_PARENT,
                rq.getStartTime(),
                launch.getStartTime(),
                launch.getId()
        );
    }

    /**
     * Verifies if the start of a child item is allowed. Conditions are
     * - the item's start time must be same or later than the parent's
     * - the parent item must be in progress
     * - the parent item hasn't any logs
     *
     * @param rq     Start child item request
     * @param parent Parent item
     */
    private void validate(StartTestItemRQ rq, TestItem parent) {
        expect(rq.getStartTime(), Preconditions.sameTimeOrLater(parent.getStartTime())).verify(CHILD_START_TIME_EARLIER_THAN_PARENT,
                rq.getStartTime(),
                parent.getStartTime(),
                parent.getItemId()
        );
        expect(parent.getItemResults().getStatus(), Preconditions.statusIn(StatusEnum.IN_PROGRESS)).verify(START_ITEM_NOT_ALLOWED,
                formattedSupplier("Parent Item '{}' is not in progress", parent.getItemId())
        );
        expect(logRepository.hasLogs(parent.getItemId()), equalTo(false)).verify(START_ITEM_NOT_ALLOWED,
                formattedSupplier("Parent Item '{}' already has log items", parent.getItemId())
        );
    }
}
