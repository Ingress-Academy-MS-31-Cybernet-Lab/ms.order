package az.ingress.dao.repository;

import az.ingress.dao.entity.OutboxEvent;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface OutboxEventRepository  extends CrudRepository<OutboxEvent, UUID> {
}
