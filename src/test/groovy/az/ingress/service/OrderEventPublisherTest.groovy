package az.ingress.service

import az.ingress.config.RabbitMQConfig
import az.ingress.model.event.OrderCreatedEvent
import az.ingress.service.concrete.OrderEventPublisher
import org.springframework.amqp.rabbit.core.RabbitTemplate
import spock.lang.Specification

class OrderEventPublisherTest extends Specification {

    RabbitTemplate rabbitTemplate = Mock()
    OrderEventPublisher publisher

    def setup() {
        publisher = new OrderEventPublisher(rabbitTemplate)
    }

    def "should publish event to exchange"() {
        given:
        def event = OrderCreatedEvent.builder()
                .orderId(UUID.randomUUID())
                .userId(1L)
                .orderNumber("ORD-123")
                .build()

        when:
        publisher.handleOrderCreated(event)

        then:
        1 * rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EVENTS_EXCHANGE, "", event)
    }
}
