package com.pharmacy.dto;

import com.pharmacy.entity.PaymentMethod;
import com.pharmacy.entity.SellUnit;

import java.math.BigDecimal;
import java.util.List;

/**
 * JSON body sent by the POS screen.
 * Discounts: when discountPercent is set (0-100) the server calculates the amount and ignores discount;
 * otherwise discount is a fixed amount.
 */
public record SaleRequest(
        Long customerId,
        List<Line> items,
        BigDecimal discount,
        BigDecimal discountPercent,
        PaymentMethod paymentMethod,
        BigDecimal paid,
        String notes) {

    /** qty is in sellUnit (BOX / STRIP / UNIT, default BOX); unitPrice is per sellUnit (null = public price). */
    public record Line(Long productId, BigDecimal qty, SellUnit sellUnit, BigDecimal unitPrice, BigDecimal discount, BigDecimal discountPercent) {}
}
