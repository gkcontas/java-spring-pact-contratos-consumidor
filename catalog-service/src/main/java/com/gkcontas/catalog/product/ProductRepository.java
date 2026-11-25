package com.gkcontas.catalog.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * Check and decrement in one statement: two concurrent reservations cannot both
     * read "10 left" and both succeed. Returns the number of rows touched, so 0 means
     * "unknown SKU or not enough stock" and the caller tells the two apart.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Product p
               set p.stock = p.stock - :quantity, p.version = p.version + 1
             where p.sku = :sku and p.stock >= :quantity
            """)
    int decrementStock(@Param("sku") String sku, @Param("quantity") int quantity);
}
