package com.pharmacy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line of a customer/supplier account statement.
 * debit increases the balance (customer owes us more / we owe the supplier more), credit decreases it.
 * descriptionKey is a message key; link is an optional URL to the document.
 */
public record StatementLine(LocalDateTime date, String descriptionKey, String refNo, String link,
                            BigDecimal debit, BigDecimal credit, BigDecimal balance) {
}
