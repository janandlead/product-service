package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.enums.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
	boolean existsBySku(String sku);

	Optional<Product> findBySku(String sku);

	Page<Product> findByStatusNot(ProductStatus status, Pageable pageable);

	@Query("select p from Product p where p.status <> com.ecommerce.product.enums.ProductStatus.DELETED and (lower(p.name) like lower(concat('%', :keyword, '%')) escape '!' or lower(p.sku) like lower(concat('%', :keyword, '%')) escape '!')")
	Page<Product> search(String keyword, Pageable pageable);

	@Lock(LockModeType.OPTIMISTIC)
	@Query("select p from Product p where p.id = :id")
	Optional<Product> findForReservation(Long id);
}
