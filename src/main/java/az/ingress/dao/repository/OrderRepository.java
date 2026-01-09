package az.ingress.dao.repository;

import az.ingress.dao.entity.Order;
import io.hypersistence.utils.spring.repository.BaseJpaRepository;

import java.util.UUID;

public interface OrderRepository extends BaseJpaRepository<Order, UUID> {



}
