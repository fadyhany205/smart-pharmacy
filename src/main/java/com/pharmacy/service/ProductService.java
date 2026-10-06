package com.pharmacy.service;

import com.pharmacy.entity.Category;
import com.pharmacy.entity.MovementType;
import com.pharmacy.entity.Product;
import com.pharmacy.entity.Unit;
import com.pharmacy.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Service
public class ProductService {
    private final ProductRepository products;
    private final BarcodeService barcodes;
    private final StockService stock;
    private final FileStorageService files;
    private final CurrentUser currentUser;
    private final EntityManager em;

    public ProductService(ProductRepository products, BarcodeService barcodes, StockService stock,
                          FileStorageService files, CurrentUser currentUser, EntityManager em) {
        this.products = products;
        this.barcodes = barcodes;
        this.stock = stock;
        this.files = files;
        this.currentUser = currentUser;
        this.em = em;
    }

    public Product get(Long id) {
        return products.findById(id).orElseThrow(() -> new BusinessException("error.notFound"));
    }

    /**
     * Creates or updates a product. A new product may carry an opening stock quantity;
     * afterwards stock only changes through purchases, sales, returns and adjustments.
     */
    @Transactional
    public Product save(Product form, BigDecimal openingQty, MultipartFile image) {
        return save(form, openingQty, null, null, image);
    }

    /** openingQty is in boxes and becomes the first batch (openingBatch / openingExpiry). */
    @Transactional
    public Product save(Product form, BigDecimal openingQty, String openingBatch, java.time.LocalDate openingExpiry,
                        MultipartFile image) {
        int strips = form.getStripsPerBox() == null || form.getStripsPerBox() < 1 ? 1 : form.getStripsPerBox();
        int units = form.getUnitsPerStrip() == null || form.getUnitsPerStrip() < 1 ? 1 : form.getUnitsPerStrip();
        String barcode = form.getBarcode() == null ? null : form.getBarcode().trim();
        form.setBarcode(barcode == null || barcode.isEmpty() ? null : barcode);
        if (form.getBarcode() != null) {
            boolean duplicate = form.getId() == null
                    ? products.existsByBarcode(form.getBarcode())
                    : products.existsByBarcodeAndIdNot(form.getBarcode(), form.getId());
            if (duplicate) throw new BusinessException("error.duplicateBarcode", form.getBarcode());
        }

        Product p;
        if (form.getId() == null) {
            p = new Product();
        } else {
            p = get(form.getId());
        }
        p.setBarcode(form.getBarcode());
        p.setSku(form.getSku());
        p.setNameAr(form.getNameAr());
        p.setNameEn(form.getNameEn());
        p.setCategory(form.getCategory() != null && form.getCategory().getId() != null
                ? em.getReference(Category.class, form.getCategory().getId()) : null);
        p.setUnit(form.getUnit() != null && form.getUnit().getId() != null
                ? em.getReference(Unit.class, form.getUnit().getId()) : null);
        p.setCostPrice(form.getCostPrice() == null ? BigDecimal.ZERO : form.getCostPrice());
        p.setSalePrice(form.getSalePrice() == null ? BigDecimal.ZERO : form.getSalePrice());
        p.setMinStock(form.getMinStock() == null ? BigDecimal.ZERO : form.getMinStock());
        p.setActive(form.isActive());
        p.setScientificName(blankToNull(form.getScientificName()));
        p.setStrength(blankToNull(form.getStrength()));
        p.setDosageForm(blankToNull(form.getDosageForm()));
        p.setManufacturer(blankToNull(form.getManufacturer()));
        p.setShelfLocation(blankToNull(form.getShelfLocation()));
        p.setNeedsFridge(form.isNeedsFridge());
        p.setPrescriptionRequired(form.isPrescriptionRequired());
        p.setTaxable(form.isTaxable());
        // Stock is stored in the smallest unit: changing the pack structure would change what the stock means.
        boolean packChanged = p.getId() != null && (p.getStripsPerBox() != strips || p.getUnitsPerStrip() != units);
        if (packChanged && p.getStockQty().signum() != 0) throw new BusinessException("error.packChangeWithStock");
        p.setStripsPerBox(strips);
        p.setUnitsPerStrip(units);

        String imagePath = files.storeImage(image, "products");
        if (imagePath != null) p.setImagePath(imagePath);

        boolean isNew = p.getId() == null;
        p = products.save(p);

        if (p.getBarcode() == null) {
            p.setBarcode(uniqueGeneratedBarcode(p.getId()));
            p = products.save(p);
        }
        if (isNew && openingQty != null && openingQty.signum() > 0) {
            if (p.getScientificName() != null && openingExpiry == null) throw new BusinessException("error.expiryRequired", p.getName());
            BigDecimal base = openingQty.multiply(BigDecimal.valueOf(p.getFactor()));
            if (base.stripTrailingZeros().scale() > 0) throw new BusinessException("error.invalidQty");
            stock.receive(p, openingBatch, openingExpiry, base, p.getCostPrice(), MovementType.OPENING, null, null, currentUser.get());
        }
        return p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String uniqueGeneratedBarcode(long id) {
        long seed = id;
        String code = barcodes.generateEan13(seed);
        while (products.existsByBarcode(code)) {
            seed += 1_000_000;
            code = barcodes.generateEan13(seed);
        }
        return code;
    }

    /** Deletes an unused product. A product that appears in any invoice or stock movement is deactivated instead. Returns true if deleted. */
    @Transactional
    public boolean deleteOrDeactivate(Long id) {
        Product p = get(id);
        long refs = 0;
        for (String entity : new String[]{"SaleItem", "PurchaseItem", "ReturnItem", "StockMovement", "Batch"}) {
            refs += em.createQuery("select count(x) from " + entity + " x where x.product.id = :id", Long.class)
                    .setParameter("id", id).getSingleResult();
        }
        if (refs > 0) {
            p.setActive(false);
            products.save(p);
            return false;
        }
        products.delete(p);
        return true;
    }
}
