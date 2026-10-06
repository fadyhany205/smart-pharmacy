package com.pharmacy.service;

import com.pharmacy.entity.*;
import com.pharmacy.repository.BatchAllocationRepository;
import com.pharmacy.repository.ReturnInvoiceRepository;
import com.pharmacy.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class ReturnService {
    private final ReturnInvoiceRepository returns;
    private final SaleService saleService;
    private final PurchaseService purchaseService;
    private final StockService stock;
    private final CurrentUser currentUser;
    private final BatchAllocationRepository allocations;

    public ReturnService(ReturnInvoiceRepository returns, SaleService saleService, PurchaseService purchaseService,
                         StockService stock, CurrentUser currentUser,
                         BatchAllocationRepository allocations) {
        this.returns = returns;
        this.saleService = saleService;
        this.purchaseService = purchaseService;
        this.stock = stock;
        this.currentUser = currentUser;
        this.allocations = allocations;
    }

    public ReturnInvoice get(Long id) {
        return returns.findById(id).orElseThrow(() -> new BusinessException("error.notFound"));
    }

    /** What is still owed on a sale invoice: total - paid - parts of earlier returns already deducted from the balance. */
    public BigDecimal outstanding(Sale sale) {
        BigDecimal prev = returns.findBySaleId(sale.getId()).stream()
                .map(ReturnInvoice::getBalanceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sale.getTotal().subtract(sale.getPaid()).subtract(prev).max(BigDecimal.ZERO);
    }

    /** What we still owe the supplier on a purchase invoice. */
    public BigDecimal outstanding(Purchase purchase) {
        BigDecimal prev = returns.findByPurchaseId(purchase.getId()).stream()
                .map(ReturnInvoice::getBalanceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return purchase.getTotal().subtract(purchase.getPaid()).subtract(prev).max(BigDecimal.ZERO);
    }

    /**
     * Splits a return total into the part deducted from the account balance; the rest is cash.
     * AUTO: cancel what is still owed on the invoice, but never more than the party currently owes
     * (payments made later are respected), and refund the rest in cash.
     */
    private BigDecimal balancePart(ReturnSettlement mode, BigDecimal total, Party party, BigDecimal invoiceOutstanding) {
        if (party == null || mode == ReturnSettlement.CASH) return BigDecimal.ZERO;
        if (mode == ReturnSettlement.BALANCE) return total;
        return total.min(invoiceOutstanding).min(party.getBalance().max(BigDecimal.ZERO)).max(BigDecimal.ZERO);
    }

    /** Returns items of a sale. qtyByItemId maps SaleItem id to the quantity returned. */
    @Transactional
    public ReturnInvoice returnSale(Long saleId, Map<Long, BigDecimal> qtyByItemId, ReturnSettlement mode, String reason) {
        Sale sale = saleService.get(saleId);
        if (mode == null) mode = ReturnSettlement.AUTO;
        if (mode == ReturnSettlement.BALANCE && sale.getCustomer() == null) throw new BusinessException("error.customerRequiredForCredit");
        BigDecimal invoiceOutstanding = outstanding(sale);
        User user = currentUser.get();

        ReturnInvoice r = new ReturnInvoice();
        r.setType(PartyType.CUSTOMER);
        r.setSale(sale);
        r.setUser(user);
        r.setReason(reason);

        // Spread the invoice discount over the lines so refunds match what the customer paid.
        BigDecimal ratio = sale.getSubtotal().signum() == 0 ? BigDecimal.ONE
                : sale.getSubtotal().subtract(sale.getDiscount()).divide(sale.getSubtotal(), 8, RoundingMode.HALF_UP);

        java.util.Map<ReturnItem, SaleItem> source = new java.util.IdentityHashMap<>();
        BigDecimal net = BigDecimal.ZERO, cost = BigDecimal.ZERO, taxableNet = BigDecimal.ZERO;
        for (SaleItem item : sale.getItems()) {
            BigDecimal qty = Money.nz(qtyByItemId.get(item.getId()));
            if (qty.signum() <= 0) continue;
            if (qty.compareTo(item.getReturnableQty()) > 0) throw new BusinessException("error.returnQtyTooHigh", item.getProductName());
            BigDecimal baseQty = qty.multiply(BigDecimal.valueOf(item.getUnitFactor()));
            if (baseQty.stripTrailingZeros().scale() > 0) throw new BusinessException("error.invalidQty");
            BigDecimal unitNet = item.getLineTotal().divide(item.getQty(), 8, RoundingMode.HALF_UP).multiply(ratio);
            ReturnItem ri = new ReturnItem();
            ri.setReturnInvoice(r);
            ri.setProduct(item.getProduct());
            ri.setProductName(item.getProductName());
            ri.setQty(qty);
            ri.setSellUnit(item.getSellUnit());
            ri.setBaseQty(baseQty);
            ri.setUnitPrice(Money.r(unitNet));
            ri.setUnitCost(item.getUnitCost());
            ri.setLineTotal(Money.r(unitNet.multiply(qty)));
            ri.setTaxable(item.isTaxable());
            r.getItems().add(ri);
            source.put(ri, item);
            item.setReturnedQty(item.getReturnedQty().add(qty));
            net = net.add(ri.getLineTotal());
            cost = cost.add(item.getUnitCost().multiply(qty));
            if (ri.isTaxable()) taxableNet = taxableNet.add(ri.getLineTotal());
        }
        if (r.getItems().isEmpty()) throw new BusinessException("error.emptyReturn");

        BigDecimal tax = Money.percent(taxableNet, sale.getTaxRate());
        r.setTaxableAmount(Money.r(taxableNet));
        r.setTaxAmount(tax);
        r.setTotal(Money.r(net.add(tax)));
        r.setCostTotal(Money.r(cost));

        sale.setReturnedAmount(sale.getReturnedAmount().add(r.getTotal()));
        sale.setStatus(sale.getItems().stream().allMatch(i -> i.getReturnableQty().signum() <= 0)
                ? InvoiceStatus.RETURNED : InvoiceStatus.PARTIAL_RETURN);
        BigDecimal toBalance = balancePart(mode, r.getTotal(), sale.getCustomer(), invoiceOutstanding);
        r.setBalanceAmount(toBalance);
        r.setCashAmount(r.getTotal().subtract(toBalance));
        r.setToBalance(toBalance.signum() > 0);
        if (toBalance.signum() > 0) {
            Customer c = sale.getCustomer();
            c.setBalance(c.getBalance().subtract(toBalance));
        }

        r = returns.save(r);
        r.setReturnNo(String.format("SR-%06d", r.getId()));
        // Back into the same batches the sale was taken from.
        for (ReturnItem ri : r.getItems()) {
            BigDecimal left = ri.getBaseQty();
            for (BatchAllocation a : allocations.findBySaleItemIdOrderByIdAsc(source.get(ri).getId())) {
                if (left.signum() <= 0) break;
                BigDecimal q = left.min(a.getQty().subtract(a.getReturnedQty()));
                if (q.signum() <= 0) continue;
                a.setReturnedQty(a.getReturnedQty().add(q));
                stock.addToBatch(a.getBatch(), q, MovementType.SALE_RETURN, r.getReturnNo(), sale.getInvoiceNo(), user);
                left = left.subtract(q);
            }
            if (left.signum() > 0) throw new BusinessException("error.returnQtyTooHigh", ri.getProductName());
        }
        return r;
    }

    /** Returns items of a purchase back to the supplier. qtyByItemId maps PurchaseItem id to quantity. */
    @Transactional
    public ReturnInvoice returnPurchase(Long purchaseId, Map<Long, BigDecimal> qtyByItemId, ReturnSettlement mode, String reason) {
        Purchase purchase = purchaseService.get(purchaseId);
        if (mode == null) mode = ReturnSettlement.AUTO;
        BigDecimal invoiceOutstanding = outstanding(purchase);
        User user = currentUser.get();

        ReturnInvoice r = new ReturnInvoice();
        r.setType(PartyType.SUPPLIER);
        r.setPurchase(purchase);
        r.setUser(user);
        r.setReason(reason);

        BigDecimal ratio = purchase.getSubtotal().signum() == 0 ? BigDecimal.ONE
                : purchase.getSubtotal().subtract(purchase.getDiscount()).divide(purchase.getSubtotal(), 8, RoundingMode.HALF_UP);

        java.util.Map<ReturnItem, PurchaseItem> source = new java.util.IdentityHashMap<>();
        BigDecimal net = BigDecimal.ZERO, taxableNet = BigDecimal.ZERO;
        for (PurchaseItem item : purchase.getItems()) {
            BigDecimal qty = Money.nz(qtyByItemId.get(item.getId()));
            if (qty.signum() <= 0) continue;
            if (qty.compareTo(item.getReturnableQty()) > 0) throw new BusinessException("error.returnQtyTooHigh", item.getProductName());
            BigDecimal baseQty = qty.multiply(BigDecimal.valueOf(item.getProduct().getFactor()));
            if (baseQty.stripTrailingZeros().scale() > 0) throw new BusinessException("error.invalidQty");
            // Effective cost per box: paid amount (after line + invoice discount) spread over paid + bonus boxes.
            BigDecimal unitNet = item.getLineTotal().multiply(ratio).divide(item.getTotalQty(), 8, RoundingMode.HALF_UP);
            ReturnItem ri = new ReturnItem();
            ri.setReturnInvoice(r);
            ri.setProduct(item.getProduct());
            ri.setProductName(item.getProductName());
            ri.setQty(qty);
            ri.setSellUnit(SellUnit.BOX);
            ri.setBaseQty(baseQty);
            ri.setUnitPrice(Money.r(unitNet));
            ri.setUnitCost(unitNet.setScale(4, RoundingMode.HALF_UP));
            ri.setLineTotal(Money.r(unitNet.multiply(qty)));
            ri.setTaxable(item.isTaxable());
            r.getItems().add(ri);
            source.put(ri, item);
            item.setReturnedQty(item.getReturnedQty().add(qty));
            net = net.add(ri.getLineTotal());
            if (ri.isTaxable()) taxableNet = taxableNet.add(ri.getLineTotal());
        }
        if (r.getItems().isEmpty()) throw new BusinessException("error.emptyReturn");

        BigDecimal tax = Money.percent(taxableNet, purchase.getTaxRate());
        r.setTaxableAmount(Money.r(taxableNet));
        r.setTaxAmount(tax);
        r.setTotal(Money.r(net.add(tax)));
        r.setCostTotal(Money.r(net));

        purchase.setReturnedAmount(purchase.getReturnedAmount().add(r.getTotal()));
        purchase.setStatus(purchase.getItems().stream().allMatch(i -> i.getReturnableQty().signum() <= 0)
                ? InvoiceStatus.RETURNED : InvoiceStatus.PARTIAL_RETURN);
        BigDecimal toBalance = balancePart(mode, r.getTotal(), purchase.getSupplier(), invoiceOutstanding);
        r.setBalanceAmount(toBalance);
        r.setCashAmount(r.getTotal().subtract(toBalance));
        r.setToBalance(toBalance.signum() > 0);
        if (toBalance.signum() > 0) {
            Supplier s = purchase.getSupplier();
            s.setBalance(s.getBalance().subtract(toBalance));
        }

        r = returns.save(r);
        r.setReturnNo(String.format("PR-%06d", r.getId()));
        // Out of the batch that this purchase line created.
        for (ReturnItem ri : r.getItems()) {
            Batch batch = source.get(ri).getBatch();
            if (batch == null) throw new BusinessException("error.notFound");
            stock.removeFromBatch(batch, ri.getBaseQty(), MovementType.PURCHASE_RETURN, r.getReturnNo(), purchase.getInvoiceNo(), user);
        }
        return r;
    }
}
