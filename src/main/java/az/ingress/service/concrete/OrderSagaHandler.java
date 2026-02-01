package az.ingress.service.concrete;

import az.ingress.dao.entity.Order;
import az.ingress.dao.entity.OutboxEvent;
import az.ingress.dao.repository.OrderRepository;
import az.ingress.dao.repository.OutboxEventRepository;
import az.ingress.config.RabbitMQConfig;
import az.ingress.enums.OrderStatus;
import az.ingress.model.event.OrderCancelledEvent;
import az.ingress.model.event.OrderConfirmedEvent;
import az.ingress.model.event.SagaSuccessEvent;
import az.ingress.service.abstraction.OrderSagaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        var order = orderOptional.get();

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

        var eventPayload = OrderCancelledEvent.builder()
                .orderId(order.getId())
                .reason(order.getFailReason())
                .build();


        @SuppressWarnings("unchecked")
        Map<String, Object> mapPayload = objectMapper.convertValue(eventPayload, Map.class);

        var event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId().toString())
                .type("ORDER_CANCELLED")
                .payload(mapPayload)
                .processed(true)
                .build();

        outboxEventRepository.save(event);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EVENTS_EXCHANGE, "", eventPayload);
                    log.info("Published ORDER_CANCELLED event for Order: {}", order.getId());
                } catch (Exception e) {
                    log.error("Failed to publish ORDER_CANCELLED event for Order: {}", order.getId(), e);
                }
            }
        });
    }



    @Transactional
    public void handleSagaSuccess(SagaSuccessEvent event) {
        var orderId = event.getOrderId();
        var source = event.getSource();
        
        log.info("Handling SAGA Success for Order: {}, Source: {}", orderId, source);

        var orderOptional = orderRepository.findById(orderId);
        if (orderOptional.isEmpty()) {
            log.warn("Order not found for SAGA Success: {}", orderId);
            return;
        }

        var order = orderOptional.get();

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.warn("Order {} is CANCELLED! success event from {} ignored.", orderId, source);
            return;
        }

        if (OrderStatus.CONFIRMED.equals(order.getStatus())) {
            log.info("Order {} is already CONFIRMED.", orderId);
            return;
        }

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

        var eventPayload = OrderConfirmedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .build();


        @SuppressWarnings("unchecked")
        var mapPayload = (Map<String, Object>) objectMapper.convertValue(eventPayload, Map.class);

        var event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId().toString())
                .type("ORDER_CONFIRMED")
                .payload(mapPayload)
                .processed(true)
                .build();

        outboxEventRepository.save(event);


        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EVENTS_EXCHANGE, "", eventPayload);
                    log.info("Published ORDER_CONFIRMED event for Order: {}", order.getId());
                } catch (Exception e) {
                    log.error("Failed to publish ORDER_CONFIRMED event for Order: {}", order.getId(), e);
                }
            }
        });
    }
}
