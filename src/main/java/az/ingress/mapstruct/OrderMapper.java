package az.ingress.mapstruct;

import az.ingress.dao.entity.Order;
import az.ingress.dao.entity.OrderAddress;
import az.ingress.model.request.AddressRequest;
import az.ingress.model.request.CreateOrderRequest;
import az.ingress.model.response.CreateOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    Order toEntity(CreateOrderRequest order);

    CreateOrderResponse toResponse(Order order);

    OrderAddress toOrderAddress(AddressRequest request);

}
