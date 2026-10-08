package com.ecommerce.product.entity;

import com.ecommerce.product.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "inventory_reservations", uniqueConstraints = @UniqueConstraint(name = "uk_reservation_order_product", columnNames = {"order_id", "product_id"}))
@Check(constraints = "quantity > 0 and order_id > 0")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class InventoryReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false, updatable = false) private Long orderId;
    @Column(name = "product_id", nullable = false, updatable = false) private Long productId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_reservation_product"))
    private Product product;
    @Column(nullable = false, updatable = false) private Integer quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ReservationStatus status;
    @Version private Long version;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    public InventoryReservation(Long orderId, Long productId, int quantity) {
        this.orderId = orderId; this.productId = productId; this.quantity = quantity;
        this.status = ReservationStatus.RESERVED;
    }
    public void complete(ReservationStatus target) {
        if (status != ReservationStatus.RESERVED || target == ReservationStatus.RESERVED)
            throw new IllegalStateException("Invalid reservation transition");
        status = target;
    }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
}
