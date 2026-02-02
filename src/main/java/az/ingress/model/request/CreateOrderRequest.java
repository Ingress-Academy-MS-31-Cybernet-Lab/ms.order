package az.ingress.model.request;

import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    private Long userId;
    private List<OrderItemRequest> items;
    private AddressRequest shippingAddress;
    private String promoCode;
}
