package com.careconnect.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Maps to `medication_tablets`. One row = ONE physical tablet/capsule = ONE compartment on the blister strip.
 * Taking it records the date and time; undo makes the very same compartment available again.
 */
@Entity
@Table(name = "medication_tablets",
        uniqueConstraints = @UniqueConstraint(name = "uq_tablet_slot", columnNames = {"medication_id", "slot_number"}))
public class MedicationTablet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TabletStatus status;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    protected MedicationTablet() { }

    public MedicationTablet(Medication medication, int slotNumber) {
        this.medication = medication;
        this.slotNumber = slotNumber;
        this.status = TabletStatus.AVAILABLE;
    }

    public void take(LocalDateTime now) {
        this.status = TabletStatus.TAKEN;
        this.takenAt = now;
    }

    public void undo() {
        this.status = TabletStatus.AVAILABLE;
        this.takenAt = null;
    }

    public boolean isTaken() {
        return status == TabletStatus.TAKEN;
    }

    public Long getId() { return id; }
    public Medication getMedication() { return medication; }
    public int getSlotNumber() { return slotNumber; }
    public TabletStatus getStatus() { return status; }
    public LocalDateTime getTakenAt() { return takenAt; }
}
