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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaHandler implements OrderSagaService {

    private static final String AGGREGATE_TYPE = "ORDER";
    private static final String EVENT_CANCELLED = "ORDER_CANCELLED";
    private static final String EVENT_CONFIRMED = "ORDER_CONFIRMED";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public void compensateOrder(UUID orderId, String reason) {
        log.info("Starting compensation for Order: {}, Reason: {}", orderId, reason);

        var order = findOrderById(orderId);
        if (order == null || isAlreadyCancelled(order, orderId)) {
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setFailReason(reason);
        orderRepository.save(order);
        log.info("Order {} status updated to CANCELLED.", orderId);

        publishCancelledEvent(order);
    }

    @Override
    @Transactional
    public void handleSagaSuccess(SagaSuccessEvent event) {
        var orderId = event.getOrderId();
        var source = event.getSource();

        log.info("Handling SAGA Success for Order: {}, Source: {}", orderId, source);

        var order = findOrderById(orderId);
        if (order == null || isTerminalState(order, orderId, source)) {
            return;
        }

        updateOrderStatus(order, event);
        orderRepository.save(order);

        if (OrderStatus.CONFIRMED.equals(order.getStatus())) {
            log.info("Order {} status updated to CONFIRMED. Publishing OrderConfirmedEvent.", order.getId());
            publishConfirmedEvent(order);
        }
    }

    private Order findOrderById(UUID orderId) {
        var orderOptional = orderRepository.findById(orderId);
        if (orderOptional.isEmpty()) {
            log.warn("Order not found: {}", orderId);
            return null;
        }
        return orderOptional.get();
    }

    private boolean isAlreadyCancelled(Order order, UUID orderId) {
        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.info("Order {} is already CANCELLED, skipping.", orderId);
            return true;
        }
        return false;
    }

    private boolean isTerminalState(Order order, UUID orderId, String source) {
        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.warn("Order {} is CANCELLED! Success event from {} ignored.", orderId, source);
            return true;
        }
        if (OrderStatus.CONFIRMED.equals(order.getStatus())) {
            log.info("Order {} is already CONFIRMED.", orderId);
            return true;
        }
        return false;
    }

    private void updateOrderStatus(Order order, SagaSuccessEvent event) {
        var source = event.getSource();

        switch (source) {
            case "PAYMENT":
                handlePaymentSuccess(order, event);
                break;
            case "PRODUCT":
                handleProductSuccess(order);
                break;
            default:
                log.warn("Unknown SAGA Source: {}", source);
        }
    }

    private void handlePaymentSuccess(Order order, SagaSuccessEvent event) {
        if (event.getPaymentId() != null) {
            order.setPaymentId(event.getPaymentId());
        }

        if (OrderStatus.PENDING.equals(order.getStatus())) {
            order.setStatus(OrderStatus.PAYMENT_PROCESSED);
        } else if (OrderStatus.PRODUCT_PROCESSED.equals(order.getStatus())) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
    }

    private void handleProductSuccess(Order order) {
        if (OrderStatus.PENDING.equals(order.getStatus())) {
            order.setStatus(OrderStatus.PRODUCT_PROCESSED);
        } else if (OrderStatus.PAYMENT_PROCESSED.equals(order.getStatus())) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
    }

    private void publishCancelledEvent(Order order) {
        var eventPayload = OrderCancelledEvent.builder()
                .orderId(order.getId())
                .reason(order.getFailReason())
                .build();

        saveOutboxEvent(order.getId(), EVENT_CANCELLED, eventPayload);
        registerEventPublisher(eventPayload, order.getId(), EVENT_CANCELLED);
    }

    private void publishConfirmedEvent(Order order) {
        var eventPayload = OrderConfirmedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .build();

        saveOutboxEvent(order.getId(), EVENT_CONFIRMED, eventPayload);
        registerEventPublisher(eventPayload, order.getId(), EVENT_CONFIRMED);
    }

    @SneakyThrows
    private void saveOutboxEvent(UUID orderId, String eventType, Object eventPayload) {
        var mapPayload = objectMapper.convertValue(eventPayload, new TypeReference<Map<String, Object>>() {});

        var event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId.toString())
                .type(eventType)
                .payload(mapPayload)
                .processed(true)
                .build();

        outboxEventRepository.save(event);
    }

    private void registerEventPublisher(Object eventPayload, UUID orderId, String eventType) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EVENTS_EXCHANGE, "", eventPayload);
                    log.info("Published {} event for Order: {}", eventType, orderId);
                } catch (Exception e) {
                    log.error("Failed to publish {} event for Order: {}", eventType, orderId, e);
                }
            }
        });
    }
}

