package com.pharmacy.service;

import com.pharmacy.dto.ReportTable;
import com.pharmacy.entity.*;
import com.pharmacy.repository.BatchRepository;
import com.pharmacy.repository.CustomerRepository;
import com.pharmacy.util.Fmt;
import com.pharmacy.repository.ProductRepository;
import com.pharmacy.repository.SupplierRepository;
import com.pharmacy.util.Money;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Report catalogue shown on the reports index page. */
    public record ReportDef(String id, String titleKey, String icon, boolean dated) {}

    public static final List<ReportDef> REPORTS = List.of(
            new ReportDef("expired-stock", "report.expiredStock", "bi-calendar-x", false),
            new ReportDef("near-expiry", "report.nearExpiry", "bi-hourglass-split", false),
            new ReportDef("reorder", "report.reorder", "bi-cart-check", false),
            new ReportDef("dead-stock", "report.deadStock", "bi-snow", true),
            new ReportDef("sales-daily", "report.salesDaily", "bi-calendar3", true),
            new ReportDef("sales-by-product", "report.salesByProduct", "bi-box-seam", true),
            new ReportDef("sales-by-category", "report.salesByCategory", "bi-tags", true),
            new ReportDef("sales-by-cashier", "report.salesByCashier", "bi-person-badge", true),
            new ReportDef("purchases-by-supplier", "report.purchasesBySupplier", "bi-truck", true),
            new ReportDef("purchases-by-product", "report.purchasesByProduct", "bi-cart-plus", true),
            new ReportDef("profit-loss", "report.profitLoss", "bi-graph-up-arrow", true),
            new ReportDef("best-sellers", "report.bestSellers", "bi-trophy", true),
            new ReportDef("worst-sellers", "report.worstSellers", "bi-arrow-down-circle", true),
            new ReportDef("low-stock", "report.lowStock", "bi-exclamation-triangle", false),
            new ReportDef("inventory-valuation", "report.inventoryValuation", "bi-archive", false),
            new ReportDef("customer-balances", "report.customerBalances", "bi-people", false),
            new ReportDef("supplier-balances", "report.supplierBalances", "bi-building", false),
            new ReportDef("tax-summary", "report.taxSummary", "bi-percent", true));

    private final EntityManager em;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final BatchRepository batches;
    private final Fmt fmt;

    public ReportService(EntityManager em, ProductRepository products, CustomerRepository customers, SupplierRepository suppliers,
                         BatchRepository batches, Fmt fmt) {
        this.batches = batches;
        this.fmt = fmt;
        this.em = em;
        this.products = products;
        this.customers = customers;
        this.suppliers = suppliers;
    }

    public Optional<ReportDef> find(String id) {
        return REPORTS.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public ReportTable build(String id, LocalDate from, LocalDate to) {
        LocalDateTime f = from.atStartOfDay(), t = to.atTime(LocalTime.MAX);
        return switch (id) {
            case "sales-daily" -> salesDaily(f, t);
            case "sales-by-product" -> salesByProduct(f, t);
            case "sales-by-category" -> salesByCategory(f, t);
            case "sales-by-cashier" -> salesByCashier(f, t);
            case "purchases-by-supplier" -> purchasesBySupplier(f, t);
            case "purchases-by-product" -> purchasesByProduct(f, t);
            case "profit-loss" -> profitLoss(f, t);
            case "best-sellers" -> sellers(f, t, true);
            case "worst-sellers" -> sellers(f, t, false);
            case "low-stock" -> lowStock();
            case "inventory-valuation" -> inventoryValuation();
            case "customer-balances" -> balances(true);
            case "supplier-balances" -> balances(false);
            case "tax-summary" -> taxSummary(f, t);
            case "expired-stock" -> expiry(true);
            case "near-expiry" -> expiry(false);
            case "reorder" -> reorder();
            case "dead-stock" -> deadStock(f, t);
            default -> throw new BusinessException("error.notFound");
        };
    }

    // ---------------------------------------------------------------- sales

    private ReportTable salesDaily(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("sales-daily", "report.salesDaily",
                "col.date", "col.invoices", "col.subtotal", "col.discount", "col.tax", "col.total", "col.returns", "col.net");
        Map<LocalDate, BigDecimal> returnsByDay = new HashMap<>();
        for (Object[] r : list(em.createNativeQuery("""
                select DATE(date), SUM(total) from returns where type = 'CUSTOMER' and date between ?1 and ?2 group by DATE(date)
                """).setParameter(1, f).setParameter(2, t).getResultList())) {
            returnsByDay.put(toDate(r[0]), dec(r[1]));
        }
        BigDecimal[] sum = zeros(6);
        long count = 0;
        for (Object[] r : list(em.createNativeQuery("""
                select DATE(date) d, COUNT(*), SUM(subtotal), SUM(discount), SUM(tax_amount), SUM(total)
                from sales where date between ?1 and ?2 group by DATE(date) order by d
                """).setParameter(1, f).setParameter(2, t).getResultList())) {
            LocalDate day = toDate(r[0]);
            long n = ((Number) r[1]).longValue();
            BigDecimal ret = returnsByDay.getOrDefault(day, BigDecimal.ZERO);
            BigDecimal total = dec(r[5]);
            rt.row(day, n, dec(r[2]), dec(r[3]), dec(r[4]), total, ret, total.subtract(ret));
            count += n;
            add(sum, dec(r[2]), dec(r[3]), dec(r[4]), total, ret, total.subtract(ret));
        }
        return rt.totals("col.total", count, sum[0], sum[1], sum[2], sum[3], sum[4], sum[5]);
    }

    /** Per-product sales net of returns. Amounts are line totals (before the invoice-level discount). */
    private ReportTable salesByProduct(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("sales-by-product", "report.salesByProduct",
                "col.barcode", "col.product", "col.qty", "col.amount", "col.cost", "col.profit");
        Map<Long, BigDecimal[]> agg = productSales(f, t);
        Map<Long, Product> prods = productsById(agg.keySet());
        BigDecimal[] sum = zeros(4);
        agg.entrySet().stream()
                .sorted((a, b) -> b.getValue()[1].compareTo(a.getValue()[1]))
                .forEach(e -> {
                    Product p = prods.get(e.getKey());
                    BigDecimal[] v = e.getValue();
                    BigDecimal profit = v[1].subtract(v[2]);
                    rt.row(p.getBarcode(), p.getName(), fmt.stock(p, v[0]), Money.r(v[1]), Money.r(v[2]), Money.r(profit));
                    add(sum, v[0], v[1], v[2], profit);
                });
        return rt.totals("col.total", "", "", Money.r(sum[1]), Money.r(sum[2]), Money.r(sum[3]));
    }

    private ReportTable salesByCategory(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("sales-by-category", "report.salesByCategory",
                "col.category", "col.amount", "col.cost", "col.profit");
        Map<Long, BigDecimal[]> agg = productSales(f, t);
        Map<Long, Product> prods = productsById(agg.keySet());
        Map<String, BigDecimal[]> byCat = new TreeMap<>();
        agg.forEach((pid, v) -> {
            Category c = prods.get(pid).getCategory();
            String name = c == null ? "-" : c.getName();
            BigDecimal[] acc = byCat.computeIfAbsent(name, k -> zeros(3));
            add(acc, v[0], v[1], v[2]);
        });
        BigDecimal[] sum = zeros(4);
        byCat.forEach((name, v) -> {
            BigDecimal profit = v[1].subtract(v[2]);
            rt.row(name, Money.r(v[1]), Money.r(v[2]), Money.r(profit));
            add(sum, v[0], v[1], v[2], profit);
        });
        return rt.totals("col.total", Money.r(sum[1]), Money.r(sum[2]), Money.r(sum[3]));
    }

    private ReportTable salesByCashier(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("sales-by-cashier", "report.salesByCashier",
                "col.cashier", "col.invoices", "col.discount", "col.total");
        long count = 0;
        BigDecimal[] sum = zeros(2);
        for (Object[] r : list(em.createQuery("""
                select coalesce(u.fullName, u.username), count(s), sum(s.discount), sum(s.total)
                from Sale s left join s.user u where s.date between :f and :t
                group by u.id, u.fullName, u.username order by sum(s.total) desc
                """).setParameter("f", f).setParameter("t", t).getResultList())) {
            long n = ((Number) r[1]).longValue();
            rt.row(r[0], n, dec(r[2]), dec(r[3]));
            count += n;
            add(sum, dec(r[2]), dec(r[3]));
        }
        return rt.totals("col.total", count, sum[0], sum[1]);
    }

    // ---------------------------------------------------------------- purchases

    private ReportTable purchasesBySupplier(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("purchases-by-supplier", "report.purchasesBySupplier",
                "col.supplier", "col.invoices", "col.total", "col.paid", "col.remaining", "col.returns");
        long count = 0;
        BigDecimal[] sum = zeros(4);
        for (Object[] r : list(em.createQuery("""
                select s.name, count(p), sum(p.total), sum(p.paid), sum(p.returnedAmount)
                from Purchase p join p.supplier s where p.date between :f and :t
                group by s.id, s.name order by sum(p.total) desc
                """).setParameter("f", f).setParameter("t", t).getResultList())) {
            long n = ((Number) r[1]).longValue();
            BigDecimal total = dec(r[2]), paid = dec(r[3]);
            rt.row(r[0], n, total, paid, total.subtract(paid), dec(r[4]));
            count += n;
            add(sum, total, paid, total.subtract(paid), dec(r[4]));
        }
        return rt.totals("col.total", count, sum[0], sum[1], sum[2], sum[3]);
    }

    private ReportTable purchasesByProduct(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("purchases-by-product", "report.purchasesByProduct",
                "col.barcode", "col.product", "col.boxes", "col.bonus", "col.avgCost", "col.total");
        List<Object[]> data = list(em.createQuery("""
                select i.product.id, sum(i.qty), sum(i.lineTotal), sum(i.bonusQty) from PurchaseItem i
                where i.purchase.date between :f and :t group by i.product.id order by sum(i.lineTotal) desc
                """).setParameter("f", f).setParameter("t", t).getResultList());
        Map<Long, Product> prods = productsById(data.stream().map(r -> (Long) r[0]).toList());
        BigDecimal[] sum = zeros(2);
        for (Object[] r : data) {
            Product p = prods.get((Long) r[0]);
            BigDecimal qty = dec(r[1]), total = dec(r[2]);
            BigDecimal bonus = dec(r[3]);
            // Average effective cost per box, bonus boxes included.
            BigDecimal boxes = qty.add(bonus);
            BigDecimal avg = boxes.signum() == 0 ? BigDecimal.ZERO : total.divide(boxes, 2, RoundingMode.HALF_UP);
            rt.row(p.getBarcode(), p.getName(), qty, bonus, avg, total);
            add(sum, qty, total);
        }
        return rt.totals("col.total", "", sum[0], "", "", sum[1]);
    }

    // ---------------------------------------------------------------- profit

    private ReportTable profitLoss(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("profit-loss", "report.profitLoss",
                "col.date", "col.netSales", "col.cost", "col.profit");

        Object[] s = (Object[]) em.createQuery("""
                select coalesce(sum(s.subtotal - s.discount), 0), coalesce(sum(s.taxAmount), 0), count(s)
                from Sale s where s.date between :f and :t
                """).setParameter("f", f).setParameter("t", t).getSingleResult();
        BigDecimal grossSales = dec(s[0]);
        BigDecimal cogs = dec(em.createQuery("""
                select coalesce(sum(i.qty * i.unitCost), 0) from SaleItem i where i.sale.date between :f and :t
                """).setParameter("f", f).setParameter("t", t).getSingleResult());
        Object[] r = (Object[]) em.createQuery("""
                select coalesce(sum(r.total - r.taxAmount), 0), coalesce(sum(r.costTotal), 0)
                from ReturnInvoice r where r.type = :type and r.date between :f and :t
                """).setParameter("type", PartyType.CUSTOMER).setParameter("f", f).setParameter("t", t).getSingleResult();
        BigDecimal returnsNet = dec(r[0]), returnsCost = dec(r[1]);

        BigDecimal netSales = grossSales.subtract(returnsNet);
        BigDecimal netCogs = cogs.subtract(returnsCost);
        BigDecimal profit = netSales.subtract(netCogs);
        BigDecimal margin = netSales.signum() == 0 ? BigDecimal.ZERO
                : profit.multiply(BigDecimal.valueOf(100)).divide(netSales, 2, RoundingMode.HALF_UP);

        rt.summary("pl.invoices", ((Number) s[2]).longValue())
          .summary("pl.grossSales", Money.r(grossSales))
          .summary("pl.salesReturns", Money.r(returnsNet))
          .highlight("pl.netSales", Money.r(netSales))
          .summary("pl.cogs", Money.r(netCogs))
          .highlight("pl.grossProfit", Money.r(profit))
          .summary("pl.margin", margin + " %");

        // Daily breakdown
        Map<LocalDate, BigDecimal[]> days = new TreeMap<>();
        for (Object[] d : list(em.createNativeQuery("""
                select DATE(s.date), SUM(i.line_total), SUM(i.qty * i.unit_cost)
                from sale_items i join sales s on s.id = i.sale_id where s.date between ?1 and ?2 group by DATE(s.date)
                """).setParameter(1, f).setParameter(2, t).getResultList())) {
            days.computeIfAbsent(toDate(d[0]), k -> zeros(2));
            add(days.get(toDate(d[0])), dec(d[1]), dec(d[2]));
        }
        for (Object[] d : list(em.createNativeQuery("""
                select DATE(date), SUM(discount) from sales where date between ?1 and ?2 group by DATE(date)
                """).setParameter(1, f).setParameter(2, t).getResultList())) {
            days.computeIfAbsent(toDate(d[0]), k -> zeros(2));
            add(days.get(toDate(d[0])), dec(d[1]).negate(), BigDecimal.ZERO);
        }
        for (Object[] d : list(em.createNativeQuery("""
                select DATE(date), SUM(total - tax_amount), SUM(cost_total) from returns
                where type = 'CUSTOMER' and date between ?1 and ?2 group by DATE(date)
                """).setParameter(1, f).setParameter(2, t).getResultList())) {
            days.computeIfAbsent(toDate(d[0]), k -> zeros(2));
            add(days.get(toDate(d[0])), dec(d[1]).negate(), dec(d[2]).negate());
        }
        days.forEach((day, v) -> rt.row(day, Money.r(v[0]), Money.r(v[1]), Money.r(v[0].subtract(v[1]))));
        return rt.totals("col.total", Money.r(netSales), Money.r(netCogs), Money.r(profit));
    }

    private ReportTable sellers(LocalDateTime f, LocalDateTime t, boolean best) {
        ReportTable rt = new ReportTable(best ? "best-sellers" : "worst-sellers",
                best ? "report.bestSellers" : "report.worstSellers",
                "col.rank", "col.barcode", "col.product", "col.qty", "col.amount", "col.stock");
        Map<Long, BigDecimal[]> agg = productSales(f, t);
        List<Product> all = best ? productsById(agg.keySet()).values().stream().toList()
                : products.findByActiveTrueOrderByNameAr();   // include products that never sold
        Comparator<Product> byQty = Comparator.comparing(p -> agg.getOrDefault(p.getId(), zeros(3))[0]);
        List<Product> sorted = all.stream().sorted(best ? byQty.reversed() : byQty).limit(50).toList();
        int rank = 1;
        for (Product p : sorted) {
            BigDecimal[] v = agg.getOrDefault(p.getId(), zeros(3));
            rt.row(rank++, p.getBarcode(), p.getName(), fmt.stock(p, v[0]), Money.r(v[1]), fmt.stock(p));
        }
        return rt;
    }

    // ---------------------------------------------------------------- inventory

    private ReportTable lowStock() {
        ReportTable rt = new ReportTable("low-stock", "report.lowStock",
                "col.barcode", "col.product", "col.category", "col.stock", "col.minStock", "col.shortage");
        for (Product p : products.findLowStock()) {
            rt.row(p.getBarcode(), p.getName(), p.getCategory() == null ? "-" : p.getCategory().getName(),
                    fmt.stock(p), p.getMinStock(), p.getMinStock().subtract(p.getStockBoxes()).max(BigDecimal.ZERO).setScale(3, RoundingMode.UP));
        }
        return rt;
    }

    private ReportTable inventoryValuation() {
        ReportTable rt = new ReportTable("inventory-valuation", "report.inventoryValuation",
                "col.barcode", "col.product", "col.stock", "col.costPrice", "col.costValue", "col.salePrice", "col.saleValue");
        BigDecimal[] sum = zeros(3);
        for (Product p : products.findByActiveTrueOrderByNameAr()) {
            BigDecimal boxes = p.getStockBoxes().max(BigDecimal.ZERO);
            BigDecimal cv = Money.r(boxes.multiply(p.getCostPrice())), sv = Money.r(boxes.multiply(p.getSalePrice()));
            rt.row(p.getBarcode(), p.getName(), fmt.stock(p), p.getCostPrice(), cv, p.getSalePrice(), sv);
            add(sum, boxes, cv, sv);
        }
        rt.highlight("inv.costValue", sum[1]).highlight("inv.saleValue", sum[2])
          .summary("inv.expectedProfit", sum[2].subtract(sum[1]));
        return rt.totals("col.total", "", "", "", sum[1], "", sum[2]);
    }

    private ReportTable balances(boolean customer) {
        ReportTable rt = new ReportTable(customer ? "customer-balances" : "supplier-balances",
                customer ? "report.customerBalances" : "report.supplierBalances",
                "col.name", "col.phone", "col.balance");
        List<? extends Party> parties = customer ? customers.findAllByOrderByNameAsc() : suppliers.findAllByOrderByNameAsc();
        BigDecimal sum = BigDecimal.ZERO;
        for (Party p : parties) {
            if (p.getBalance().signum() == 0) continue;
            rt.row(p.getName(), p.getPhone(), p.getBalance());
            sum = sum.add(p.getBalance());
        }
        return rt.totals("col.total", "", sum);
    }

    private ReportTable taxSummary(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("tax-summary", "report.taxSummary", "col.item", "col.taxable", "col.tax");
        Object[] s = (Object[]) em.createQuery("select coalesce(sum(s.taxableAmount),0), coalesce(sum(s.taxAmount),0), coalesce(sum(s.subtotal - s.discount),0) from Sale s where s.date between :f and :t")
                .setParameter("f", f).setParameter("t", t).getSingleResult();
        Object[] p = (Object[]) em.createQuery("select coalesce(sum(p.taxableAmount),0), coalesce(sum(p.taxAmount),0), coalesce(sum(p.subtotal - p.discount),0) from Purchase p where p.date between :f and :t")
                .setParameter("f", f).setParameter("t", t).getSingleResult();
        Object[] sr = returnTax(PartyType.CUSTOMER, f, t), pr = returnTax(PartyType.SUPPLIER, f, t);

        BigDecimal output = dec(s[1]).subtract(dec(sr[1]));
        BigDecimal input = dec(p[1]).subtract(dec(pr[1]));
        BigDecimal exemptSales = dec(s[2]).subtract(dec(s[0])).subtract(dec(sr[2]).subtract(dec(sr[0])));
        rt.row(new ReportTable.Msg("tax.sales"), dec(s[0]), dec(s[1]))
          .row(new ReportTable.Msg("tax.salesReturns"), dec(sr[0]).negate(), dec(sr[1]).negate())
          .row(new ReportTable.Msg("tax.purchases"), dec(p[0]), dec(p[1]))
          .row(new ReportTable.Msg("tax.purchaseReturns"), dec(pr[0]).negate(), dec(pr[1]).negate());
        rt.summary("tax.exemptSales", exemptSales)
          .summary("tax.output", output).summary("tax.input", input).highlight("tax.net", output.subtract(input));
        return rt;
    }

    private Object[] returnTax(PartyType type, LocalDateTime f, LocalDateTime t) {
        return (Object[]) em.createQuery("select coalesce(sum(r.taxableAmount),0), coalesce(sum(r.taxAmount),0), coalesce(sum(r.total - r.taxAmount),0) from ReturnInvoice r where r.type = :type and r.date between :f and :t")
                .setParameter("type", type).setParameter("f", f).setParameter("t", t).getSingleResult();
    }

    // ---------------------------------------------------------------- pharmacy

    /** Expired batches still in stock, or batches expiring within 90 days. */
    private ReportTable expiry(boolean expired) {
        ReportTable rt = new ReportTable(expired ? "expired-stock" : "near-expiry",
                expired ? "report.expiredStock" : "report.nearExpiry",
                "col.product", "col.batchNo", "col.expiry", "col.daysLeft", "col.stock", "col.costValue");
        LocalDate today = LocalDate.now();
        BigDecimal sum = BigDecimal.ZERO;
        for (Batch b : batches.findExpiringUntil(expired ? today.minusDays(1) : today.plusDays(90))) {
            if (!expired && b.isExpired()) continue;
            Product p = b.getProduct();
            BigDecimal value = Money.r(b.getQty().multiply(b.getCostPerBox()).divide(BigDecimal.valueOf(p.getFactor()), 6, RoundingMode.HALF_UP));
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, b.getExpiryDate());
            rt.row(p.getName(), b.getBatchNo() == null ? "-" : b.getBatchNo(), b.getExpiryDate(), days, fmt.stock(p, b.getQty()), value);
            sum = sum.add(value);
        }
        rt.highlight("inv.costValue", sum);
        return rt.totals("col.total", "", "", "", "", sum);
    }

    /**
     * Reorder suggestions: average daily sales over the last 30 days × 30 days of cover, minus sellable stock,
     * rounded up to whole boxes. Also lists items at or below their minimum stock.
     */
    private ReportTable reorder() {
        ReportTable rt = new ReportTable("reorder", "report.reorder",
                "col.barcode", "col.product", "col.stock", "col.sold30", "col.minStock", "col.suggestBoxes");
        LocalDate today = LocalDate.now();
        Map<Long, BigDecimal[]> sold = productSales(today.minusDays(29).atStartOfDay(), today.atTime(LocalTime.MAX));
        for (Product p : products.findByActiveTrueOrderByNameAr()) {
            BigDecimal soldBase = sold.getOrDefault(p.getId(), zeros(3))[0].max(BigDecimal.ZERO);
            BigDecimal sellable = batches.sellableQty(p.getId(), today);
            BigDecimal factor = BigDecimal.valueOf(p.getFactor());
            BigDecimal needBase = soldBase.max(p.getMinStock().multiply(factor)).subtract(sellable);
            if (needBase.signum() <= 0) continue;
            BigDecimal boxes = needBase.divide(factor, 0, RoundingMode.UP);
            rt.row(p.getBarcode(), p.getName(), fmt.stock(p, sellable), fmt.stock(p, soldBase), p.getMinStock(), boxes);
        }
        return rt;
    }

    /** Items with stock but no sales in the period. */
    private ReportTable deadStock(LocalDateTime f, LocalDateTime t) {
        ReportTable rt = new ReportTable("dead-stock", "report.deadStock",
                "col.barcode", "col.product", "col.stock", "col.costValue");
        Map<Long, BigDecimal[]> sold = productSales(f, t);
        BigDecimal sum = BigDecimal.ZERO;
        for (Product p : products.findByActiveTrueOrderByNameAr()) {
            if (p.getStockQty().signum() <= 0) continue;
            BigDecimal[] v = sold.get(p.getId());
            if (v != null && v[0].signum() > 0) continue;
            BigDecimal value = Money.r(p.getStockBoxes().multiply(p.getCostPrice()));
            rt.row(p.getBarcode(), p.getName(), fmt.stock(p), value);
            sum = sum.add(value);
        }
        rt.highlight("inv.costValue", sum);
        return rt.totals("col.total", "", "", sum);
    }

    // ---------------------------------------------------------------- dashboard helpers

    /** product id -> [qty, amount, cost], net of sales returns. */
    public Map<Long, BigDecimal[]> productSales(LocalDateTime f, LocalDateTime t) {
        Map<Long, BigDecimal[]> agg = new HashMap<>();
        for (Object[] r : list(em.createQuery("""
                select i.product.id, sum(i.baseQty), sum(i.lineTotal), sum(i.qty * i.unitCost)
                from SaleItem i where i.sale.date between :f and :t group by i.product.id
                """).setParameter("f", f).setParameter("t", t).getResultList())) {
            agg.put((Long) r[0], new BigDecimal[]{dec(r[1]), dec(r[2]), dec(r[3])});
        }
        for (Object[] r : list(em.createQuery("""
                select i.product.id, sum(i.baseQty), sum(i.lineTotal), sum(i.qty * i.unitCost)
                from ReturnItem i where i.returnInvoice.type = :type and i.returnInvoice.date between :f and :t
                group by i.product.id
                """).setParameter("type", PartyType.CUSTOMER).setParameter("f", f).setParameter("t", t).getResultList())) {
            BigDecimal[] v = agg.computeIfAbsent((Long) r[0], k -> zeros(3));
            add(v, dec(r[1]).negate(), dec(r[2]).negate(), dec(r[3]).negate());
        }
        return agg;
    }

    /** Daily sales totals for charts: date -> total. Days without sales are filled with zero. */
    public Map<LocalDate, BigDecimal> dailySales(LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) map.put(d, BigDecimal.ZERO);
        for (Object[] r : list(em.createNativeQuery("select DATE(date), SUM(total) from sales where date between ?1 and ?2 group by DATE(date)")
                .setParameter(1, from.atStartOfDay()).setParameter(2, to.atTime(LocalTime.MAX)).getResultList())) {
            map.put(toDate(r[0]), dec(r[1]));
        }
        return map;
    }

    public Map<Long, Product> productsById(Collection<Long> ids) {
        if (ids.isEmpty()) return new HashMap<>();
        return products.findByIdIn(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    // ---------------------------------------------------------------- utils

    @SuppressWarnings("unchecked")
    private static List<Object[]> list(List<?> raw) {
        return (List<Object[]>) raw;
    }

    static BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(o.toString());
    }

    private static LocalDate toDate(Object o) {
        if (o instanceof Date d) return d.toLocalDate();
        if (o instanceof LocalDate d) return d;
        return LocalDate.parse(o.toString().substring(0, 10));
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static void add(BigDecimal[] acc, BigDecimal... values) {
        for (int i = 0; i < values.length; i++) acc[i] = acc[i].add(values[i]);
    }
}
