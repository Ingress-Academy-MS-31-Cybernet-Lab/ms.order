package az.ingress.model.request;

import lombok.Data;

@Data
public class AddressRequest {
    private String country;
    private String city;
    private String street;
    private String zipCode;
    private String detail;
}
