package com.careconnect.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Maps to `medications`. "Deleting" a medication only sets active=false so history is kept. */
@Entity
@Table(name = "medications")
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private User parent;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedicationType type;

    @Column(length = 50)
    private String strength;

    @Column(name = "dosage_per_dose", nullable = false, precision = 6, scale = 2)
    private BigDecimal dosagePerDose;

    @Enumerated(EnumType.STRING)
    @Column(name = "quantity_unit", nullable = false)
    private QuantityUnit quantityUnit;

    @Column(name = "frequency_label", nullable = false, length = 60)
    private String frequencyLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_instruction", nullable = false)
    private FoodInstruction foodInstruction;

    @Column(length = 500)
    private String notes;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Medication() { }

    public Medication(User parent, String name, MedicationType type, String strength, BigDecimal dosagePerDose,
                      String frequencyLabel, FoodInstruction foodInstruction, String notes,
                      LocalDate startDate, LocalDate endDate) {
        this.parent = parent;
        this.active = true;
        updateDetails(name, type, strength, dosagePerDose, frequencyLabel, foodInstruction, notes, startDate, endDate);
    }

    public void updateDetails(String name, MedicationType type, String strength, BigDecimal dosagePerDose,
                              String frequencyLabel, FoodInstruction foodInstruction, String notes,
                              LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.type = type;
        this.quantityUnit = type.getUnit();
        this.strength = strength;
        this.dosagePerDose = dosagePerDose;
        this.frequencyLabel = frequencyLabel;
        this.foodInstruction = foodInstruction;
        this.notes = notes;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void deactivate() {
        this.active = false;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getParent() { return parent; }
    public String getName() { return name; }
    public MedicationType getType() { return type; }
    public String getStrength() { return strength; }
    public BigDecimal getDosagePerDose() { return dosagePerDose; }
    public QuantityUnit getQuantityUnit() { return quantityUnit; }
    public String getFrequencyLabel() { return frequencyLabel; }
    public FoodInstruction getFoodInstruction() { return foodInstruction; }
    public String getNotes() { return notes; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
