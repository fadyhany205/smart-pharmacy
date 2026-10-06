package com.pharmacy.service;

import com.pharmacy.dto.PurchaseRequest;
import com.pharmacy.entity.*;
import com.pharmacy.repository.ProductRepository;
import com.pharmacy.repository.PurchaseRepository;
import com.pharmacy.repository.SupplierRepository;
import com.pharmacy.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class PurchaseService {
    private final PurchaseRepository purchases;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final StockService stock;
    private final SettingsService settings;
    private final CurrentUser currentUser;

    public PurchaseService(PurchaseRepository purchases, ProductRepository products, SupplierRepository suppliers,
                           StockService stock, SettingsService settings, CurrentUser currentUser) {
        this.purchases = purchases;
        this.products = products;
        this.suppliers = suppliers;
        this.stock = stock;
        this.settings = settings;
        this.currentUser = currentUser;
    }

    public Purchase get(Long id) {
        return purchases.findById(id).orElseThrow(() -> new BusinessException("error.notFound"));
    }

    @Transactional
    public Purchase create(PurchaseRequest req) {
        if (req.items() == null || req.items().isEmpty()) throw new BusinessException("error.emptyInvoice");
        if (req.supplierId() == null) throw new BusinessException("error.supplierRequired");
        User user = currentUser.get();

        Purchase purchase = new Purchase();
        purchase.setUser(user);
        purchase.setSupplier(suppliers.findById(req.supplierId()).orElseThrow(() -> new BusinessException("error.notFound")));
        purchase.setSupplierInvoiceNo(req.supplierInvoiceNo());
        purchase.setNotes(req.notes());
        if (req.date() != null && !req.date().equals(LocalDate.now())) {
            purchase.setDate(LocalDateTime.of(req.date(), LocalTime.NOON));
        }

        java.util.Map<PurchaseItem, PurchaseRequest.Line> pending = new java.util.IdentityHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO, taxableLines = BigDecimal.ZERO;
        for (PurchaseRequest.Line line : req.items()) {
            if (line.qty() == null || line.qty().signum() <= 0) throw new BusinessException("error.invalidQty");
            BigDecimal bonus = Money.nz(line.bonusQty());
            if (bonus.signum() < 0) throw new BusinessException("error.invalidQty");
            Product p = products.findById(line.productId()).orElseThrow(() -> new BusinessException("error.notFound"));
            boolean medicine = p.getScientificName() != null && !p.getScientificName().isBlank();
            if (medicine && line.expiryDate() == null) throw new BusinessException("error.expiryRequired", p.getName());
            if (line.expiryDate() != null && line.expiryDate().isBefore(LocalDate.now()))
                throw new BusinessException("error.expiryInPast", p.getName());
            // Whole smallest units only (e.g. 0.5 box of 3 strips is not possible).
            if (line.qty().add(bonus).multiply(BigDecimal.valueOf(p.getFactor())).stripTrailingZeros().scale() > 0)
                throw new BusinessException("error.invalidQty");

            BigDecimal cost = line.unitCost() == null ? p.getCostPrice() : line.unitCost();
            BigDecimal linePct = Money.nz(Money.validPercent(line.discountPercent()));
            BigDecimal gross = Money.r(line.qty().multiply(cost));
            BigDecimal lineTotal = gross.subtract(Money.percent(gross, linePct));

            PurchaseItem item = new PurchaseItem();
            item.setPurchase(purchase);
            item.setProduct(p);
            item.setProductName(p.getName());
            item.setQty(line.qty());
            item.setBonusQty(bonus);
            item.setUnitCost(Money.r(cost));
            item.setDiscountPercent(linePct);
            item.setLineTotal(lineTotal);
            item.setTaxable(p.isTaxable());
            purchase.getItems().add(item);
            subtotal = subtotal.add(lineTotal);
            if (item.isTaxable()) taxableLines = taxableLines.add(lineTotal);
            pending.put(item, line);
        }

        BigDecimal pct = Money.validPercent(req.discountPercent());
        BigDecimal requested = pct != null ? Money.percent(subtotal, pct) : Money.nz(req.discount());
        BigDecimal discount = Money.r(requested.min(subtotal).max(BigDecimal.ZERO));
        purchase.setDiscountPercent(pct);
        BigDecimal ratio = subtotal.signum() == 0 ? BigDecimal.ONE
                : subtotal.subtract(discount).divide(subtotal, 8, RoundingMode.HALF_UP);
        BigDecimal taxRate = settings.effectiveTaxRate();
        BigDecimal taxableNet = Money.r(taxableLines.multiply(ratio));
        BigDecimal tax = Money.percent(taxableNet, taxRate);
        BigDecimal total = Money.r(subtotal.subtract(discount).add(tax));
        BigDecimal paid = Money.r((req.paid() == null ? total : req.paid()).min(total).max(BigDecimal.ZERO));

        purchase.setSubtotal(Money.r(subtotal));
        purchase.setDiscount(discount);
        purchase.setTaxRate(taxRate);
        purchase.setTaxableAmount(taxableNet);
        purchase.setTaxAmount(tax);
        purchase.setTotal(total);
        purchase.setPaid(paid);

        Supplier supplier = purchase.getSupplier();
        supplier.setBalance(supplier.getBalance().add(total.subtract(paid)));

        purchase = purchases.save(purchase);
        purchase.setInvoiceNo(String.format("PUR-%06d", purchase.getId()));
        for (PurchaseItem item : purchase.getItems()) {
            PurchaseRequest.Line line = pending.get(item);
            Product p = item.getProduct();
            // Effective cost per box: what was paid (after line + invoice discount) spread over paid + free boxes.
            BigDecimal boxes = item.getTotalQty();
            BigDecimal effCost = item.getLineTotal().multiply(ratio).divide(boxes, 4, RoundingMode.HALF_UP);
            updateCost(p, boxes, effCost);
            if (line.salePrice() != null && line.salePrice().signum() > 0) p.setSalePrice(Money.r(line.salePrice()));
            BigDecimal baseQty = boxes.multiply(BigDecimal.valueOf(p.getFactor()));
            item.setBatch(stock.receive(p, line.batchNo(), line.expiryDate(), baseQty, effCost,
                    MovementType.PURCHASE, purchase.getInvoiceNo(), null, user));
        }
        return purchase;
    }

    /**
     * Updates the product cost per box according to Settings:
     * LAST_PURCHASE = the new effective cost; WEIGHTED_AVERAGE = (stock boxes × old cost + boxes × new cost) ÷ total boxes.
     * Must be called before the new stock is received.
     */
    private void updateCost(Product p, BigDecimal boxes, BigDecimal newCost) {
        if (settings.get().getCostMethod() == CostMethod.LAST_PURCHASE) {
            p.setCostPrice(Money.r(newCost));
            return;
        }
        BigDecimal oldBoxes = p.getStockBoxes().max(BigDecimal.ZERO);
        BigDecimal totalBoxes = oldBoxes.add(boxes);
        if (oldBoxes.signum() == 0 || totalBoxes.signum() == 0) {
            p.setCostPrice(Money.r(newCost));
        } else {
            BigDecimal value = oldBoxes.multiply(p.getCostPrice()).add(boxes.multiply(newCost));
            p.setCostPrice(value.divide(totalBoxes, 2, RoundingMode.HALF_UP));
        }
    }
}
