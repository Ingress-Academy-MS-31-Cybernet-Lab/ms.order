package az.ingress.dao.repository;

import az.ingress.dao.entity.OrderDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, UUID> {
}
