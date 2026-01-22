package az.ingress.service.abstraction;

import az.ingress.dao.entity.Order;
import az.ingress.service.concrete.OrderContext;

public interface OrderCalculationService {

    public void calculateOrder(Order order, OrderContext context);

}
