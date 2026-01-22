package az.ingress.client.mock;

import az.ingress.client.product.ProductClient;
import az.ingress.enums.ShippingType;
import az.ingress.model.client.ProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "client.product.use-mock", havingValue = "true")
public class MockProductAdapter implements ProductClient {

    private static final Map<String, ProductDto> MOCK_DATABASE = new HashMap<>();

    @PostConstruct
    public void init() {
        // --- 1: Eyni Satıcı (Supplier ID: 100) ---

        // iPhone (Standard)
        MOCK_DATABASE.put("P-1001", ProductDto.builder()
                .id("P-1001")
                .name("iPhone 15 Pro - Black")
                .price(new BigDecimal("2400.00"))
                .originalPrice(new BigDecimal("2500.00")) // 100 AZN endirim
                .supplierId(100L)
                .supplierName("Baku Electronics")
                .commissionRate(new BigDecimal("0.05")) // 5% komissiya
                .shippingType(ShippingType.STANDARD)
                .build());

        // iPhoneCase (Standard) - iPhone ilə alanda karqo pulsuz olmalıdır (Bundle)
        MOCK_DATABASE.put("P-1002", ProductDto.builder()
                .id("P-1002")
                .name("iPhone 15 Silicon Case")
                .price(new BigDecimal("50.00"))
                .originalPrice(new BigDecimal("50.00"))
                .supplierId(100L)
                .supplierName("Baku Electronics")
                .commissionRate(new BigDecimal("0.10"))
                .shippingType(ShippingType.STANDARD)
                .build());


        // --- Fərqli Satıcı (Supplier ID: 200) - "Irshad" ---

        // iPhone
        MOCK_DATABASE.put("P-2001", ProductDto.builder()
                .id("P-2001")
                .name("iPhone 15 Pro - Black")
                .price(new BigDecimal("2450.00"))
                .originalPrice(new BigDecimal("2450.00"))
                .supplierId(200L)
                .supplierName("Irshad Electronics")
                .commissionRate(new BigDecimal("0.05"))
                .shippingType(ShippingType.STANDARD)
                .build());


        // --- Ağır Məhsul (Supplier ID: 300) - "Baku Electronics" ---

        // Soyuducu (HEAVY_ITEM)
        // SHIPPING - 30 AZN
        MOCK_DATABASE.put("P-3001", ProductDto.builder()
                .id("P-3001")
                .name("LG Soyuducu No-Frost")
                .price(new BigDecimal("1500.00"))
                .originalPrice(new BigDecimal("1800.00"))
                .supplierId(300L)
                .supplierName("Baku Electronics")
                .commissionRate(new BigDecimal("0.08"))
                .shippingType(ShippingType.HEAVY_ITEM)
                .extraShippingCost(new BigDecimal("30.00"))
                .build());

        log.info("MOCK Product DB Initialized. Keys: {}", MOCK_DATABASE.keySet());
    }

    @Override
    public ProductDto getProductById(String productId) {
        log.info("MOCK REQUEST: Fetching product with ID: {}", productId);

        if (!MOCK_DATABASE.containsKey(productId)) {
            throw new RuntimeException("Product not found! ID does not exist in Mock DB: " + productId);
        }

        return MOCK_DATABASE.get(productId);
    }
}
