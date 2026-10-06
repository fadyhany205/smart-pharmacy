package com.pharmacy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** JSON body sent by the purchase invoice screen. */
public record PurchaseRequest(
        Long supplierId,
        String supplierInvoiceNo,
        LocalDate date,
        List<Line> items,
        BigDecimal discount,
        /** When set (0-100) the invoice discount is this percentage of the subtotal; discount is then ignored. */
        BigDecimal discountPercent,
        BigDecimal paid,
        String notes) {

    /**
     * Quantities in boxes. bonusQty = free boxes (بونص), discountPercent = supplier discount on the line.
     * batchNo / expiryDate identify the lot (expiry is required for medicines).
     * salePrice is optional: when given, the product public price per box is updated.
     */
    public record Line(Long productId, BigDecimal qty, BigDecimal bonusQty, BigDecimal unitCost, BigDecimal discountPercent,
                       String batchNo, LocalDate expiryDate, BigDecimal salePrice) {}
}
