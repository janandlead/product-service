package com.ecommerce.product;
import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.entity.*;
import com.ecommerce.product.enums.ReservationStatus;
import com.ecommerce.product.exception.*;
import com.ecommerce.product.repository.*;
import com.ecommerce.product.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock ProductRepository products;
    @Mock InventoryRepository inventories;
    @Mock InventoryReservationRepository reservations;
    @Mock OrderTransactionLock lock;
    @InjectMocks InventoryServiceImpl service;
    Inventory stock;
    @BeforeEach void setup() { stock = new Inventory(1L, 10); }
    InventoryReservationRequest request() {
        return new InventoryReservationRequest(5001L, List.of(new InventoryReservationRequest.Item(1L, 2)));
    }
    void active(Long id, Inventory i) {
        when(products.findForReservation(id)).thenReturn(Optional.of(new Product("SKU" + id, "Phone", null, BigDecimal.TEN)));
        when(inventories.findByProductId(id)).thenReturn(Optional.of(i));
    }
    InventoryReservation reserved() {
        stock.reserve(2);
        InventoryReservation r = new InventoryReservation(5001L, 1L, 2);
        when(reservations.findByOrderId(5001L)).thenReturn(List.of(r));
        when(inventories.findByProductId(1L)).thenReturn(Optional.of(stock));
        return r;
    }
    @Test void reserveInventory_success() {
        active(1L, stock); var result = service.reserveInventory(request());
        assertThat(stock.getAvailableQuantity()).isEqualTo(8);
        assertThat(result.items().getFirst().status()).isEqualTo(ReservationStatus.RESERVED);
    }
    @Test void reserveInventory_insufficientStock() {
        active(1L, stock);
        assertThatThrownBy(() -> service.reserveInventory(new InventoryReservationRequest(5001L,
            List.of(new InventoryReservationRequest.Item(1L, 11))))).isInstanceOf(InsufficientStockException.class);
        assertThat(stock.getReservedQuantity()).isZero();
        verify(reservations, never()).saveAllAndFlush(any());
    }
    @Test void reserveInventory_multipleProducts() {
        Inventory second = new Inventory(2L, 3); active(1L, stock); active(2L, second);
        service.reserveInventory(new InventoryReservationRequest(5001L, List.of(
            new InventoryReservationRequest.Item(2L, 3), new InventoryReservationRequest.Item(1L, 2))));
        assertThat(stock.getReservedQuantity()).isEqualTo(2);
        assertThat(second.getAvailableQuantity()).isZero();
    }
    @Test void reserveInventory_duplicateRequest() {
        when(reservations.findByOrderId(5001L)).thenReturn(List.of(new InventoryReservation(5001L, 1L, 2)));
        service.reserveInventory(request());
        verifyNoInteractions(inventories, products);
    }
    @Test void reserveInventory_changedPayloadRejected() {
        when(reservations.findByOrderId(5001L)).thenReturn(List.of(new InventoryReservation(5001L, 1L, 3)));
        assertThatThrownBy(() -> service.reserveInventory(request())).isInstanceOf(InvalidInventoryOperationException.class);
    }
    @Test void reserveInventory_duplicateProductsRejected() {
        assertThatThrownBy(() -> service.reserveInventory(new InventoryReservationRequest(1L,
            List.of(new InventoryReservationRequest.Item(1L, 1), new InventoryReservationRequest.Item(1L, 2)))))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lock, inventories);
    }
    @Test void releaseInventory_success() {
        var r = reserved(); service.releaseInventory(5001L);
        assertThat(stock.getTotalQuantity()).isEqualTo(10); assertThat(stock.getReservedQuantity()).isZero();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }
    @Test void releaseInventory_duplicateRequest() {
        reserved(); service.releaseInventory(5001L); service.releaseInventory(5001L);
        assertThat(stock.getReservedQuantity()).isZero(); assertThat(stock.getTotalQuantity()).isEqualTo(10);
    }
    @Test void confirmInventory_success() {
        var r = reserved(); service.confirmInventory(5001L);
        assertThat(stock.getTotalQuantity()).isEqualTo(8); assertThat(stock.getReservedQuantity()).isZero();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }
    @Test void confirmInventory_duplicateRequest() {
        reserved(); service.confirmInventory(5001L); service.confirmInventory(5001L);
        assertThat(stock.getTotalQuantity()).isEqualTo(8);
    }
    @Test void confirmAfterRelease_rejected() {
        reserved(); service.releaseInventory(5001L);
        assertThatThrownBy(() -> service.confirmInventory(5001L)).isInstanceOf(InvalidInventoryOperationException.class);
        assertThat(stock.getTotalQuantity()).isEqualTo(10);
    }
    @Test void completeUnknownOrder_rejected() {
        assertThatThrownBy(() -> service.confirmInventory(999L)).isInstanceOf(InvalidInventoryOperationException.class);
    }
    @Test void adjustInventory_success() {
        when(inventories.findByProductId(1L)).thenReturn(Optional.of(stock));
        assertThat(service.adjustInventory(1L, new InventoryAdjustmentRequest(50, "Delivery")).totalQuantity()).isEqualTo(60);
    }
    @Test void adjustInventory_invalidQuantity() {
        when(inventories.findByProductId(1L)).thenReturn(Optional.of(stock)); stock.reserve(2);
        assertThatThrownBy(() -> service.adjustInventory(1L, new InventoryAdjustmentRequest(-9, "Damage")))
            .isInstanceOf(InvalidInventoryOperationException.class);
        assertThat(stock.getTotalQuantity()).isEqualTo(10);
    }
    @Test void adjustInventory_overflowRejected() {
        Inventory large = new Inventory(1L, Integer.MAX_VALUE);
        assertThatThrownBy(() -> large.adjust(1)).isInstanceOf(InvalidInventoryOperationException.class);
    }
}

