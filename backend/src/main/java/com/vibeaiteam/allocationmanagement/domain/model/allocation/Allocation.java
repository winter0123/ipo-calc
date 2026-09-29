package com.vibeaiteam.allocationmanagement.domain.model.allocation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "allocations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Allocation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subscription_id", nullable = false)
  private Long subscriptionId;

  @Column(name = "allocated_quantity", nullable = false)
  private Long allocatedQuantity;

  @Column(name = "allocated_at", nullable = false)
  private LocalDateTime allocatedAt;

  @Column(name = "allocation_method")
  private String allocationMethod;

  @Column(name = "allocation_price")
  private Long allocationPrice;

  @Column(name = "note")
  private String note;

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
