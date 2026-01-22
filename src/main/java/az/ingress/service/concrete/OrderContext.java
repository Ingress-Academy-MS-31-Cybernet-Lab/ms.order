package az.ingress.service.concrete;

import az.ingress.model.client.ProductDto;
import az.ingress.model.client.PromoDto;
import az.ingress.model.request.OrderItemRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class OrderContext {
    private final List<OrderItemRequest> requests;
    private final Map<String, ProductDto> products;
    private final PromoDto globalPromo;
    private final Map<String, PromoDto> itemPromos;
}
