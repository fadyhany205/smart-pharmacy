package com.pharmacy.repository;

import com.pharmacy.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Category/unit are loaded in the same query (@EntityGraph) so lists do not run one extra query per row. */
public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = "unit")
    Optional<Product> findByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    boolean existsByBarcode(String barcode);

    @EntityGraph(attributePaths = {"category", "unit"})
    @Query(value = """
            select p from Product p
            where (:q is null or p.nameAr like concat('%', :q, '%') or p.nameEn like concat('%', :q, '%')
                   or p.scientificName like concat('%', :q, '%') or p.barcode = :q or p.sku like concat('%', :q, '%'))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:lowOnly = false or p.stockQty <= p.minStock * p.stripsPerBox * p.unitsPerStrip)
            """,
            countQuery = """
            select count(p) from Product p
            where (:q is null or p.nameAr like concat('%', :q, '%') or p.nameEn like concat('%', :q, '%')
                   or p.scientificName like concat('%', :q, '%') or p.barcode = :q or p.sku like concat('%', :q, '%'))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:lowOnly = false or p.stockQty <= p.minStock * p.stripsPerBox * p.unitsPerStrip)
            """)
    Page<Product> search(@Param("q") String q, @Param("categoryId") Long categoryId,
                         @Param("lowOnly") boolean lowOnly, Pageable pageable);

    @EntityGraph(attributePaths = "unit")
    @Query("""
            select p from Product p
            where p.active = true and (p.nameAr like concat('%', :q, '%') or p.nameEn like concat('%', :q, '%')
                   or p.scientificName like concat('%', :q, '%') or p.barcode like concat(:q, '%') or p.sku like concat(:q, '%'))
            order by p.nameAr
            """)
    List<Product> quickSearch(@Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("select p from Product p where p.active = true and p.stockQty <= p.minStock * p.stripsPerBox * p.unitsPerStrip order by p.stockQty")
    List<Product> findLowStock();

    /** Only the first rows (dashboard). */
    @Query("select p from Product p where p.active = true and p.stockQty <= p.minStock * p.stripsPerBox * p.unitsPerStrip order by p.stockQty")
    List<Product> findLowStock(Pageable pageable);

    @Query("select count(p) from Product p where p.active = true and p.stockQty <= p.minStock * p.stripsPerBox * p.unitsPerStrip")
    long countLowStock();

    List<Product> findByActiveTrueOrderByNameAr();

    /** For the import: externalRef -> id and barcode -> id of all products (loaded once per import). */
    @Query("select p.externalRef, p.id from Product p where p.externalRef is not null")
    List<Object[]> allExternalRefs();

    @Query("select p.barcode, p.id from Product p where p.barcode is not null")
    List<Object[]> allBarcodes();

    /** Same active ingredient, other products (substitutes). */
    @EntityGraph(attributePaths = "unit")
    @Query("select p from Product p where p.active = true and p.id <> :excludeId and lower(p.scientificName) = lower(:scientificName) order by p.nameAr")
    List<Product> findSubstitutes(@Param("scientificName") String scientificName, @Param("excludeId") Long excludeId, Pageable pageable);

    /** Like findAllById but with the category loaded (reports). */
    @EntityGraph(attributePaths = "category")
    List<Product> findByIdIn(Collection<Long> ids);
}
