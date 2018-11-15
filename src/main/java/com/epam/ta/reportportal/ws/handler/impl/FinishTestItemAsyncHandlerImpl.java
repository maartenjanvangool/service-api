package com.epam.ta.reportportal.ws.handler.impl;

import com.epam.ta.reportportal.core.configs.RabbitMqConfiguration;
import com.epam.ta.reportportal.ws.handler.FinishTestItemAsyncHandler;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.rabbit.MessageHeaders;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.epam.ta.reportportal.commons.EntityUtils.normalizeId;

@Component
public class FinishTestItemAsyncHandlerImpl implements FinishTestItemAsyncHandler {

    private final AmqpTemplate amqpTemplate;

    public FinishTestItemAsyncHandlerImpl(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    @Override
    public OperationCompletionRS finishTestItem(FinishTestItemRQ finishExecutionRQ, Long testItemId, String projectName, String username) {
        amqpTemplate.convertAndSend(RabbitMqConfiguration.QUEUE_FINISH_ITEM, finishExecutionRQ, message -> {
            Map<String, Object> headers = message.getMessageProperties().getHeaders();
            headers.put(MessageHeaders.PROJECT_NAME, normalizeId(projectName));
            headers.put(MessageHeaders.ITEM_ID, testItemId);
            headers.put(MessageHeaders.USERNAME, username);
            return message;
        });

        return new OperationCompletionRS("Started completion of test item with ID = '" + testItemId);
    }
}
