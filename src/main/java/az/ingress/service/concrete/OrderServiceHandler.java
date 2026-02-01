package az.ingress.service.concrete;

import az.ingress.annotation.ActionLog;
import az.ingress.client.product.ProductClient;
import az.ingress.client.promo.PromoClient;
import az.ingress.dao.entity.Order;
import az.ingress.dao.entity.OutboxEvent;
import az.ingress.dao.repository.OrderRepository;
import az.ingress.dao.repository.OutboxEventRepository;
import az.ingress.enums.OrderStatus;
import az.ingress.exception.BusinessException;
import az.ingress.exception.ErrorMessage;
import az.ingress.mapstruct.OrderMapper;
import az.ingress.model.client.ProductDto;
import az.ingress.model.client.PromoDto;
import az.ingress.model.event.OrderCreatedEvent;
import az.ingress.model.request.CreateOrderRequest;
import az.ingress.model.request.OrderItemRequest;
import az.ingress.model.response.CreateOrderResponse;
import az.ingress.service.abstraction.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @ActionLog
    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for User: {}", request.getUserId());

        var order = initializeOrder(request);
        var context = buildOrderContext(request);
        
        calculationService.calculateOrder(order, context);

        var savedOrder = orderRepository.save(order);
        saveOutboxEvent(savedOrder);

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
        var products = fetchProducts(request);
        var globalPromo = resolvePromo(request.getPromoCode());
        var itemPromos = collectItemPromos(request);

        return OrderContext.builder()
                .requests(request.getItems())
                .products(products)
                .globalPromo(globalPromo)
                .itemPromos(itemPromos)
                .build();
    }

    private Map<String, ProductDto> fetchProducts(CreateOrderRequest request) {
        return request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .map(productClient::getProductById)
                .collect(Collectors.toMap(ProductDto::getId, Function.identity()));
    }

    private PromoDto resolvePromo(String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            return null;
        }
        var promo = promoClient.validateAndGetPromo(promoCode);
        if (promo == null) {
            throw new BusinessException(ErrorMessage.PROMO_NOT_FOUND, promoCode);
        }
        return promo;
    }

    private Map<String, PromoDto> collectItemPromos(CreateOrderRequest request) {
        var itemPromos = new HashMap<String, PromoDto>();
        request.getItems().stream()
                .map(OrderItemRequest::getPromoCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .forEach(code -> itemPromos.put(code, resolvePromo(code)));
        return itemPromos;
    }

    @SneakyThrows
    private void saveOutboxEvent(Order order) {
        var eventPayload = OrderCreatedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .build();

        var mapPayload = objectMapper.convertValue(eventPayload, new TypeReference<Map<String, Object>>() {});

        var event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId().toString())
                .type("ORDER_CREATED")
                .payload(mapPayload)
                .processed(true)
                .build();

        outboxRepository.save(event);
        eventPublisher.publishEvent(eventPayload);
    }
}
