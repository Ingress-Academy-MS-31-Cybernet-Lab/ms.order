package az.ingress.service.abstraction;

import az.ingress.model.request.CreateOrderRequest;
import az.ingress.model.response.CreateOrderResponse;

public interface OrderService {

    CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest);

}
