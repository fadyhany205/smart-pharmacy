package com.pharmacy.entity;

/** How the value of a return is settled with the customer / supplier. */
public enum ReturnSettlement {
    /** First cancel what is still owed on the invoice (up to the current account balance), refund the rest in cash. */
    AUTO,
    /** Everything is deducted from the account balance (may leave a credit for the party). */
    BALANCE,
    /** Everything is refunded in cash; the account balance does not change. */
    CASH
}
