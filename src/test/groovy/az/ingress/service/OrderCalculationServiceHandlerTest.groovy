package az.ingress.service

import az.ingress.dao.entity.Order
import az.ingress.dao.entity.OrderDiscount
import az.ingress.dao.entity.OrderItem
import az.ingress.enums.DiscountScope
import az.ingress.enums.DiscountType
import az.ingress.enums.ShippingType
import az.ingress.model.client.ProductDto
import az.ingress.model.client.PromoDto
import az.ingress.model.request.OrderItemRequest
import az.ingress.service.concrete.OrderCalculationServiceHandler
import az.ingress.service.concrete.OrderContext
import spock.lang.Specification

class OrderCalculationServiceHandlerTest extends Specification {

    OrderCalculationServiceHandler service = new OrderCalculationServiceHandler()

    def "should calculate gross amount correctly"() {
        given:
        def order = new Order()
        def context = createContext([
                [productId: "p1", price: 100, quantity: 2],
                [productId: "p2", price: 50, quantity: 1]
        ])

        when:
        service.calculateOrder(order, context)

        then:
        order.grossAmount == BigDecimal.valueOf(250)
    }

    def "should apply item promo correctly"() {
        given:
        def order = new Order()
        def itemPromo = createPromo("ITEM10", DiscountType.PERCENTAGE, DiscountScope.PRODUCT, 10, "p1")
        def context = createContextWithItemPromo([
                [productId: "p1", price: 100, quantity: 1, promoCode: "ITEM10"]
        ], itemPromo)

        when:
        service.calculateOrder(order, context)

        then:
        order.totalDiscount == BigDecimal.valueOf(10).setScale(2)
    }

    def "should apply global promo when no item promo"() {
        given:
        def order = new Order()
        def globalPromo = createPromo("GLOBAL15", DiscountType.PERCENTAGE, DiscountScope.ORDER, 15, null)
        def context = createContextWithGlobalPromo([
                [productId: "p1", price: 200, quantity: 1]
        ], globalPromo)

        when:
        service.calculateOrder(order, context)

        then:
        order.totalDiscount == BigDecimal.valueOf(30).setScale(2)
    }

    def "should not apply global promo when item promo exists"() {
        given:
        def order = new Order()
        def itemPromo = createPromo("ITEM20", DiscountType.FIXED, DiscountScope.PRODUCT, 20, "p1")
        def globalPromo = createPromo("GLOBAL10", DiscountType.PERCENTAGE, DiscountScope.ORDER, 10, null)

        def context = OrderContext.builder()
                .requests([new OrderItemRequest(productId: "p1", quantity: 1, promoCode: "ITEM20")])
                .products(["p1": createProduct("p1", 100)])
                .globalPromo(globalPromo)
                .itemPromos(["ITEM20": itemPromo])
                .build()

        when:
        service.calculateOrder(order, context)

        then:
        order.totalDiscount == BigDecimal.valueOf(20).setScale(2)
    }

    def "should calculate shipping for standard items"() {
        given:
        def order = new Order()
        def context = createContext([
                [productId: "p1", price: 50, quantity: 1, shippingType: ShippingType.STANDARD],
                [productId: "p2", price: 75, quantity: 1, shippingType: ShippingType.STANDARD, supplierId: 2L]
        ])

        when:
        service.calculateOrder(order, context)

        then:
        order.shippingAmount == BigDecimal.valueOf(10.00).setScale(2)
    }

    def "should calculate shipping for heavy items"() {
        given:
        def order = new Order()
        def context = createContext([
                [productId: "p1", price: 500, quantity: 2, shippingType: ShippingType.HEAVY_ITEM, extraShipping: 25]
        ])

        when:
        service.calculateOrder(order, context)

        then:
        order.shippingAmount == BigDecimal.valueOf(50)
    }

    def "should calculate net amount correctly"() {
        given:
        def order = new Order()
        def context = createContext([
                [productId: "p1", price: 100, quantity: 1]
        ])

        when:
        service.calculateOrder(order, context)

        then:
        order.netAmount == order.grossAmount - order.totalDiscount + order.shippingAmount
    }

    private OrderContext createContext(List<Map> items) {
        def products = [:]
        def requests = []

        items.each { item ->
            def productId = item.productId
            def shippingType = item.shippingType ?: ShippingType.STANDARD
            def supplierId = item.supplierId ?: 1L

            products[productId] = ProductDto.builder()
                    .id(productId)
                    .price(BigDecimal.valueOf(item.price as long))
                    .shippingType(shippingType)
                    .supplierId(supplierId)
                    .extraShippingCost(item.extraShipping ? BigDecimal.valueOf(item.extraShipping as long) : null)
                    .commissionRate(BigDecimal.valueOf(0.1))
                    .build()

            requests << new OrderItemRequest(productId: productId, quantity: item.quantity as int)
        }

        OrderContext.builder()
                .requests(requests)
                .products(products)
                .itemPromos([:])
                .build()
    }

    private OrderContext createContextWithItemPromo(List<Map> items, PromoDto promo) {
        def base = createContext(items)
        def requests = items.collect { new OrderItemRequest(productId: it.productId, quantity: it.quantity as int, promoCode: it.promoCode) }

        OrderContext.builder()
                .requests(requests)
                .products(base.products)
                .itemPromos([(promo.code): promo])
                .build()
    }

    private OrderContext createContextWithGlobalPromo(List<Map> items, PromoDto promo) {
        def base = createContext(items)

        OrderContext.builder()
                .requests(base.requests)
                .products(base.products)
                .globalPromo(promo)
                .itemPromos([:])
                .build()
    }

    private ProductDto createProduct(String id, long price) {
        ProductDto.builder()
                .id(id)
                .price(BigDecimal.valueOf(price))
                .shippingType(ShippingType.STANDARD)
                .supplierId(1L)
                .commissionRate(BigDecimal.valueOf(0.1))
                .build()
    }

    private PromoDto createPromo(String code, DiscountType type, DiscountScope scope, long value, String targetProductId) {
        PromoDto.builder()
                .code(code)
                .type(type)
                .scope(scope)
                .value(BigDecimal.valueOf(value))
                .minOrderAmount(BigDecimal.ZERO)
                .targetProductId(targetProductId)
                .build()
    }
}
