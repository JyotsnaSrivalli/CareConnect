package com.careconnect.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to `medication_inventory`: PHYSICAL stock only (how many tablets are in the box).
 * It knows nothing about doses taken; that is tracked separately in medication_logs (Day 4).
 */
@Entity
@Table(name = "medication_inventory")
public class MedicationInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false, unique = true)
    private Medication medication;

    @Column(name = "total_quantity", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalQuantity;

    @Column(name = "remaining_quantity", nullable = false, precision = 8, scale = 2)
    private BigDecimal currentQuantity;

    @Column(name = "low_stock_threshold", nullable = false, precision = 8, scale = 2)
    private BigDecimal lowStockThreshold;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MedicationInventory() { }

    public MedicationInventory(Medication medication, BigDecimal initialQuantity, BigDecimal lowStockThreshold) {
        this.medication = medication;
        this.totalQuantity = initialQuantity;
        this.currentQuantity = initialQuantity;
        this.lowStockThreshold = lowStockThreshold;
    }

    /**
     * Sets the quantity currently in the box. The database requires current <= total,
     * so if the parent enters more than the old total (a refill), total grows to match.
     */
    public void updateStock(BigDecimal currentQuantity, BigDecimal lowStockThreshold) {
        if (currentQuantity.compareTo(totalQuantity) > 0) {
            this.totalQuantity = currentQuantity;
        }
        this.currentQuantity = currentQuantity;
        this.lowStockThreshold = lowStockThreshold;
    }

    /** Strip medicines: stock is always the number of AVAILABLE compartments, total the number of compartments. */
    public void syncCounts(BigDecimal currentQuantity, BigDecimal totalQuantity) {
        this.currentQuantity = currentQuantity;
        this.totalQuantity = totalQuantity;
    }

    public void changeThreshold(BigDecimal lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean hasAtLeast(BigDecimal quantity) {
        return currentQuantity.compareTo(quantity) >= 0;
    }

    /** Takes stock out for a confirmed dose. Never lets the quantity go below zero. */
    public void deduct(BigDecimal quantity) {
        if (!hasAtLeast(quantity)) {
            throw new IllegalArgumentException("Cannot deduct " + quantity + " from " + currentQuantity);
        }
        this.currentQuantity = currentQuantity.subtract(quantity);
    }

    /** Gives back stock when a dose is undone. Raises the total if needed (DB rule: current <= total). */
    public void restore(BigDecimal quantity) {
        this.currentQuantity = currentQuantity.add(quantity);
        if (currentQuantity.compareTo(totalQuantity) > 0) {
            this.totalQuantity = currentQuantity;
        }
    }

    /** "Low" as soon as the quantity reaches the threshold (current <= threshold). */
    public boolean isLowStock() {
        return currentQuantity.compareTo(lowStockThreshold) <= 0;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Medication getMedication() { return medication; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public BigDecimal getCurrentQuantity() { return currentQuantity; }
    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
