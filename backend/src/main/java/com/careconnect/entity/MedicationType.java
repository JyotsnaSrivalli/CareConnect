package com.careconnect.entity;

public enum MedicationType {
    TABLET(QuantityUnit.TABLET),
    CAPSULE(QuantityUnit.CAPSULE),
    SYRUP(QuantityUnit.ML);

    private final QuantityUnit unit;

    MedicationType(QuantityUnit unit) {
        this.unit = unit;
    }

    /** The unit stock is counted in: tablets are counted in tablets, syrup in millilitres. */
    public QuantityUnit getUnit() { return unit; }

    /** Tablets and capsules are counted one by one on a blister strip. Syrup is measured in ml and has no strip. */
    public boolean usesBlisterStrip() { return this != SYRUP; }
}
