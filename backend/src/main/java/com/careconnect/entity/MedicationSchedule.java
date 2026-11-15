package com.careconnect.entity;

import jakarta.persistence.*;

import java.time.LocalTime;

/** Maps to `medication_schedules`: one planned dose time per row (08:00, 20:00, ...). */
@Entity
@Table(name = "medication_schedules",
        uniqueConstraints = @UniqueConstraint(name = "uq_sched", columnNames = {"medication_id", "dose_time"}))
public class MedicationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "dose_time", nullable = false)
    private LocalTime doseTime;

    /** false = removed by the parent. Kept in the table so old dose logs still point at it. */
    @Column(nullable = false)
    private boolean active;

    protected MedicationSchedule() { }

    public MedicationSchedule(Medication medication, LocalTime doseTime) {
        this.medication = medication;
        this.doseTime = doseTime;
        this.active = true;
    }

    public void changeTime(LocalTime doseTime) {
        this.doseTime = doseTime;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public Long getId() { return id; }
    public Medication getMedication() { return medication; }
    public LocalTime getDoseTime() { return doseTime; }
    public boolean isActive() { return active; }
}
