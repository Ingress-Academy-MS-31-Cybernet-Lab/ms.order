package az.ingress.service.concrete;

import az.ingress.dao.entity.Order;
import az.ingress.dao.entity.OutboxEvent;
import az.ingress.dao.repository.OrderRepository;
import az.ingress.dao.repository.OutboxEventRepository;
import az.ingress.enums.OrderStatus;
import az.ingress.service.abstraction.OrderSagaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaHandler implements OrderSagaService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public void compensateOrder(UUID orderId, String reason) {
        log.info("Starting compensation for Order: {}, Reason: {}", orderId, reason);

        var orderOptional = orderRepository.findById(orderId);
        if (orderOptional.isEmpty()) {
            log.warn("Order not found for compensation: {}", orderId);
            return;
        }

        Order order = orderOptional.get();

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.info("Order {} is already CANCELLED, skipping compensation.", orderId);
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setFailReason(reason);
        orderRepository.save(order);
        log.info("Order {} status updated to CANCELLED.", orderId);

        saveAndPublishOutboxEvent(order);
    }

    @SneakyThrows
    private void saveAndPublishOutboxEvent(Order order) {
        // Create event object
        az.ingress.model.event.OrderCancelledEvent eventPayload = az.ingress.model.event.OrderCancelledEvent.builder()
                .orderId(order.getId())
                .reason(order.getFailReason())
                .build();

        // Convert to map for Outbox (DB storage)
        @SuppressWarnings("unchecked")
        Map<String, Object> mapPayload = objectMapper.convertValue(eventPayload, Map.class);

        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId().toString())
                .type("ORDER_CANCELLED")
                .payload(mapPayload)
                .processed(true) // Mark as processed
                .build();

        outboxEventRepository.save(event);

        // Publish to RabbitMQ (Using Order Events Exchange for status updates)
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(az.ingress.config.RabbitMQConfig.ORDER_EVENTS_EXCHANGE, "", eventPayload);
                log.info("Published ORDER_CANCELLED event for Order: {}", order.getId());
            }
        });
    }


    @Override
    @Transactional
    public void handleSagaSuccess(UUID orderId, String source) {
        // Fallback for interface consistency if called directly
        handleSagaSuccess(az.ingress.model.event.SagaSuccessEvent.builder()
                .orderId(orderId)
                .source(source)
                .build());
    }

    @Transactional
    public void handleSagaSuccess(az.ingress.model.event.SagaSuccessEvent event) {
        UUID orderId = event.getOrderId();
        String source = event.getSource();
        
        log.info("Handling SAGA Success for Order: {}, Source: {}", orderId, source);

        var orderOptional = orderRepository.findById(orderId);
        if (orderOptional.isEmpty()) {
            log.warn("Order not found for SAGA Success: {}", orderId);
            return;
        }

        Order order = orderOptional.get();

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.warn("Order {} is CANCELLED! success event from {} ignored.", orderId, source);
            return;
        }

        if (OrderStatus.CONFIRMED.equals(order.getStatus())) {
            log.info("Order {} is already CONFIRMED.", orderId);
            return;
        }

        // State Machine Logic
        switch (source) {
            case "PAYMENT":
                if (event.getPaymentId() != null) {
                    order.setPaymentId(event.getPaymentId());
                }
                if (OrderStatus.PENDING.equals(order.getStatus())) {
                    order.setStatus(OrderStatus.PAYMENT_PROCESSED);
                } else if (OrderStatus.PRODUCT_PROCESSED.equals(order.getStatus())) {
                    order.setStatus(OrderStatus.CONFIRMED);
                }
                break;

            case "PRODUCT":
                if (OrderStatus.PENDING.equals(order.getStatus())) {
                    order.setStatus(OrderStatus.PRODUCT_PROCESSED);
                } else if (OrderStatus.PAYMENT_PROCESSED.equals(order.getStatus())) {
                    order.setStatus(OrderStatus.CONFIRMED);
                }
                break;

            default:
                log.warn("Unknown SAGA Source: {}", source);
                return;
        }

        orderRepository.save(order);
        if (OrderStatus.CONFIRMED.equals(order.getStatus())) {
            log.info("Order {} status updated to CONFIRMED. Publishing OrderConfirmedEvent.", order.getId());
            saveAndPublishConfirmedEvent(order);
        }
    }

    @SneakyThrows
    private void saveAndPublishConfirmedEvent(Order order) {
        // Create event object
        az.ingress.model.event.OrderConfirmedEvent eventPayload = az.ingress.model.event.OrderConfirmedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .build();

        // Convert to map for Outbox (DB storage)
        @SuppressWarnings("unchecked")
        Map<String, Object> mapPayload = objectMapper.convertValue(eventPayload, Map.class);

        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId().toString())
                .type("ORDER_CONFIRMED")
                .payload(mapPayload)
                .processed(true) // Mark as processed
                .build();

        outboxEventRepository.save(event);

        // Publish to RabbitMQ
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(az.ingress.config.RabbitMQConfig.ORDER_EVENTS_EXCHANGE, "", eventPayload);
                log.info("Published ORDER_CONFIRMED event for Order: {}", order.getId());
            }
        });
    }
}
