package az.ingress.service

import az.ingress.client.product.ProductClient
import az.ingress.client.promo.PromoClient
import az.ingress.dao.entity.Order
import az.ingress.dao.repository.OrderRepository
import az.ingress.dao.repository.OutboxEventRepository
import az.ingress.enums.OrderStatus
import az.ingress.mapstruct.OrderMapper
import az.ingress.model.client.ProductDto
import az.ingress.model.client.PromoDto
import az.ingress.model.request.CreateOrderRequest
import az.ingress.model.request.OrderItemRequest
import az.ingress.model.request.AddressRequest
import az.ingress.model.response.CreateOrderResponse
import az.ingress.service.concrete.OrderCalculationServiceHandler
import az.ingress.service.concrete.OrderServiceHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

class OrderServiceHandlerTest extends Specification {

    OrderRepository orderRepository = Mock()
    OutboxEventRepository outboxRepository = Mock()
    OrderMapper orderMapper = Mock()
    ProductClient productClient = Mock()
    PromoClient promoClient = Mock()
    ObjectMapper objectMapper = new ObjectMapper()
    OrderCalculationServiceHandler calculationService = Mock()
    ApplicationEventPublisher eventPublisher = Mock()

    OrderServiceHandler service

    def setup() {
        service = new OrderServiceHandler(
                orderRepository,
                outboxRepository,
                orderMapper,
                productClient,
                promoClient,
                objectMapper,
                calculationService,
                eventPublisher
        )
    }

    def "should create order successfully"() {
        given:
        def request = createOrderRequest(1L, "PROMO10")
        def order = new Order()
        def savedOrder = new Order(id: UUID.randomUUID())
        def response = new CreateOrderResponse()
        def product = createProduct("prod-1", BigDecimal.valueOf(100))

        orderMapper.toEntity(request) >> order
        orderMapper.toOrderAddress(_) >> null
        productClient.getProductById("prod-1") >> product
        promoClient.validateAndGetPromo("PROMO10") >> createPromo("PROMO10")
        orderRepository.save(order) >> savedOrder
        orderMapper.toResponse(savedOrder) >> response

        when:
        def result = service.createOrder(request)

        then:
        result == response
        1 * calculationService.calculateOrder(order, _)
        1 * outboxRepository.save(_)
        1 * eventPublisher.publishEvent(_)
    }

    def "should create order without promo code"() {
        given:
        def request = createOrderRequest(2L, null)
        def order = new Order()
        def savedOrder = new Order(id: UUID.randomUUID())
        def product = createProduct("prod-1", BigDecimal.valueOf(50))

        orderMapper.toEntity(request) >> order
        orderMapper.toOrderAddress(_) >> null
        productClient.getProductById("prod-1") >> product
        orderRepository.save(order) >> savedOrder
        orderMapper.toResponse(savedOrder) >> new CreateOrderResponse()

        when:
        service.createOrder(request)

        then:
        0 * promoClient.validateAndGetPromo(_)
        1 * calculationService.calculateOrder(order, _)
    }

    def "should set order status to PENDING on creation"() {
        given:
        def request = createOrderRequest(3L, null)
        def order = new Order()
        def savedOrder = new Order(id: UUID.randomUUID(), status: OrderStatus.PENDING)
        def product = createProduct("prod-1", BigDecimal.valueOf(25))

        orderMapper.toEntity(request) >> order
        orderMapper.toOrderAddress(_) >> null
        productClient.getProductById("prod-1") >> product
        orderRepository.save(_) >> savedOrder
        orderMapper.toResponse(_) >> new CreateOrderResponse()

        when:
        service.createOrder(request)

        then:
        order.status == OrderStatus.PENDING
    }

    private CreateOrderRequest createOrderRequest(Long userId, String promoCode) {
        def request = new CreateOrderRequest()
        request.userId = userId
        request.promoCode = promoCode
        request.shippingAddress = new AddressRequest()
        request.items = [new OrderItemRequest(productId: "prod-1", quantity: 2)]
        return request
    }

    private ProductDto createProduct(String id, BigDecimal price) {
        ProductDto.builder()
                .id(id)
                .price(price)
                .supplierId(1L)
                .build()
    }

    private PromoDto createPromo(String code) {
        PromoDto.builder()
                .code(code)
                .build()
    }
}
