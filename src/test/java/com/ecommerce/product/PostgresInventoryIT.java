package com.ecommerce.product;

import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.entity.Inventory;
import com.ecommerce.product.enums.ReservationStatus;
import com.ecommerce.product.exception.*;
import com.ecommerce.product.repository.*;
import com.ecommerce.product.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
class PostgresInventoryIT {
    @Autowired ProductService products;
    @Autowired InventoryService inventory;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryReservationRepository reservations;
    @Autowired ProductRepository productRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    TransactionTemplate tx;
    @BeforeEach void setup() {
        tx = new TransactionTemplate(transactionManager);
        reservations.deleteAll(); inventories.deleteAll(); productRepository.deleteAll();
    }
    Long product(int stock) {
        return products.createProduct(new ProductCreateRequest("TEST-" + UUID.randomUUID(),
            "Phone", "Integration fixture", BigDecimal.TEN, stock)).id();
    }
    InventoryReservationRequest request(long order, Long product, int quantity) {
        return new InventoryReservationRequest(order, List.of(new InventoryReservationRequest.Item(product, quantity)));
    }
    @Test void productAndInventoryCreatedTogether() {
        Long id = product(10);
        assertThat(inventory.getInventory(id).totalQuantity()).isEqualTo(10);
        assertThat(products.searchProducts("phone", org.springframework.data.domain.PageRequest.of(0, 10)).totalElements()).isEqualTo(1);
    }
    @Test void createRollsBackProductWhenInventoryFails() {
        // Invalid direct service call tests rollback after the product INSERT, bypassing HTTP validation intentionally.
        assertThatThrownBy(() -> products.createProduct(new ProductCreateRequest("ROLLBACK", "Phone", null, BigDecimal.TEN, -1)))
            .isInstanceOf(InvalidInventoryOperationException.class);
        assertThat(productRepository.findBySku("ROLLBACK")).isEmpty();
    }
    @Test void multiItemReservationRollsBack() {
        Long first = product(10), second = product(0);
        var request = new InventoryReservationRequest(5001L, List.of(
            new InventoryReservationRequest.Item(first, 2), new InventoryReservationRequest.Item(second, 1)));
        assertThatThrownBy(() -> inventory.reserveInventory(request)).isInstanceOf(InsufficientStockException.class);
        assertThat(inventory.getInventory(first).reservedQuantity()).isZero();
        assertThat(reservations.findByOrderId(5001L)).isEmpty();
    }
    @Test void reserveInventory_concurrentOrders() throws Exception {
        Long id = product(1);
        CyclicBarrier bothReadSameVersion = new CyclicBarrier(2);
        List<Object> outcomes = parallel(
            () -> reserveFromSameVersion(7001L, id, bothReadSameVersion),
            () -> reserveFromSameVersion(7002L, id, bothReadSameVersion));
        assertThat(outcomes.stream().filter(Boolean.TRUE::equals).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(OptimisticLockingFailureException.class::isInstance).count()).isEqualTo(1);
        assertThat(inventory.getInventory(id).reservedQuantity()).isEqualTo(1);
        assertThat(reservations.count()).isEqualTo(1);
    }
    Object reserveFromSameVersion(Long orderId, Long id, CyclicBarrier barrier) {
        try {
            return tx.execute(status -> {
                Inventory snapshot = inventories.findByProductId(id).orElseThrow();
                assertThat(snapshot.getAvailableQuantity()).isEqualTo(1);
                await(barrier);
                inventory.reserveInventory(request(orderId, id, 1));
                return true;
            });
        } catch (OptimisticLockingFailureException e) { return e; }
    }
    @Test void concurrentIdenticalRetriesReserveOnlyOnce() throws Exception {
        Long id = product(10);
        var request = request(8001L, id, 2);
        var results = parallel(() -> inventory.reserveInventory(request), () -> inventory.reserveInventory(request));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(inventory.getInventory(id).reservedQuantity()).isEqualTo(2);
        assertThat(reservations.findByOrderId(8001L)).hasSize(1);
    }
    @Test void concurrentDifferentPayloadsForSameOrderCannotExtendReservation() throws Exception {
        Long first = product(10), second = product(10);
        List<Object> results = parallel(
            () -> reserveOrConflict(request(8101L, first, 1)),
            () -> reserveOrConflict(request(8101L, second, 1)));
        assertThat(results.stream().filter(InvalidInventoryOperationException.class::isInstance).count()).isEqualTo(1);
        assertThat(reservations.findByOrderId(8101L)).hasSize(1);
        assertThat(inventory.getInventory(first).reservedQuantity() + inventory.getInventory(second).reservedQuantity()).isEqualTo(1);
    }
    Object reserveOrConflict(InventoryReservationRequest request) {
        try { return inventory.reserveInventory(request); } catch (InvalidInventoryOperationException e) { return e; }
    }
    @Test void concurrentConfirmAndReleaseHaveOneTerminalWinner() throws Exception {
        Long id = product(10); inventory.reserveInventory(request(8201L, id, 2));
        List<Object> results = parallel(() -> completeOrConflict(true), () -> completeOrConflict(false));
        assertThat(results.stream().filter(InvalidInventoryOperationException.class::isInstance).count()).isEqualTo(1);
        var finalStatus = reservations.findByOrderId(8201L).getFirst().getStatus();
        assertThat(inventory.getInventory(id).reservedQuantity()).isZero();
        assertThat(inventory.getInventory(id).totalQuantity()).isEqualTo(finalStatus == ReservationStatus.CONFIRMED ? 8 : 10);
    }
    Object completeOrConflict(boolean confirm) {
        try { return confirm ? inventory.confirmInventory(8201L) : inventory.releaseInventory(8201L); }
        catch (InvalidInventoryOperationException e) { return e; }
    }
    @Test void repeatedConfirmAndReserveAfterConfirmationDoNotDeductTwice() {
        Long id = product(10); var request = request(9001L, id, 2);
        inventory.reserveInventory(request); inventory.confirmInventory(9001L); inventory.confirmInventory(9001L);
        assertThat(inventory.reserveInventory(request).items().getFirst().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(inventory.getInventory(id).totalQuantity()).isEqualTo(8);
        assertThat(inventory.getInventory(id).reservedQuantity()).isZero();
    }
    @Test void releaseAfterSoftDeleteStillWorks() {
        Long id = product(10); inventory.reserveInventory(request(9002L, id, 2));
        products.deleteProduct(id); inventory.releaseInventory(9002L); inventory.releaseInventory(9002L);
        assertThat(inventory.getInventory(id).availableQuantity()).isEqualTo(10);
        assertThatThrownBy(() -> products.getProductById(id)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> inventory.reserveInventory(request(9003L, id, 1))).isInstanceOf(InvalidInventoryOperationException.class);
    }
    @Test void concurrentDuplicateConfirmDeductsOnlyOnce() throws Exception {
        Long id = product(10); inventory.reserveInventory(request(9004L, id, 2));
        parallel(() -> inventory.confirmInventory(9004L), () -> inventory.confirmInventory(9004L));
        assertThat(inventory.getInventory(id).totalQuantity()).isEqualTo(8);
    }
    @Test void databaseRejectsInvalidCounters() {
        Long id = product(10);
        assertThatThrownBy(() -> jdbc.update("update inventory set reserved_quantity = 11 where product_id = ?", id))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(inventory.getInventory(id).totalQuantity()).isEqualTo(10);
        assertThat(inventory.getInventory(id).reservedQuantity()).isZero();
    }
    private List<Object> parallel(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> a = executor.submit(() -> { start.await(); return first.call(); });
            Future<Object> b = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private void await(CyclicBarrier barrier) {
        try { barrier.await(10, TimeUnit.SECONDS); }
        catch (Exception e) { throw new IllegalStateException("Concurrent test synchronization failed", e); }
    }
}
