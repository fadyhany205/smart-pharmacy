package com.pharmacy.service;

import com.pharmacy.entity.*;
import com.pharmacy.repository.BatchRepository;
import com.pharmacy.repository.ProductRepository;
import com.pharmacy.repository.StockMovementRepository;
import com.pharmacy.util.Fmt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The only place where stock changes. All stock lives in batches (quantities in the smallest unit);
 * product.stockQty is kept equal to the sum of its batches. Every change is logged as a StockMovement.
 */
@Service
public class StockService {
    private final ProductRepository products;
    private final BatchRepository batches;
    private final StockMovementRepository movements;
    private final Fmt fmt;

    public StockService(ProductRepository products, BatchRepository batches, StockMovementRepository movements, Fmt fmt) {
        this.products = products;
        this.batches = batches;
        this.movements = movements;
        this.fmt = fmt;
    }

    /** A quantity taken from one batch. */
    public record Allocation(Batch batch, BigDecimal qty) {}

    /**
     * Stock in: adds to the batch with the same number and expiry, or creates it.
     * baseQty is in the smallest unit; costPerBox is the lot's effective cost.
     */
    @Transactional
    public Batch receive(Product product, String batchNo, LocalDate expiry, BigDecimal baseQty, BigDecimal costPerBox,
                         MovementType type, String refNo, String note, User user) {
        if (baseQty.signum() <= 0) throw new BusinessException("error.invalidQty");
        String no = batchNo == null || batchNo.isBlank() ? null : batchNo.trim();
        Batch b = product.getId() == null ? null
                : batches.findFirstByProductIdAndBatchNoAndExpiryDate(product.getId(), no, expiry).orElse(null);
        if (b == null) {
            b = new Batch();
            b.setProduct(product);
            b.setBatchNo(no);
            b.setExpiryDate(expiry);
            b.setCostPerBox(costPerBox == null ? product.getCostPrice() : costPerBox);
        }
        b.setQty(b.getQty().add(baseQty));
        b = batches.save(b);
        log(product, b, baseQty, type, refNo, note, user);
        return b;
    }

    /**
     * Stock out by FEFO (first expiry, first out) from batches that are not expired.
     * Expired stock can never be sold.
     */
    @Transactional
    public List<Allocation> consumeFefo(Product product, BigDecimal baseQty, MovementType type, String refNo, User user) {
        if (baseQty.signum() <= 0) throw new BusinessException("error.invalidQty");
        List<Batch> sellable = batches.findSellableForUpdate(product.getId(), LocalDate.now());
        BigDecimal available = sellable.stream().map(Batch::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(baseQty) < 0) {
            throw new BusinessException("error.insufficientStock", product.getName(), fmt.stock(product, available));
        }
        List<Allocation> taken = new ArrayList<>();
        BigDecimal left = baseQty;
        for (Batch b : sellable) {
            if (left.signum() <= 0) break;
            BigDecimal q = left.min(b.getQty());
            b.setQty(b.getQty().subtract(q));
            batches.save(b);
            log(product, b, q.negate(), type, refNo, null, user);
            taken.add(new Allocation(b, q));
            left = left.subtract(q);
        }
        return taken;
    }

    /** Puts stock back into a specific batch (sales return). */
    @Transactional
    public void addToBatch(Batch batch, BigDecimal baseQty, MovementType type, String refNo, String note, User user) {
        if (baseQty.signum() <= 0) return;
        batch.setQty(batch.getQty().add(baseQty));
        batches.save(batch);
        log(batch.getProduct(), batch, baseQty, type, refNo, note, user);
    }

    /** Takes stock out of a specific batch (purchase return, expired write-off, adjustment). */
    @Transactional
    public void removeFromBatch(Batch batch, BigDecimal baseQty, MovementType type, String refNo, String note, User user) {
        if (baseQty.signum() <= 0) return;
        if (batch.getQty().compareTo(baseQty) < 0) {
            throw new BusinessException("error.insufficientBatchStock", batch.getBatchNo() == null ? "-" : batch.getBatchNo(),
                    fmt.stock(batch.getProduct(), batch.getQty()));
        }
        batch.setQty(batch.getQty().subtract(baseQty));
        batches.save(batch);
        log(batch.getProduct(), batch, baseQty.negate(), type, refNo, note, user);
    }

    /** Sellable (not expired) stock in the smallest unit. */
    public BigDecimal sellableQty(Product product) {
        return batches.sellableQty(product.getId(), LocalDate.now());
    }

    private void log(Product product, Batch batch, BigDecimal signedQty, MovementType type, String refNo, String note, User user) {
        BigDecimal newQty = product.getStockQty().add(signedQty);
        product.setStockQty(newQty);
        products.save(product);

        StockMovement m = new StockMovement();
        m.setProduct(product);
        m.setBatch(batch);
        m.setType(type);
        m.setQty(signedQty);
        m.setBalanceAfter(newQty);
        m.setRefNo(refNo);
        m.setNote(note);
        m.setUser(user);
        movements.save(m);
    }
}
