package az.ingress.service.concrete;

import az.ingress.dao.entity.Order;
import az.ingress.dao.entity.OrderItem;
import az.ingress.dao.repository.OrderRepository;
import az.ingress.model.request.CreateOrderRequest;
import az.ingress.model.request.OrderItemRequest;
import az.ingress.model.response.CreateOrderResponse;
import az.ingress.service.abstraction.OrderService;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class OrderServiceHandler implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {


        List<OrderItem> orderItemList = new ArrayList<>();

        for (OrderItemRequest orderItemRequest : createOrderRequest.getItems()) {
            OrderItem orderItem = new OrderItem();

            orderItem.setProductId(orderItemRequest.getProductId());
        }

        Order.builder()
                .userId(createOrderRequest.getUserId());


        return null;
    }


}
