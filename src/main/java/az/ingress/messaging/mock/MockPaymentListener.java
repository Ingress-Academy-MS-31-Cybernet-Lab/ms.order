package az.ingress.messaging.mock;

import az.ingress.config.RabbitMQConfig;
import az.ingress.model.event.OrderCreatedEvent;
import az.ingress.model.event.SagaFailureEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitMQConfig.MOCK_PAYMENT_QUEUE)
public class MockPaymentListener {

    private final RabbitTemplate rabbitTemplate;

    @RabbitHandler
    public void processPayment(OrderCreatedEvent event) {
        log.debug("Received ORDER_CREATED for Order: {}", event.getOrderId());

        if (event.getStatus() != az.ingress.enums.OrderStatus.PENDING) {
            log.trace("Ignoring non-PENDING event for Order: {}", event.getOrderId());
            return;
        }

        if (Long.valueOf(999).equals(event.getUserId())) {
            log.warn("Simulating payment failure for Order: {}", event.getOrderId());
            
            SagaFailureEvent failureEvent = SagaFailureEvent.builder()
                    .orderId(event.getOrderId())
                    .reason("Insufficient Funds")
                    .source("PAYMENT")
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SAGA_EXCHANGE,
                    RabbitMQConfig.SAGA_FAIL_ROUTING_KEY,
                    failureEvent
            );
            
            log.info("Published PAYMENT_FAILED event to {}", RabbitMQConfig.SAGA_EXCHANGE);
        } else {
            // Success Case
            String transactionId = java.util.UUID.randomUUID().toString();
            az.ingress.model.event.SagaSuccessEvent successEvent = az.ingress.model.event.SagaSuccessEvent.builder()
                    .orderId(event.getOrderId())
                    .source("PAYMENT")
                    .paymentId(transactionId)
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SAGA_EXCHANGE,
                    RabbitMQConfig.SAGA_SUCCESS_ROUTING_KEY,
                    successEvent
            );
            log.info("Published PAYMENT_SUCCESS event for Order: {}", event.getOrderId());
        }
    }

    @RabbitHandler
    public void handleOrderCancellation(az.ingress.model.event.OrderCancelledEvent event) {
        log.warn("Received ORDER_CANCELLED event for Order: {}. Initiating REFUND...", event.getOrderId());
        log.info("Refund SUCCESSFUL for Order: {}", event.getOrderId());
    }

    @RabbitHandler
    public void handleOrderConfirmation(az.ingress.model.event.OrderConfirmedEvent event) {
        log.info("Received ORDER_CONFIRMED event for Order: {}. Payment mock acknowledges.", event.getOrderId());
    }
}
