package az.ingress.service

import az.ingress.dao.entity.Order
import az.ingress.dao.repository.OrderRepository
import az.ingress.dao.repository.OutboxEventRepository
import az.ingress.enums.OrderStatus
import az.ingress.model.event.SagaSuccessEvent
import az.ingress.service.concrete.OrderSagaHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import spock.lang.Specification

class OrderSagaHandlerTest extends Specification {

    OrderRepository orderRepository = Mock()
    OutboxEventRepository outboxEventRepository = Mock()
    ObjectMapper objectMapper = new ObjectMapper()
    RabbitTemplate rabbitTemplate = Mock()

    OrderSagaHandler service

    def setup() {
        service = new OrderSagaHandler(
                orderRepository,
                outboxEventRepository,
                objectMapper,
                rabbitTemplate
        )
    }

    def "compensateOrder should skip if order already cancelled"() {
        given:
        def orderId = UUID.randomUUID()
        def order = new Order(id: orderId, status: OrderStatus.CANCELLED)

        orderRepository.findById(orderId) >> Optional.of(order)

        when:
        service.compensateOrder(orderId, "Some reason")

        then:
        0 * orderRepository.save(_)
        0 * outboxEventRepository.save(_)
    }

    def "compensateOrder should skip if order not found"() {
        given:
        def orderId = UUID.randomUUID()
        orderRepository.findById(orderId) >> Optional.empty()

        when:
        service.compensateOrder(orderId, "Reason")

        then:
        0 * orderRepository.save(_)
    }

    def "handleSagaSuccess should update status on product success"() {
        given:
        def orderId = UUID.randomUUID()
        def order = new Order(id: orderId, status: OrderStatus.PENDING)
        def event = new SagaSuccessEvent(orderId: orderId, source: "PRODUCT")

        orderRepository.findById(orderId) >> Optional.of(order)

        when:
        service.handleSagaSuccess(event)

        then:
        order.status == OrderStatus.PRODUCT_PROCESSED
        1 * orderRepository.save(order)
    }

    def "handleSagaSuccess should ignore if order cancelled"() {
        given:
        def orderId = UUID.randomUUID()
        def order = new Order(id: orderId, status: OrderStatus.CANCELLED)
        def event = new SagaSuccessEvent(orderId: orderId, source: "PAYMENT")

        orderRepository.findById(orderId) >> Optional.of(order)

        when:
        service.handleSagaSuccess(event)

        then:
        0 * orderRepository.save(_)
    }

    def "handleSagaSuccess should ignore if order already confirmed"() {
        given:
        def orderId = UUID.randomUUID()
        def order = new Order(id: orderId, status: OrderStatus.CONFIRMED)
        def event = new SagaSuccessEvent(orderId: orderId, source: "PAYMENT")

        orderRepository.findById(orderId) >> Optional.of(order)

        when:
        service.handleSagaSuccess(event)

        then:
        0 * orderRepository.save(_)
    }

    def "handleSagaSuccess should set paymentId on payment success"() {
        given:
        def orderId = UUID.randomUUID()
        def paymentId = "PAY-12345"
        def order = new Order(id: orderId, status: OrderStatus.PENDING)
        def event = new SagaSuccessEvent(orderId: orderId, source: "PAYMENT", paymentId: paymentId)

        orderRepository.findById(orderId) >> Optional.of(order)

        when:
        service.handleSagaSuccess(event)

        then:
        order.paymentId == paymentId
        order.status == OrderStatus.PAYMENT_PROCESSED
        1 * orderRepository.save(order)
    }
}
