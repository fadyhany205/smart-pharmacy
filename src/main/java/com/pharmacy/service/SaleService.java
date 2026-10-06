package com.pharmacy.service;

import com.pharmacy.dto.SaleRequest;
import com.pharmacy.entity.*;
import com.pharmacy.repository.BatchAllocationRepository;
import com.pharmacy.repository.CustomerRepository;
import com.pharmacy.repository.ProductRepository;
import com.pharmacy.repository.SaleRepository;
import com.pharmacy.util.Fmt;
import com.pharmacy.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SaleService {
    private final SaleRepository sales;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final BatchAllocationRepository allocations;
    private final StockService stock;
    private final SettingsService settings;
    private final CurrentUser currentUser;
    private final Fmt fmt;

    public SaleService(SaleRepository sales, ProductRepository products, CustomerRepository customers,
                       BatchAllocationRepository allocations, StockService stock, SettingsService settings,
                       CurrentUser currentUser, Fmt fmt) {
        this.sales = sales;
        this.products = products;
        this.customers = customers;
        this.allocations = allocations;
        this.stock = stock;
        this.settings = settings;
        this.currentUser = currentUser;
        this.fmt = fmt;
    }

    public Sale get(Long id) {
        return sales.findById(id).orElseThrow(() -> new BusinessException("error.notFound"));
    }

    @Transactional
    public Sale create(SaleRequest req) {
        if (req.items() == null || req.items().isEmpty()) throw new BusinessException("error.emptyInvoice");
        User user = currentUser.get();

        Sale sale = new Sale();
        sale.setUser(user);
        sale.setNotes(req.notes());
        sale.setPaymentMethod(req.paymentMethod() == null ? PaymentMethod.CASH : req.paymentMethod());
        if (req.customerId() != null) {
            sale.setCustomer(customers.findById(req.customerId()).orElseThrow(() -> new BusinessException("error.notFound")));
        }

        BigDecimal subtotal = BigDecimal.ZERO, taxableLines = BigDecimal.ZERO;
        for (SaleRequest.Line line : req.items()) {
            if (line.qty() == null || line.qty().signum() <= 0) throw new BusinessException("error.invalidQty");
            Product p = products.findById(line.productId()).orElseThrow(() -> new BusinessException("error.notFound"));
            SellUnit unit = line.sellUnit() == null ? SellUnit.BOX : line.sellUnit();
            if (!p.canSell(unit)) throw new BusinessException("error.sellUnitNotAllowed", p.getName(), fmt.unitName(p, unit));
            int factor = p.factorOf(unit);
            BigDecimal baseQty = line.qty().multiply(BigDecimal.valueOf(factor));
            if (baseQty.stripTrailingZeros().scale() > 0) throw new BusinessException("error.invalidQty");

            BigDecimal price = line.unitPrice() == null ? p.priceOf(unit) : line.unitPrice();
            BigDecimal linePct = Money.validPercent(line.discountPercent());
            BigDecimal gross = Money.r(line.qty().multiply(price));
            BigDecimal discount = linePct != null ? Money.percent(gross, linePct) : Money.nz(line.discount()).max(BigDecimal.ZERO);
            discount = Money.r(discount.min(gross));

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(p);
            item.setProductName(p.getName());
            item.setQty(line.qty());
            item.setSellUnit(unit);
            item.setUnitFactor(factor);
            item.setBaseQty(baseQty);
            item.setUnitPrice(Money.r(price));
            item.setUnitCost(p.costOf(unit));
            item.setDiscountPercent(linePct);
            item.setDiscount(discount);
            item.setLineTotal(gross.subtract(discount));
            item.setTaxable(p.isTaxable());
            sale.getItems().add(item);
            subtotal = subtotal.add(item.getLineTotal());
            if (item.isTaxable()) taxableLines = taxableLines.add(item.getLineTotal());
        }

        BigDecimal pct = Money.validPercent(req.discountPercent());
        BigDecimal requested = pct != null ? Money.percent(subtotal, pct) : Money.nz(req.discount());
        BigDecimal discount = Money.r(requested.min(subtotal).max(BigDecimal.ZERO));
        sale.setDiscountPercent(pct);

        // VAT only on taxable lines (medicines are exempt), after their share of the invoice discount.
        BigDecimal taxRate = settings.effectiveTaxRate();
        BigDecimal taxableNet = subtotal.signum() == 0 ? BigDecimal.ZERO
                : Money.r(taxableLines.multiply(subtotal.subtract(discount)).divide(subtotal, 8, RoundingMode.HALF_UP));
        BigDecimal tax = Money.percent(taxableNet, taxRate);
        BigDecimal total = subtotal.subtract(discount).add(tax);

        sale.setSubtotal(Money.r(subtotal));
        sale.setDiscount(discount);
        sale.setTaxRate(taxRate);
        sale.setTaxableAmount(taxableNet);
        sale.setTaxAmount(tax);
        sale.setTotal(Money.r(total));

        // Amount given by the customer. Cash/card without an amount = paid in full.
        BigDecimal tendered = req.paid() == null
                ? (sale.getPaymentMethod() == PaymentMethod.CREDIT ? BigDecimal.ZERO : total)
                : req.paid();
        BigDecimal paid = tendered.min(total).max(BigDecimal.ZERO);
        sale.setTendered(Money.r(tendered));
        sale.setPaid(Money.r(paid));

        BigDecimal unpaid = total.subtract(paid);
        if (unpaid.signum() > 0) {
            if (sale.getCustomer() == null) throw new BusinessException("error.customerRequiredForCredit");
            sale.getCustomer().setBalance(sale.getCustomer().getBalance().add(unpaid));
            if (sale.getPaymentMethod() != PaymentMethod.CREDIT) sale.setPaymentMethod(PaymentMethod.CREDIT);
        }

        checkStock(sale);
        sale = sales.save(sale);
        sale.setInvoiceNo(String.format("INV-%06d", sale.getId()));

        // FEFO: take from the batches that expire first; remember them for returns.
        for (SaleItem item : sale.getItems()) {
            for (StockService.Allocation a : stock.consumeFefo(item.getProduct(), item.getBaseQty(), MovementType.SALE, sale.getInvoiceNo(), user)) {
                BatchAllocation ba = new BatchAllocation();
                ba.setSaleItem(item);
                ba.setBatch(a.batch());
                ba.setQty(a.qty());
                allocations.save(ba);
            }
        }
        return sale;
    }

    /**
     * Validate before inserting, so a rejected sale does not consume an invoice number (keeps numbering gap-free).
     * Only non-expired stock counts; there is no "sell without stock" in a pharmacy.
     */
    private void checkStock(Sale sale) {
        Map<Product, BigDecimal> needed = new LinkedHashMap<>();
        for (SaleItem i : sale.getItems()) needed.merge(i.getProduct(), i.getBaseQty(), BigDecimal::add);
        needed.forEach((p, qty) -> {
            BigDecimal sellable = stock.sellableQty(p);
            if (qty.compareTo(sellable) > 0)
                throw new BusinessException("error.insufficientStock", p.getName(), fmt.stock(p, sellable));
        });
    }
}
