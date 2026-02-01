package az.ingress.dao.repository;

import az.ingress.dao.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {



}
