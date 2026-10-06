package com.pharmacy.dto;

import com.pharmacy.entity.Party;

import java.math.BigDecimal;

/** Customer / supplier search result for the POS and purchase screens. */
public record PartyLookup(Long id, String name, String phone, BigDecimal balance) {
    public static PartyLookup of(Party p) {
        return new PartyLookup(p.getId(), p.getName(), p.getPhone(), p.getBalance());
    }
}
