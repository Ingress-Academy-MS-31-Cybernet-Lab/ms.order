package az.ingress.service.concrete;

import az.ingress.client.product.ProductClient;
import az.ingress.client.promo.PromoClient;
import az.ingress.dao.entity.Order;
import az.ingress.dao.entity.OutboxEvent;
import az.ingress.dao.repository.OrderRepository;
import az.ingress.dao.repository.OutboxEventRepository;
import az.ingress.enums.OrderStatus;
import az.ingress.mapstruct.OrderMapper;
import az.ingress.model.client.ProductDto;
import az.ingress.model.client.PromoDto;
import az.ingress.model.request.CreateOrderRequest;
import az.ingress.model.request.OrderItemRequest;
import az.ingress.model.response.CreateOrderResponse;
import az.ingress.service.abstraction.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceHandler implements OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final PromoClient promoClient;
    private final ObjectMapper objectMapper;
    private final OrderCalculationServiceHandler calculationService;

    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for User: {}", request.getUserId());

        Order order = initializeOrder(request);
        OrderContext context = buildOrderContext(request);
        
        calculationService.calculateOrder(order, context);

        Order savedOrder = orderRepository.save(order);
        saveAndPublishOutboxEvent(savedOrder);

        log.info("Order created successfully: {}", savedOrder.getId());
        return orderMapper.toResponse(savedOrder);
    }

    private Order initializeOrder(CreateOrderRequest request) {
        var order = orderMapper.toEntity(request);
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(orderMapper.toOrderAddress(request.getShippingAddress()));
        return order;
    }

    private OrderContext buildOrderContext(CreateOrderRequest request) {
        // Fetch Products
        Map<String, ProductDto> products = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .map(productClient::getProductById)
                .collect(Collectors.toMap(ProductDto::getId, Function.identity()));

        // Fetch Promos
        PromoDto globalPromo = null;
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            globalPromo = promoClient.validateAndGetPromo(request.getPromoCode());
        }

        Map<String, PromoDto> itemPromos = new HashMap<>();
        request.getItems().stream()
                .map(OrderItemRequest::getPromoCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .forEach(code -> itemPromos.put(code, promoClient.validateAndGetPromo(code)));

        return OrderContext.builder()
                .requests(request.getItems())
                .products(products)
                .globalPromo(globalPromo)
                .itemPromos(itemPromos)
                .build();
    }

    @SneakyThrows
    private void saveAndPublishOutboxEvent(Order order) {
        // Build correct event payload
        az.ingress.model.event.OrderCreatedEvent eventPayload = az.ingress.model.event.OrderCreatedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> mapPayload = objectMapper.convertValue(eventPayload, Map.class);

        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId().toString())
                .type("ORDER_CREATED")
                .payload(mapPayload)
                .processed(true) // Mark as processed immediately
                .build();

        outboxRepository.save(event);
        
        // Publish to RabbitMQ after transaction commit
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(az.ingress.config.RabbitMQConfig.ORDER_EVENTS_EXCHANGE, "", eventPayload);
                log.info("Published ORDER_CREATED event for Order: {}", order.getId());
            }
        });
    }
}
