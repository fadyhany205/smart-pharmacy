package com.pharmacy.controller;

import com.pharmacy.entity.Batch;
import com.pharmacy.entity.MovementType;
import com.pharmacy.entity.Product;
import com.pharmacy.entity.SellUnit;
import com.pharmacy.repository.BatchRepository;
import com.pharmacy.repository.StockMovementRepository;
import com.pharmacy.service.BusinessException;
import com.pharmacy.service.CurrentUser;
import com.pharmacy.service.ProductService;
import com.pharmacy.service.StockService;
import com.pharmacy.util.Fmt;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/inventory")
public class InventoryController {
    private final StockMovementRepository movements;
    private final BatchRepository batches;
    private final ProductService products;
    private final StockService stock;
    private final CurrentUser currentUser;
    private final Fmt fmt;

    public InventoryController(StockMovementRepository movements, BatchRepository batches, ProductService products,
                               StockService stock, CurrentUser currentUser, Fmt fmt) {
        this.movements = movements;
        this.batches = batches;
        this.products = products;
        this.stock = stock;
        this.currentUser = currentUser;
        this.fmt = fmt;
    }

    @GetMapping("/adjust")
    public String adjustForm(@RequestParam(required = false) Long productId,
                             @RequestParam(required = false) Long batchId, Model model) {
        if (productId != null) {
            model.addAttribute("product", products.get(productId));
            model.addAttribute("batches", batches.findByProductIdOrderByExpiryDateAscIdAsc(productId));
        }
        model.addAttribute("batchId", batchId);
        return "inventory/adjust";
    }

    /**
     * Adjusts one batch. batchId = an existing batch, or empty for a new batch (newBatchNo / newExpiry).
     * qty is in qtyUnit (box / strip / unit).
     * mode = "set": the counted quantity becomes the batch stock (stock count);
     * mode = "add": qty is added (negative to remove, e.g. damaged).
     */
    @PostMapping("/adjust")
    @Transactional
    public String adjust(@RequestParam Long productId, @RequestParam(required = false) Long batchId,
                         @RequestParam(required = false) String newBatchNo,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newExpiry,
                         @RequestParam BigDecimal qty, @RequestParam(defaultValue = "BOX") SellUnit qtyUnit,
                         @RequestParam(defaultValue = "add") String mode,
                         @RequestParam(required = false) String note, RedirectAttributes ra) {
        Product p = products.get(productId);
        BigDecimal base = qty.multiply(BigDecimal.valueOf(p.factorOf(qtyUnit)));
        if (base.stripTrailingZeros().scale() > 0) throw new BusinessException("error.invalidQty");
        if (batchId == null) {
            if (base.signum() <= 0) throw new BusinessException("error.invalidQty");
            if (p.getScientificName() != null && newExpiry == null) throw new BusinessException("error.expiryRequired", p.getName());
            stock.receive(p, newBatchNo, newExpiry, base, p.getCostPrice(), MovementType.ADJUSTMENT, null, note, currentUser.get());
        } else {
            Batch b = batches.findById(batchId).filter(x -> x.getProduct().getId().equals(productId))
                    .orElseThrow(() -> new BusinessException("error.notFound"));
            BigDecimal diff = "set".equals(mode) ? base.subtract(b.getQty()) : base;
            if (diff.signum() == 0) throw new BusinessException("error.noChange");
            if (diff.signum() > 0) stock.addToBatch(b, diff, MovementType.ADJUSTMENT, null, note, currentUser.get());
            else stock.removeFromBatch(b, diff.negate(), MovementType.ADJUSTMENT, null, note, currentUser.get());
        }
        ra.addFlashAttribute("success", fmt.msg("inventory.adjusted", p.getName(), fmt.stock(p)));
        return "redirect:/inventory/batches?productId=" + productId;
    }

    /** All batches of one product. */
    @GetMapping("/batches")
    public String batches(@RequestParam Long productId, Model model) {
        model.addAttribute("product", products.get(productId));
        model.addAttribute("batches", batches.findByProductIdOrderByExpiryDateAscIdAsc(productId));
        return "inventory/batches";
    }

    /** Expired and soon-to-expire batches in stock. */
    @GetMapping("/expiry")
    public String expiry(@RequestParam(defaultValue = "90") int days, Model model) {
        int d = Math.max(0, Math.min(days, 730));
        model.addAttribute("batches", batches.findExpiringUntil(LocalDate.now().plusDays(d)));
        model.addAttribute("days", d);
        return "inventory/expiry";
    }

    /** Writes off (إتلاف) the whole remaining quantity of an expired batch. */
    @PostMapping("/batches/{id}/write-off")
    @Transactional
    public String writeOff(@PathVariable Long id, @RequestParam(required = false) String note, RedirectAttributes ra) {
        Batch b = batches.findById(id).orElseThrow(() -> new BusinessException("error.notFound"));
        if (!b.isExpired()) throw new BusinessException("error.batchNotExpired");
        BigDecimal q = b.getQty();
        if (q.signum() <= 0) throw new BusinessException("error.noChange");
        stock.removeFromBatch(b, q, MovementType.EXPIRED, null, StringUtils.hasText(note) ? note : fmt.msg("movement.EXPIRED"), currentUser.get());
        ra.addFlashAttribute("success", fmt.msg("expiry.writtenOff", b.getProduct().getName(), fmt.stock(b.getProduct(), q)));
        return "redirect:/inventory/expiry";
    }

    @GetMapping("/movements")
    public String movements(@RequestParam(required = false) Long productId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "0") int page, Model model) {
        DateRange range = DateRange.of(from, to);
        model.addAttribute("page", movements.search(productId, range.start(), range.end(),
                PageRequest.of(page, 50, Sort.by(Sort.Direction.DESC, "date", "id"))));
        if (productId != null) model.addAttribute("product", products.get(productId));
        model.addAttribute("productId", productId);
        model.addAttribute("range", range);
        return "inventory/movements";
    }
}
