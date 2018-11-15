package com.epam.ta.reportportal.ws.handler.impl;

import com.epam.ta.reportportal.core.configs.RabbitMqConfiguration;
import com.epam.ta.reportportal.ws.handler.FinishLaunchAsyncHandler;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.rabbit.MessageHeaders;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.epam.ta.reportportal.commons.EntityUtils.normalizeId;

@Component
public class FinishLaunchAsyncHandlerImpl implements FinishLaunchAsyncHandler {

    private final AmqpTemplate amqpTemplate;

    public FinishLaunchAsyncHandlerImpl(RabbitTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    @Override
    public OperationCompletionRS finishLaunch(FinishExecutionRQ finishLaunchRQ, Long launchId, String projectName, String username) {
        amqpTemplate.convertAndSend(RabbitMqConfiguration.QUEUE_FINISH_LAUNCH, finishLaunchRQ, message -> {
            Map<String, Object> headers = message.getMessageProperties().getHeaders();
            headers.put(MessageHeaders.PROJECT_NAME, normalizeId(projectName));
            headers.put(MessageHeaders.LAUNCH_ID, launchId);
            headers.put(MessageHeaders.USERNAME, username);
            return message;
        });
        return new OperationCompletionRS("Started completion of launch with ID = '" + launchId);
    }
}
