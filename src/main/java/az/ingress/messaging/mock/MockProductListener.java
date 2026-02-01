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
@RabbitListener(queues = RabbitMQConfig.MOCK_PRODUCT_QUEUE)
public class MockProductListener {

    private final RabbitTemplate rabbitTemplate;

    @RabbitHandler
    public void reserveProduct(OrderCreatedEvent event) {
        log.debug("Received ORDER_CREATED for Product Reservation (Order: {})", event.getOrderId());

        if (event.getStatus() != az.ingress.enums.OrderStatus.PENDING) {
            return;
        }

        // Simulating Product Failure (quantity > 100 || specific product)
        // if userId is 998, we simulate 'Out of Stock'
        if (Long.valueOf(998).equals(event.getUserId())) {
            log.warn("Simulating OUT OF STOCK failure for Order: {}", event.getOrderId());

            SagaFailureEvent failureEvent = SagaFailureEvent.builder()
                    .orderId(event.getOrderId())
                    .reason("Out of Stock")
                    .source("PRODUCT")
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SAGA_EXCHANGE,
                    RabbitMQConfig.SAGA_FAIL_ROUTING_KEY,
                    failureEvent
            );

            log.info("Published PRODUCT_FAILURE event to {}", RabbitMQConfig.SAGA_EXCHANGE);
        } else {
            // Success Case
            az.ingress.model.event.SagaSuccessEvent successEvent = az.ingress.model.event.SagaSuccessEvent.builder()
                    .orderId(event.getOrderId())
                    .source("PRODUCT")
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SAGA_EXCHANGE,
                    RabbitMQConfig.SAGA_SUCCESS_ROUTING_KEY,
                    successEvent
            );
            log.info("Published PRODUCT_SUCCESS event for Order: {}", event.getOrderId());
        }
    }

    @RabbitHandler
    public void handleOrderCancellation(az.ingress.model.event.OrderCancelledEvent event) {
        log.warn("Received ORDER_CANCELLED event for Order: {}. Releasing STOCK...", event.getOrderId());
        log.info("Stock Released SUCCESSFULLY for Order: {}", event.getOrderId());
    }

    @RabbitHandler
    public void handleOrderConfirmation(az.ingress.model.event.OrderConfirmedEvent event) {
        log.info("Received ORDER_CONFIRMED event for Order: {}. Product mock acknowledges.", event.getOrderId());
    }
}
