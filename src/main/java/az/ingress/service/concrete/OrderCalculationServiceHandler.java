package az.ingress.service.concrete;

import az.ingress.dao.entity.Order;
import az.ingress.dao.entity.OrderDiscount;
import az.ingress.dao.entity.OrderItem;
import az.ingress.enums.DiscountScope;
import az.ingress.enums.DiscountSource;
import az.ingress.enums.DiscountType;
import az.ingress.enums.ShippingType;
import az.ingress.model.client.ProductDto;
import az.ingress.model.client.PromoDto;
import az.ingress.model.request.OrderItemRequest;
import az.ingress.service.abstraction.OrderCalculationService;
import az.ingress.service.abstraction.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class OrderCalculationServiceHandler implements OrderCalculationService {

    private static final BigDecimal BASE_SHIPPING_FEE = new BigDecimal("5.00");

    public void calculateOrder(Order order, OrderContext context) {
        var orderItems = new ArrayList<OrderItem>();
        var appliedDiscounts = new ArrayList<OrderDiscount>();
        var totalDiscountAmount = BigDecimal.ZERO;

        BigDecimal grossAmount = calculateGrossAmount(context);

        for (OrderItemRequest req : context.getRequests()) {
            ProductDto product = context.getProducts().get(req.getProductId());
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
            BigDecimal currentItemPrice = lineTotal;
            BigDecimal itemTotalDiscount = BigDecimal.ZERO;

            // 1. Item Specific Promo
            PromoDto itemPromo = context.getItemPromos().get(req.getPromoCode());
            if (itemPromo != null) {
                validatePromoApplicability(itemPromo, lineTotal, DiscountScope.PRODUCT);
                if (!product.getId().equals(itemPromo.getTargetProductId())) {
                    throw new RuntimeException("Promo code " + req.getPromoCode() + " is not applicable to product "
                            + product.getId());
                }

                BigDecimal discountValue = calculateDiscountValue(itemPromo, currentItemPrice);
                discountValue = discountValue.min(currentItemPrice);

                currentItemPrice = currentItemPrice.subtract(discountValue);
                itemTotalDiscount = itemTotalDiscount.add(discountValue);

                appliedDiscounts.add(createDiscountEntity(order, itemPromo, discountValue, DiscountSource.SELLER));
            }

            // 2. Global Promo
            PromoDto globalPromo = context.getGlobalPromo();
            if (globalPromo != null && itemTotalDiscount.compareTo(BigDecimal.ZERO) == 0) {
                validatePromoApplicability(globalPromo, grossAmount, DiscountScope.ORDER);

                BigDecimal globalTotalValue = calculateDiscountValue(globalPromo, grossAmount);
                BigDecimal ratio = lineTotal.divide(grossAmount, 4, RoundingMode.HALF_UP);
                BigDecimal share = globalTotalValue.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                share = share.min(currentItemPrice);

                currentItemPrice = currentItemPrice.subtract(share);
                itemTotalDiscount = itemTotalDiscount.add(share);

                appliedDiscounts.add(createDiscountEntity(order, globalPromo, share, DiscountSource.PLATFORM));
            }

            totalDiscountAmount = totalDiscountAmount.add(itemTotalDiscount);
            orderItems.add(buildOrderItem(order, req, product, lineTotal, itemTotalDiscount));
        }

        BigDecimal shippingFee = calculateShippingFee(context);

        order.setItems(orderItems);
        order.setDiscounts(appliedDiscounts);
        order.setGrossAmount(grossAmount);
        order.setTotalDiscount(totalDiscountAmount);
        order.setShippingAmount(shippingFee);
        order.setNetAmount(grossAmount.subtract(totalDiscountAmount).add(shippingFee));
    }

    private BigDecimal calculateGrossAmount(OrderContext context) {
        return context.getRequests().stream()
                .map(req -> {
                    ProductDto product = context.getProducts().get(req.getProductId());
                    return product.getPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateShippingFee(OrderContext context) {
        BigDecimal heavyCost = context.getRequests().stream()
                .map(req -> {
                    ProductDto product = context.getProducts().get(req.getProductId());
                    if (product.getShippingType() == ShippingType.HEAVY_ITEM) {
                        BigDecimal cost = product.getExtraShippingCost() != null ?
                                product.getExtraShippingCost() : BigDecimal.ZERO;
                        return cost.multiply(BigDecimal.valueOf(req.getQuantity()));
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long uniqueSuppliers = context.getRequests().stream()
                .map(req -> context.getProducts().get(req.getProductId()))
                .filter(p -> p.getShippingType() == ShippingType.STANDARD)
                .map(ProductDto::getSupplierId)
                .distinct()
                .count();

        BigDecimal standardCost = BigDecimal.valueOf(uniqueSuppliers).multiply(BASE_SHIPPING_FEE);

        return heavyCost.add(standardCost);
    }

    private BigDecimal calculateDiscountValue(PromoDto promo, BigDecimal baseAmount) {
        if (promo.getType() == DiscountType.PERCENTAGE) {
            return baseAmount.multiply(promo.getValue()).divide(new BigDecimal("100"), 2,
                    RoundingMode.HALF_UP);
        } else {
            return promo.getValue().min(baseAmount);
        }
    }

    private void validatePromoApplicability(PromoDto promo, BigDecimal amount, DiscountScope expectedScope) {
        if (promo.getScope() != expectedScope) {
            throw new RuntimeException("Promo scope mismatch. Expected: " + expectedScope);
        }
        if (amount.compareTo(promo.getMinOrderAmount()) < 0) {
            throw new RuntimeException("Minimum amount requirements not met for promo: " + promo.getCode());
        }
    }

    private OrderItem buildOrderItem(Order order, OrderItemRequest req, ProductDto product, BigDecimal lineTotal,
                                     BigDecimal discountShare) {
        BigDecimal commissionAmount = lineTotal.multiply(product.getCommissionRate());
        BigDecimal payoutAmount = lineTotal.subtract(commissionAmount);

        return OrderItem.builder()
                .order(order)
                .productId(product.getId())
                .supplierId(product.getSupplierId())
                .quantity(req.getQuantity())
                .unitPrice(product.getPrice())
                .discountShare(discountShare)
                .finalPrice(lineTotal.subtract(discountShare))
                .commissionRate(product.getCommissionRate())
                .commissionAmount(commissionAmount)
                .payoutAmount(payoutAmount)
                .shippingType(product.getShippingType().name())
                .build();
    }

    private OrderDiscount createDiscountEntity(Order order, PromoDto promo, BigDecimal amount, DiscountSource source) {
        return OrderDiscount.builder()
                .order(order)
                .promoCode(promo.getCode())
                .discountType(promo.getScope().name())
                .fundedBy(source.name())
                .amount(amount)
                .build();
    }
}
