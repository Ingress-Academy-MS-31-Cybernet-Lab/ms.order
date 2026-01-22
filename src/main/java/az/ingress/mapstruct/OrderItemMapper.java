package az.ingress.mapstruct;

import az.ingress.dao.entity.OrderItem;
import az.ingress.model.request.OrderItemRequest;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderItemMapper {

    OrderItem toEntity(OrderItemRequest orderItem);

}
