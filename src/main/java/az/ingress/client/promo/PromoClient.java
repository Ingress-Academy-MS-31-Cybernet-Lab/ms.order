package az.ingress.client.promo;

import az.ingress.model.client.PromoDto;

public interface PromoClient {

    PromoDto validateAndGetPromo(String code);

}
