package com.careconnect.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MedicationInventoryTest {

    @ParameterizedTest(name = "current={0}, threshold={1} -> low={2}")
    @CsvSource({
            "20, 5, false",
            "6,  5, false",
            "5,  5, true",     // reaching the threshold counts as low
            "4,  5, true",
            "0,  0, true",     // empty box is always low
            "1,  0, false"
    })
    void lowStockDetection(String current, String threshold, boolean expectedLow) {
        MedicationInventory inventory = new MedicationInventory(null, new BigDecimal("100"), new BigDecimal(threshold));
        inventory.updateStock(new BigDecimal(current), new BigDecimal(threshold));

        assertEquals(expectedLow, inventory.isLowStock());
    }

    @Test
    void deductReducesStock() {
        MedicationInventory inv = new MedicationInventory(null, bd("20"), bd("5"));
        inv.deduct(bd("1"));
        assertEquals(0, bd("19").compareTo(inv.getCurrentQuantity()));
    }

    @Test
    void stockCanNeverGoNegative() {
        MedicationInventory inv = new MedicationInventory(null, bd("1"), bd("0"));
        assertThrows(IllegalArgumentException.class, () -> inv.deduct(bd("2")));
        assertEquals(0, bd("1").compareTo(inv.getCurrentQuantity()));   // unchanged after the refused deduction
    }

    @Test
    void deductingExactlyWhatIsLeftGivesZero() {
        MedicationInventory inv = new MedicationInventory(null, bd("1"), bd("0"));
        inv.deduct(bd("1"));
        assertEquals(0, BigDecimal.ZERO.compareTo(inv.getCurrentQuantity()));
    }

    @Test
    void restoreGivesStockBack() {
        MedicationInventory inv = new MedicationInventory(null, bd("20"), bd("5"));
        inv.deduct(bd("1"));
        inv.restore(bd("1"));
        assertEquals(0, bd("20").compareTo(inv.getCurrentQuantity()));
        assertEquals(0, bd("20").compareTo(inv.getTotalQuantity()));
    }

    @Test
    void restoreAboveTotalRaisesTotalSoTheDatabaseRuleHolds() {
        MedicationInventory inv = new MedicationInventory(null, bd("20"), bd("5"));   // current 20 = total 20
        inv.restore(bd("1"));
        assertEquals(0, bd("21").compareTo(inv.getCurrentQuantity()));
        assertEquals(0, bd("21").compareTo(inv.getTotalQuantity()));
    }

    @Test
    void syncCountsSetsCurrentAndTotalDirectly() {
        MedicationInventory inv = new MedicationInventory(null, bd("12"), bd("3"));
        inv.syncCounts(bd("5"), bd("8"));
        assertEquals(0, bd("5").compareTo(inv.getCurrentQuantity()));
        assertEquals(0, bd("8").compareTo(inv.getTotalQuantity()));
    }

    @Test
    void thresholdCanBeChangedWithoutTouchingStock() {
        MedicationInventory inv = new MedicationInventory(null, bd("12"), bd("3"));
        inv.changeThreshold(bd("12"));
        assertTrue(inv.isLowStock());
        assertEquals(0, bd("12").compareTo(inv.getCurrentQuantity()));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
