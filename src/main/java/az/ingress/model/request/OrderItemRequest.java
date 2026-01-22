package az.ingress.model.request;

import lombok.Data;

@Data
public class OrderItemRequest {
    private String productId;
    private Integer quantity;
    private String promoCode;
}
