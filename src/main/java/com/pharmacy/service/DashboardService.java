package com.pharmacy.service;

import com.pharmacy.entity.Product;
import com.pharmacy.repository.ProductRepository;
import com.pharmacy.repository.SaleRepository;
import com.pharmacy.util.Money;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final EntityManager em;
    private final ReportService reports;
    private final ProductRepository products;
    private final SaleRepository sales;

    private final com.pharmacy.repository.BatchRepository batches;
    private final com.pharmacy.util.Fmt fmt;

    public DashboardService(EntityManager em, ReportService reports, ProductRepository products, SaleRepository sales,
                            com.pharmacy.repository.BatchRepository batches, com.pharmacy.util.Fmt fmt) {
        this.batches = batches;
        this.fmt = fmt;
        this.em = em;
        this.reports = reports;
        this.products = products;
        this.sales = sales;
    }

    /** qty = quantity text in boxes / strips / units. */
    public record TopProduct(String name, String qty, BigDecimal amount) {}

    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay(), dayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        Object[] t = (Object[]) em.createQuery("select coalesce(sum(s.total),0), count(s), coalesce(sum(s.discount),0) from Sale s where s.date between :f and :t")
                .setParameter("f", dayStart).setParameter("t", dayEnd).getSingleResult();
        BigDecimal todayReturns = (BigDecimal) em.createQuery("select coalesce(sum(r.total),0) from ReturnInvoice r where r.type = com.pharmacy.entity.PartyType.CUSTOMER and r.date between :f and :t")
                .setParameter("f", dayStart).setParameter("t", dayEnd).getSingleResult();
        m.put("todaySales", ((BigDecimal) t[0]).subtract(todayReturns));
        m.put("todayCount", t[1]);

        BigDecimal profit = BigDecimal.ZERO;
        for (BigDecimal[] v : reports.productSales(dayStart, dayEnd).values()) profit = profit.add(v[1]).subtract(v[2]);
        m.put("todayProfit", Money.r(profit.subtract((BigDecimal) t[2])));

        BigDecimal month = (BigDecimal) em.createQuery("select coalesce(sum(s.total),0) from Sale s where s.date between :f and :t")
                .setParameter("f", monthStart).setParameter("t", dayEnd).getSingleResult();
        BigDecimal monthReturns = (BigDecimal) em.createQuery("select coalesce(sum(r.total),0) from ReturnInvoice r where r.type = com.pharmacy.entity.PartyType.CUSTOMER and r.date between :f and :t")
                .setParameter("f", monthStart).setParameter("t", dayEnd).getSingleResult();
        m.put("monthSales", month.subtract(monthReturns));

        m.put("lowStockCount", products.countLowStock());
        m.put("expiredCount", batches.countExpired(today));
        m.put("nearExpiryCount", batches.countExpiring(today, today.plusDays(90)));
        m.put("productCount", products.count());
        m.put("receivables", em.createQuery("select coalesce(sum(c.balance),0) from Customer c where c.balance > 0").getSingleResult());
        m.put("payables", em.createQuery("select coalesce(sum(s.balance),0) from Supplier s where s.balance > 0").getSingleResult());

        Map<LocalDate, BigDecimal> daily = reports.dailySales(today.minusDays(29), today);
        m.put("chartLabels", daily.keySet().stream().map(d -> d.getMonthValue() + "/" + d.getDayOfMonth()).toList());
        m.put("chartValues", daily.values());

        Map<Long, BigDecimal[]> agg = reports.productSales(monthStart, dayEnd);
        Map<Long, Product> prods = reports.productsById(agg.keySet());
        List<TopProduct> top = agg.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]))
                .limit(5)
                .map(e -> new TopProduct(prods.get(e.getKey()).getName(), fmt.stock(prods.get(e.getKey()), e.getValue()[0]), Money.r(e.getValue()[1])))
                .toList();
        m.put("topProducts", top);
        m.put("recentSales", sales.findTop8ByOrderByDateDesc());
        m.put("lowStock", products.findLowStock(org.springframework.data.domain.PageRequest.of(0, 8)));
        return m;
    }
}
