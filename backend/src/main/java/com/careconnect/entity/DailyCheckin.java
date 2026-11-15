package com.careconnect.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to `daily_checkins`: at most ONE row per (parent, date), enforced by the unique constraint
 * uq_checkin_day. Saving a check-in for a date that already has one updates that row.
 *
 * The old column `took_medicines` is deliberately NOT mapped here. Hibernate then never reads or
 * writes it, so old values stay untouched and new rows simply leave it empty (the Day 5 migration
 * made it nullable). Confirmed adherence comes from the Day 4 tablet / dose records instead.
 */
@Entity
@Table(name = "daily_checkins",
        uniqueConstraints = @UniqueConstraint(name = "uq_checkin_day", columnNames = {"parent_id", "checkin_date"}))
public class DailyCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private User parent;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Feeling feeling;

    /** Null only for rows written before Day 5. The API requires it for every new check-in. */
    @Enumerated(EnumType.STRING)
    @Column(name = "energy_level")
    private EnergyLevel energyLevel;

    @Column(length = 500)
    private String symptoms;

    /** The column keeps its old name `comments`; in the app it is called "notes". */
    @Column(name = "comments", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected DailyCheckin() { }

    public DailyCheckin(User parent, LocalDate checkinDate, Feeling feeling, EnergyLevel energyLevel,
                        String symptoms, String notes) {
        this.parent = parent;
        this.checkinDate = checkinDate;
        this.feeling = feeling;
        this.energyLevel = energyLevel;
        this.symptoms = symptoms;
        this.notes = notes;
    }

    /** Used when a check-in already exists for that date: change the answers, keep the same row. */
    public void update(Feeling feeling, EnergyLevel energyLevel, String symptoms, String notes) {
        this.feeling = feeling;
        this.energyLevel = energyLevel;
        this.symptoms = symptoms;
        this.notes = notes;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getParent() { return parent; }
    public LocalDate getCheckinDate() { return checkinDate; }
    public Feeling getFeeling() { return feeling; }
    public EnergyLevel getEnergyLevel() { return energyLevel; }
    public String getSymptoms() { return symptoms; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
