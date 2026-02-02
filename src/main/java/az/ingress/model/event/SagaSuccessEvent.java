package az.ingress.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaSuccessEvent {
    private UUID orderId;
    private String source; // PAYMENT, PRODUCT
    private String paymentId;
}
