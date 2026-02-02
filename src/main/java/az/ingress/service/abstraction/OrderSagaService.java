package az.ingress.service.abstraction;

import java.util.UUID;

public interface OrderSagaService {
    void compensateOrder(UUID orderId, String reason);

    void handleSagaSuccess(az.ingress.model.event.SagaSuccessEvent event);
}
