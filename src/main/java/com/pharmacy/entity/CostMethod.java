package com.pharmacy.entity;

/** How a purchase updates the product cost price. */
public enum CostMethod {
    /** (stock × old cost + qty × new cost) ÷ (stock + qty) */
    WEIGHTED_AVERAGE,
    /** The cost becomes the unit cost of the latest purchase invoice. */
    LAST_PURCHASE
}
