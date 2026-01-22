package az.ingress.dao.repository;

import az.ingress.dao.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxEventRepository  extends JpaRepository<OutboxEvent, UUID> {
}
