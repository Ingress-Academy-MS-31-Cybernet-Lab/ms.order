package az.ingress.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String SAGA_EXCHANGE = "saga.exchange";
    public static final String SAGA_ROLLBACK_QUEUE = "q.order.saga.rollback";
    public static final String MOCK_PAYMENT_QUEUE = "q.mock.payment";
    public static final String SAGA_FAIL_ROUTING_KEY = "saga.event.fail";
    public static final String SAGA_SUCCESS_QUEUE = "q.order.saga.success";
    public static final String SAGA_SUCCESS_ROUTING_KEY = "saga.event.success";

    // Audit/Trace Queue (For Debugging/History)
    public static final String AUDIT_QUEUE = "q.order.audit";


    @Bean
    public FanoutExchange orderEventsExchange() {
        return ExchangeBuilder.fanoutExchange(ORDER_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange sagaExchange() {
        return ExchangeBuilder.topicExchange(SAGA_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue sagaRollbackQueue() {
        return QueueBuilder.durable(SAGA_ROLLBACK_QUEUE).build();
    }

    @Bean
    public Queue mockPaymentQueue() {
        return QueueBuilder.durable(MOCK_PAYMENT_QUEUE).build();
    }

    public static final String MOCK_PRODUCT_QUEUE = "q.mock.product";

    @Bean
    public Queue mockProductQueue() {
        return QueueBuilder.durable(MOCK_PRODUCT_QUEUE).build();
    }

    @Bean
    public Binding mockProductBinding() {
        return BindingBuilder.bind(mockProductQueue()).to(orderEventsExchange());
    }

    @Bean
    public Binding mockPaymentBinding() {
        return BindingBuilder.bind(mockPaymentQueue()).to(orderEventsExchange());
    }

    @Bean
    public Binding sagaRollbackBinding() {
        return BindingBuilder.bind(sagaRollbackQueue()).to(sagaExchange()).with(SAGA_FAIL_ROUTING_KEY);
    }

    @Bean
    public Queue sagaSuccessQueue() {
        return QueueBuilder.durable(SAGA_SUCCESS_QUEUE).build();
    }

    @Bean
    public Binding sagaSuccessBinding() {
        return BindingBuilder.bind(sagaSuccessQueue()).to(sagaExchange()).with(SAGA_SUCCESS_ROUTING_KEY);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).build();
    }

    @Bean
    public Binding auditOrderEventsBinding() {
        return BindingBuilder.bind(auditQueue()).to(orderEventsExchange());
    }

    @Bean
    public Binding auditSagaBinding() {
        return BindingBuilder.bind(auditQueue()).to(sagaExchange()).with("#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
