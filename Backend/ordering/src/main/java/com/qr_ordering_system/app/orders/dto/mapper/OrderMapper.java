package com.qr_ordering_system.app.orders.dto.mapper;

import com.qr_ordering_system.app.orders.domain.Order;
import com.qr_ordering_system.app.orders.domain.OrderItem;
import com.qr_ordering_system.app.orders.domain.OrderItemModifier;
import com.qr_ordering_system.app.orders.dto.response.OrderItemResponse;
import com.qr_ordering_system.app.orders.dto.response.OrderModifierResponse;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;

import java.util.Comparator;
import java.util.List;

/**
 * Entity -> response mapping for orders. Items are sorted by id so callers
 * see the same line order every time (collection order in memory is not
 * guaranteed), and id IS the timeline: line 1 was chosen before line 2.
 */
public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus() != null ? order.getStatus().name() : null,
                order.getOrderType() != null ? order.getOrderType().name() : null,
                order.getSource() != null ? order.getSource().name() : null,
                order.getTableNumberSnapshot(),
                order.getTableSession() != null ? order.getTableSession().getSessionCode() : null,
                order.getCustomerNote(),
                order.getCustomerPhoneNumber(),
                order.getInvoiceNumber(),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getDiscountAmount(),
                order.getServiceCharge(),
                order.getFinalAmount(),
                order.getCreatedAt(),
                order.getItems() != null
                        ? order.getItems().stream()
                            .sorted(Comparator.comparing(OrderItem::getId))
                            .map(OrderMapper::toItemResponse)
                            .toList()
                        : List.of()
        );
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getItemNameSnapshot(),
                item.getQuantity(),
                item.getPriceAtTimeOfOrder(),
                item.getLineTotal(),
                item.getItemStatus() != null ? item.getItemStatus().name() : null,
                item.getSelectedModifiers() != null
                        ? item.getSelectedModifiers().stream()
                            .sorted(Comparator.comparing(OrderItemModifier::getId))
                            .map(m -> new OrderModifierResponse(
                                    m.getId(),
                                    m.getModifierName(),
                                    m.getModifierPrice()))
                            .toList()
                        : List.of()
        );
    }
}
