package com.ecommerce.product.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL transaction locks serialize all operations for one order,
 * including its first reservation.
 */
@Repository
@RequiredArgsConstructor
public class OrderTransactionLock {
	private final EntityManager entityManager;

	public void acquire(Long orderId) {
		// Transaction-scoped: PostgreSQL releases this automatically on commit or
		// rollback.
		entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(:orderId)")
				.setParameter("orderId", orderId).getSingleResult();
	}
}
