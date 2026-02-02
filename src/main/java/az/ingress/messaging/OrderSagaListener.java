package az.ingress.messaging;

import az.ingress.config.RabbitMQConfig;
import az.ingress.model.event.SagaFailureEvent;
import az.ingress.service.abstraction.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaListener {

    private final OrderSagaService orderSagaService;

    @RabbitListener(queues = RabbitMQConfig.SAGA_ROLLBACK_QUEUE)
    public void handleSagaFailure(SagaFailureEvent event) {
        log.debug("Received SAGA failure event for Order: {}", event.getOrderId());
        log.info("Saga failure details - Source: {}, Reason: {}", event.getSource(), event.getReason());

        orderSagaService.compensateOrder(event.getOrderId(), event.getReason());
    }

    @RabbitListener(queues = RabbitMQConfig.SAGA_SUCCESS_QUEUE)
    public void handleSagaSuccess(az.ingress.model.event.SagaSuccessEvent event) {
        log.debug("Received SAGA SUCCESS event for Order: {}, Source: {}", event.getOrderId(), event.getSource());
        orderSagaService.handleSagaSuccess(event);
    }
}
