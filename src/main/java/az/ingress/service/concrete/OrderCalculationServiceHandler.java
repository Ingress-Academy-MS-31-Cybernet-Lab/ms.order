package az.ingress.service.concrete;

import az.ingress.dao.entity.Order;
import az.ingress.dao.entity.OrderDiscount;
import az.ingress.dao.entity.OrderItem;
import az.ingress.enums.DiscountScope;
import az.ingress.enums.DiscountSource;
import az.ingress.enums.DiscountType;
import az.ingress.enums.ShippingType;
import az.ingress.exception.BusinessException;
import az.ingress.exception.ErrorMessage;
import az.ingress.model.client.ProductDto;
import az.ingress.model.client.PromoDto;
import az.ingress.model.request.OrderItemRequest;
import az.ingress.service.abstraction.OrderCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderCalculationServiceHandler implements OrderCalculationService {

    private static final BigDecimal BASE_SHIPPING_FEE = new BigDecimal("5.00");
    private static final BigDecimal PERCENTAGE_DIVISOR = new BigDecimal("100");
    private static final int RATIO_SCALE = 4;
    private static final int AMOUNT_SCALE = 2;

    public void calculateOrder(Order order, OrderContext context) {
        var grossAmount = calculateGrossAmount(context);
        var result = processOrderItems(order, context, grossAmount);
        var shippingFee = calculateShippingFee(context);

        applyToOrder(order, result, grossAmount, shippingFee);
    }

    private ItemProcessingResult processOrderItems(Order order, OrderContext context, BigDecimal grossAmount) {
        var orderItems = new ArrayList<OrderItem>();
        var appliedDiscounts = new ArrayList<OrderDiscount>();
        var totalDiscountAmount = BigDecimal.ZERO;

        for (var req : context.getRequests()) {
            var product = context.getProducts().get(req.getProductId());
            var lineTotal = calculateLineTotal(product, req.getQuantity());

            var itemResult = processItemDiscounts(order, context, req, product, lineTotal, grossAmount);

            orderItems.add(itemResult.orderItem());
            appliedDiscounts.addAll(itemResult.discounts());
            totalDiscountAmount = totalDiscountAmount.add(itemResult.totalDiscount());
        }

        return new ItemProcessingResult(orderItems, appliedDiscounts, totalDiscountAmount);
    }

    private SingleItemResult processItemDiscounts(Order order, OrderContext context, OrderItemRequest req,
                                                   ProductDto product, BigDecimal lineTotal, BigDecimal grossAmount) {
        var currentItemPrice = lineTotal;
        var itemTotalDiscount = BigDecimal.ZERO;
        var discounts = new ArrayList<OrderDiscount>();

        var itemPromoResult = applyItemPromo(order, context, req, product, currentItemPrice);
        if (itemPromoResult != null) {
            currentItemPrice = currentItemPrice.subtract(itemPromoResult.discountValue());
            itemTotalDiscount = itemTotalDiscount.add(itemPromoResult.discountValue());
            discounts.add(itemPromoResult.discount());
        }

        if (itemTotalDiscount.compareTo(BigDecimal.ZERO) == 0) {
            var globalPromoResult = applyGlobalPromo(order, context, lineTotal, grossAmount, currentItemPrice);
            if (globalPromoResult != null) {
                itemTotalDiscount = itemTotalDiscount.add(globalPromoResult.discountValue());
                discounts.add(globalPromoResult.discount());
            }
        }

        var sellerFundedDiscount = calculateSellerFundedDiscount(discounts);
        var orderItem = buildOrderItem(order, req, product, lineTotal, itemTotalDiscount, sellerFundedDiscount);

        discounts.forEach(d -> d.setOrderItem(orderItem));

        return new SingleItemResult(orderItem, discounts, itemTotalDiscount);
    }

    private DiscountResult applyItemPromo(Order order, OrderContext context, OrderItemRequest req,
                                          ProductDto product, BigDecimal currentPrice) {
        var itemPromo = context.getItemPromos().get(req.getPromoCode());
        if (itemPromo == null) {
            return null;
        }

        validatePromoApplicability(itemPromo, currentPrice, DiscountScope.PRODUCT);
        validateProductPromoMatch(itemPromo, product, req.getPromoCode());

        var discountValue = calculateDiscountValue(itemPromo, currentPrice);
        discountValue = discountValue.min(currentPrice);

        var discount = createDiscountEntity(order, itemPromo, discountValue, DiscountSource.SELLER);
        return new DiscountResult(discountValue, discount);
    }

    private DiscountResult applyGlobalPromo(Order order, OrderContext context, BigDecimal lineTotal,
                                            BigDecimal grossAmount, BigDecimal currentPrice) {
        var globalPromo = context.getGlobalPromo();
        if (globalPromo == null) {
            return null;
        }

        validatePromoApplicability(globalPromo, grossAmount, DiscountScope.ORDER);

        var globalTotalValue = calculateDiscountValue(globalPromo, grossAmount);
        var ratio = lineTotal.divide(grossAmount, RATIO_SCALE, RoundingMode.HALF_UP);
        var share = globalTotalValue.multiply(ratio).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        share = share.min(currentPrice);

        var discount = createDiscountEntity(order, globalPromo, share, DiscountSource.PLATFORM);
        return new DiscountResult(share, discount);
    }

    private void validateProductPromoMatch(PromoDto promo, ProductDto product, String promoCode) {
        if (!product.getId().equals(promo.getTargetProductId())) {
            throw new BusinessException(ErrorMessage.PROMO_NOT_APPLICABLE_TO_PRODUCT, promoCode, product.getId());
        }
    }

    private BigDecimal calculateSellerFundedDiscount(List<OrderDiscount> discounts) {
        return discounts.stream()
                .filter(d -> DiscountSource.SELLER.name().equals(d.getDiscountSource()))
                .map(OrderDiscount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void applyToOrder(Order order, ItemProcessingResult result, BigDecimal grossAmount, BigDecimal shippingFee) {
        order.setItems(result.orderItems());
        order.setDiscounts(result.appliedDiscounts());
        order.setGrossAmount(grossAmount);
        order.setTotalDiscount(result.totalDiscountAmount());
        order.setShippingAmount(shippingFee);
        order.setNetAmount(grossAmount.subtract(result.totalDiscountAmount()).add(shippingFee));
    }

    private BigDecimal calculateLineTotal(ProductDto product, int quantity) {
        return product.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal calculateGrossAmount(OrderContext context) {
        return context.getRequests().stream()
                .map(req -> {
                    var product = context.getProducts().get(req.getProductId());
                    return calculateLineTotal(product, req.getQuantity());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateShippingFee(OrderContext context) {
        var heavyCost = calculateHeavyItemShipping(context);
        var standardCost = calculateStandardShipping(context);
        return heavyCost.add(standardCost);
    }

    private BigDecimal calculateHeavyItemShipping(OrderContext context) {
        return context.getRequests().stream()
                .map(req -> calculateItemHeavyShipping(context.getProducts().get(req.getProductId()), req.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateItemHeavyShipping(ProductDto product, int quantity) {
        if (product.getShippingType() != ShippingType.HEAVY_ITEM) {
            return BigDecimal.ZERO;
        }
        var cost = product.getExtraShippingCost() != null ? product.getExtraShippingCost() : BigDecimal.ZERO;
        return cost.multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal calculateStandardShipping(OrderContext context) {
        var uniqueSuppliers = context.getRequests().stream()
                .map(req -> context.getProducts().get(req.getProductId()))
                .filter(p -> p.getShippingType() == ShippingType.STANDARD)
                .map(ProductDto::getSupplierId)
                .distinct()
                .count();

        return BigDecimal.valueOf(uniqueSuppliers).multiply(BASE_SHIPPING_FEE);
    }

    private BigDecimal calculateDiscountValue(PromoDto promo, BigDecimal baseAmount) {
        if (promo.getType() == DiscountType.PERCENTAGE) {
            return baseAmount.multiply(promo.getValue())
                    .divide(PERCENTAGE_DIVISOR, AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return promo.getValue().min(baseAmount);
    }

    private void validatePromoApplicability(PromoDto promo, BigDecimal amount, DiscountScope expectedScope) {
        if (promo.getScope() != expectedScope) {
            throw new BusinessException(ErrorMessage.PROMO_SCOPE_MISMATCH, expectedScope);
        }
        if (amount.compareTo(promo.getMinOrderAmount()) < 0) {
            throw new BusinessException(ErrorMessage.PROMO_MIN_AMOUNT_NOT_MET, promo.getCode());
        }
    }

    private OrderItem buildOrderItem(Order order, OrderItemRequest req, ProductDto product, BigDecimal lineTotal,
                                     BigDecimal discountShare, BigDecimal sellerFundedDiscount) {
        var commissionAmount = lineTotal.multiply(product.getCommissionRate());
        var payoutAmount = lineTotal.subtract(commissionAmount).subtract(sellerFundedDiscount);
        var shippingFee = calculateItemHeavyShipping(product, req.getQuantity());

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
                .shippingFee(shippingFee)
                .build();
    }

    private OrderDiscount createDiscountEntity(Order order, PromoDto promo, BigDecimal amount, DiscountSource source) {
        return OrderDiscount.builder()
                .order(order)
                .promoCode(promo.getCode())
                .discountScope(promo.getScope().name())
                .discountType(promo.getType().name())
                .discountSource(source.name())
                .amount(amount)
                .build();
    }

    private record ItemProcessingResult(List<OrderItem> orderItems, List<OrderDiscount> appliedDiscounts,
                                        BigDecimal totalDiscountAmount) {}

    private record SingleItemResult(OrderItem orderItem, List<OrderDiscount> discounts, BigDecimal totalDiscount) {}

    private record DiscountResult(BigDecimal discountValue, OrderDiscount discount) {}
}
