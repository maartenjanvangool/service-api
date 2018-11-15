package com.epam.ta.reportportal.ws.handler.impl;

import com.epam.ta.reportportal.auth.ReportPortalUser;
import com.epam.ta.reportportal.dao.LaunchRepository;
import com.epam.ta.reportportal.entity.launch.Launch;
import com.epam.ta.reportportal.entity.project.ProjectRole;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.converter.builders.LaunchBuilder;
import com.epam.ta.reportportal.ws.handler.StartLaunchAsyncHandler;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.launch.Mode;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import com.epam.ta.reportportal.ws.rabbit.MessageHeaders;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

import static com.epam.ta.reportportal.commons.EntityUtils.normalizeId;
import static com.epam.ta.reportportal.core.configs.RabbitMqConfiguration.QUEUE_START_LAUNCH;
import static com.epam.ta.reportportal.util.ProjectExtractor.extractProjectDetails;

@Component
public class StartLaunchAsyncHandlerImpl implements StartLaunchAsyncHandler {

    private final AmqpTemplate amqpTemplate;
    private final LaunchRepository launchRepository;

    public StartLaunchAsyncHandlerImpl(AmqpTemplate amqpTemplate, LaunchRepository launchRepository) {
        this.amqpTemplate = amqpTemplate;
        this.launchRepository = launchRepository;
    }

    @Override
    public StartLaunchRS startLaunchAsync(ReportPortalUser user, String projectName, StartLaunchRQ startLaunchRQ) {
        validateRoles(extractProjectDetails(user, normalizeId(projectName)), startLaunchRQ);

        String uuid = startLaunchRQ.getUuid();

        if (StringUtils.isEmpty(uuid)) {
            startLaunchRQ.setUuid(UUID.randomUUID().toString());
        }

        Long launchId = launchRepository.getNextId();
        startLaunchRQ.setId(launchId);

        amqpTemplate.convertAndSend(QUEUE_START_LAUNCH, startLaunchRQ, message -> {
            Map<String, Object> headers = message.getMessageProperties().getHeaders();
            headers.put(MessageHeaders.USERNAME, user.getUsername());
            headers.put(MessageHeaders.PROJECT_NAME, normalizeId(projectName));
            headers.put(MessageHeaders.LAUNCH_ID, launchId);
            return message;
        });
        StartLaunchRS startLaunchRS = new StartLaunchRS();
        startLaunchRQ.setId(launchId);
        startLaunchRQ.setUuid(uuid);
        return startLaunchRS;
    }

    @Override
    public void startLaunch(ReportPortalUser user, String projectName, StartLaunchRQ startLaunchRQ, Long launchId) {
        Long projectId = extractProjectDetails(user, normalizeId(projectName)).getProjectId();
        Launch launch = new LaunchBuilder().addStartRQ(startLaunchRQ)
                .addProject(projectId)
                .addUser(user.getUserId())
                .addTags(startLaunchRQ.getTags())
                .get();
        launch.setId(launchId);
        launchRepository.save(launch);
    }


    /**
     * Validate {@link ReportPortalUser} credentials
     *
     * @param projectDetails {@link com.epam.ta.reportportal.auth.ReportPortalUser.ProjectDetails}
     * @param startLaunchRQ  {@link StartLaunchRQ}
     */
    private void validateRoles(ReportPortalUser.ProjectDetails projectDetails, StartLaunchRQ startLaunchRQ) {
        if (startLaunchRQ.getMode() == Mode.DEBUG && projectDetails.getProjectRole() == ProjectRole.CUSTOMER) {
            throw new ReportPortalException(ErrorType.ACCESS_DENIED);
        }
    }
}
