package com.ordering.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(name = "table_no", nullable = false, length = 10)
    private String tableNo;

    @com.fasterxml.jackson.annotation.JsonRawValue
    @Column(columnDefinition = "TEXT", nullable = false)
    private String items;         // JSON: [{name, spec, price, quantity, subtotal}]

    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Column(nullable = false, length = 20)
    private String status;        // CREATED / PREPARING / PENDING_CONFIRM / CONFIRMED / CLOSED / VERIFIED

    @com.fasterxml.jackson.annotation.JsonRawValue
    @Column(name = "confirm_detail", columnDefinition = "TEXT")
    private String confirmDetail;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
