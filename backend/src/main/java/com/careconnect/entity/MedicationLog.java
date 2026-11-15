package com.careconnect.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to `medication_logs`: one row per (schedule, date) that the parent has confirmed.
 * No row means "pending". Undo keeps the row and flips status to UNDONE (history is never deleted).
 */
@Entity
@Table(name = "medication_logs",
        uniqueConstraints = @UniqueConstraint(name = "uq_log_dose", columnNames = {"schedule_id", "scheduled_date"}))
public class MedicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private MedicationSchedule schedule;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogStatus status;

    /** Exactly what this dose took out of stock; undo gives back this amount. */
    @Column(name = "quantity_deducted", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantityDeducted;

    @Column(name = "taken_at", nullable = false)
    private LocalDateTime takenAt;

    @Column(name = "undone_at")
    private LocalDateTime undoneAt;

    protected MedicationLog() { }

    public MedicationLog(Medication medication, MedicationSchedule schedule, LocalDate scheduledDate,
                         BigDecimal quantityDeducted, LocalDateTime takenAt) {
        this.medication = medication;
        this.schedule = schedule;
        this.scheduledDate = scheduledDate;
        this.status = LogStatus.TAKEN;
        this.quantityDeducted = quantityDeducted;
        this.takenAt = takenAt;
    }

    public void markUndone(LocalDateTime now) {
        this.status = LogStatus.UNDONE;
        this.undoneAt = now;
    }

    /**
     * Confirming again after an undo reuses the same row (the unique key allows only one row per dose).
     * Note: this overwrites the earlier undo timestamp.
     */
    public void retake(BigDecimal quantityDeducted, LocalDateTime takenAt) {
        this.status = LogStatus.TAKEN;
        this.quantityDeducted = quantityDeducted;
        this.takenAt = takenAt;
        this.undoneAt = null;
    }

    public Long getId() { return id; }
    public Medication getMedication() { return medication; }
    public MedicationSchedule getSchedule() { return schedule; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LogStatus getStatus() { return status; }
    public BigDecimal getQuantityDeducted() { return quantityDeducted; }
    public LocalDateTime getTakenAt() { return takenAt; }
    public LocalDateTime getUndoneAt() { return undoneAt; }
}
