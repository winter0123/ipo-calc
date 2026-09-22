package com.vibeaiteam.salemanagement.domain.model.sale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sales")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_id", nullable = false)
    private Long allocationId;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "price")
    private Long price;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "sale_date", nullable = false)
    private LocalDateTime saleDate;

    @Column(name = "fee")
    private Long fee;

    @Column(name = "tax")
    private Long tax;

    @Column(name = "other_cost")
    private Long otherCost;

    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}