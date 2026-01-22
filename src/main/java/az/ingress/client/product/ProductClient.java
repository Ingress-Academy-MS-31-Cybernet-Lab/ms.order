package az.ingress.client.product;

import az.ingress.model.client.ProductDto;

public interface ProductClient {

    ProductDto getProductById(String productId);

}
