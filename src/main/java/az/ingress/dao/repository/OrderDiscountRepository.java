package az.ingress.dao.repository;

import az.ingress.dao.entity.OrderDiscount;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface OrderDiscountRepository extends CrudRepository<OrderDiscount, UUID> {

}
