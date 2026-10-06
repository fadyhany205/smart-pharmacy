package com.pharmacy.service;

import com.pharmacy.dto.StatementLine;
import com.pharmacy.entity.*;
import com.pharmacy.repository.*;
import com.pharmacy.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Customers & suppliers: payments and account statements. */
@Service
public class PartyService {
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final SaleRepository sales;
    private final PurchaseRepository purchases;
    private final ReturnInvoiceRepository returns;
    private final PaymentRepository payments;
    private final CurrentUser currentUser;

    public PartyService(CustomerRepository customers, SupplierRepository suppliers, SaleRepository sales,
                        PurchaseRepository purchases, ReturnInvoiceRepository returns, PaymentRepository payments,
                        CurrentUser currentUser) {
        this.customers = customers;
        this.suppliers = suppliers;
        this.sales = sales;
        this.purchases = purchases;
        this.returns = returns;
        this.payments = payments;
        this.currentUser = currentUser;
    }

    public Party get(PartyType type, Long id) {
        return (type == PartyType.CUSTOMER ? customers.findById(id).map(c -> (Party) c) : suppliers.findById(id).map(s -> (Party) s))
                .orElseThrow(() -> new BusinessException("error.notFound"));
    }

    @Transactional
    public Party save(PartyType type, Party form) {
        Party p;
        if (form.getId() == null) {
            p = type == PartyType.CUSTOMER ? new Customer() : new Supplier();
            p.setBalance(Money.r(form.getBalance()));   // opening balance
        } else {
            p = get(type, form.getId());
        }
        p.setName(form.getName());
        p.setPhone(form.getPhone());
        p.setAddress(form.getAddress());
        p.setTaxNumber(form.getTaxNumber());
        p.setNotes(form.getNotes());
        return type == PartyType.CUSTOMER ? customers.save((Customer) p) : suppliers.save((Supplier) p);
    }

    @Transactional
    public void delete(PartyType type, Long id) {
        try {
            if (type == PartyType.CUSTOMER) {
                customers.deleteById(id);
                customers.flush();
            } else {
                suppliers.deleteById(id);
                suppliers.flush();
            }
        } catch (Exception e) {
            throw new BusinessException("error.partyInUse");
        }
    }

    /**
     * refund = false: money received from a customer / paid to a supplier (balance decreases).
     * refund = true:  cash paid out to a customer / received back from a supplier (balance increases);
     *                 only allowed up to the credit the party has with us (negative balance).
     */
    @Transactional
    public Payment addPayment(PartyType type, Long partyId, BigDecimal amount, PaymentMethod method, String note, boolean refund) {
        if (amount == null || amount.signum() <= 0) throw new BusinessException("error.invalidAmount");
        Party party = get(type, partyId);
        BigDecimal credit = party.getBalance().negate().max(BigDecimal.ZERO);
        if (refund && Money.r(amount).compareTo(credit) > 0) {
            throw new BusinessException(type == PartyType.CUSTOMER ? "error.refundMoreThanCredit" : "error.refundMoreThanSupplierCredit",
                    credit.setScale(2).toPlainString());
        }
        Payment pay = new Payment();
        pay.setRefund(refund);
        pay.setPartyType(type);
        pay.setAmount(Money.r(amount));
        pay.setMethod(method == null || method == PaymentMethod.CREDIT ? PaymentMethod.CASH : method);
        pay.setNote(note);
        pay.setUser(currentUser.get());
        if (type == PartyType.CUSTOMER) pay.setCustomer((Customer) party);
        else pay.setSupplier((Supplier) party);
        party.setBalance(refund ? party.getBalance().add(pay.getAmount()) : party.getBalance().subtract(pay.getAmount()));
        return payments.save(pay);
    }

    /** Full account statement; the first line is the opening balance so the running total ends at the current balance. */
    public List<StatementLine> statement(PartyType type, Long id) {
        Party party = get(type, id);
        record Entry(LocalDateTime date, String key, String ref, String link, BigDecimal debit, BigDecimal credit) {}
        List<Entry> entries = new ArrayList<>();

        if (type == PartyType.CUSTOMER) {
            for (Sale s : sales.findByCustomerIdOrderByDateAsc(id)) {
                entries.add(new Entry(s.getDate(), "statement.sale", s.getInvoiceNo(), "/sales/" + s.getId(), s.getTotal(), BigDecimal.ZERO));
                if (s.getPaid().signum() > 0)
                    entries.add(new Entry(s.getDate(), "statement.paidOnInvoice", s.getInvoiceNo(), "/sales/" + s.getId(), BigDecimal.ZERO, s.getPaid()));
            }
            for (ReturnInvoice r : returns.findBySaleCustomerIdOrderByDateAsc(id)) {
                entries.add(new Entry(r.getDate(), "statement.return", r.getReturnNo(), "/returns/" + r.getId(), BigDecimal.ZERO, r.getTotal()));
                if (r.getCashAmount().signum() > 0)
                    entries.add(new Entry(r.getDate(), "statement.cashRefund", r.getReturnNo(), "/returns/" + r.getId(), r.getCashAmount(), BigDecimal.ZERO));
            }
            for (Payment p : payments.findByCustomerIdOrderByDateAsc(id)) {
                if (p.isRefund())
                    entries.add(new Entry(p.getDate(), "statement.cashPaidToCustomer", p.getNote(), null, p.getAmount(), BigDecimal.ZERO));
                else
                    entries.add(new Entry(p.getDate(), "statement.paymentReceived", p.getNote(), null, BigDecimal.ZERO, p.getAmount()));
            }
        } else {
            for (Purchase pu : purchases.findBySupplierIdOrderByDateAsc(id)) {
                entries.add(new Entry(pu.getDate(), "statement.purchase", pu.getInvoiceNo(), "/purchases/" + pu.getId(), pu.getTotal(), BigDecimal.ZERO));
                if (pu.getPaid().signum() > 0)
                    entries.add(new Entry(pu.getDate(), "statement.paidOnInvoice", pu.getInvoiceNo(), "/purchases/" + pu.getId(), BigDecimal.ZERO, pu.getPaid()));
            }
            for (ReturnInvoice r : returns.findByPurchaseSupplierIdOrderByDateAsc(id)) {
                entries.add(new Entry(r.getDate(), "statement.return", r.getReturnNo(), "/returns/" + r.getId(), BigDecimal.ZERO, r.getTotal()));
                if (r.getCashAmount().signum() > 0)
                    entries.add(new Entry(r.getDate(), "statement.cashRefundFromSupplier", r.getReturnNo(), "/returns/" + r.getId(), r.getCashAmount(), BigDecimal.ZERO));
            }
            for (Payment p : payments.findBySupplierIdOrderByDateAsc(id)) {
                if (p.isRefund())
                    entries.add(new Entry(p.getDate(), "statement.cashFromSupplier", p.getNote(), null, p.getAmount(), BigDecimal.ZERO));
                else
                    entries.add(new Entry(p.getDate(), "statement.paymentMade", p.getNote(), null, BigDecimal.ZERO, p.getAmount()));
            }
        }
        entries.sort(Comparator.comparing(Entry::date));

        BigDecimal movement = entries.stream().map(e -> e.debit().subtract(e.credit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal running = party.getBalance().subtract(movement);

        List<StatementLine> lines = new ArrayList<>();
        lines.add(new StatementLine(null, "statement.opening", null, null, null, null, running));
        for (Entry e : entries) {
            running = running.add(e.debit()).subtract(e.credit());
            lines.add(new StatementLine(e.date(), e.key(), e.ref(), e.link(), e.debit(), e.credit(), running));
        }
        return lines;
    }
}
