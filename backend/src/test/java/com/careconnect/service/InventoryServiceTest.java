package com.careconnect.service;

import com.careconnect.dto.InventoryRequest;
import com.careconnect.dto.InventoryResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private MedicationInventoryRepository inventoryRepository;
    @Mock private MedicationService medicationService;
    @Mock private TabletService tabletService;

    private InventoryService service;
    private Medication med;
    private MedicationInventory inventory;   // 20 tablets, low at <= 5

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, medicationService, tabletService);
        User parent = new User("Asha", "asha@test.com", "hash", Role.PARENT);
        ReflectionTestUtils.setField(parent, "id", 1L);
        med = new Medication(parent, "Cough syrup", MedicationType.SYRUP, null, BigDecimal.ONE, "Twice daily",
                FoodInstruction.ANY_TIME, null, LocalDate.now(), null);
        ReflectionTestUtils.setField(med, "id", 10L);
        inventory = new MedicationInventory(med, bd("20"), bd("5"));
    }

    @Test
    void parentCanUpdateStock() {
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(inventoryRepository.findByMedicationId(10L)).thenReturn(Optional.of(inventory));

        InventoryResponse response = service.update(1L, 10L, new InventoryRequest(bd("12"), bd("5")));

        assertEquals(0, bd("12").compareTo(response.currentQuantity()));
        assertEquals(0, bd("20").compareTo(response.totalQuantity()));   // total unchanged
        assertFalse(response.lowStock());
        assertEquals(QuantityUnit.ML, response.unit());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void refillingAboveTotalRaisesTotal() {
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(inventoryRepository.findByMedicationId(10L)).thenReturn(Optional.of(inventory));

        InventoryResponse response = service.update(1L, 10L, new InventoryRequest(bd("30"), bd("5")));

        assertEquals(0, bd("30").compareTo(response.currentQuantity()));
        assertEquals(0, bd("30").compareTo(response.totalQuantity()));   // DB rule current <= total still holds
    }

    @Test
    void stockBecomesLowWhenQuantityReachesThreshold() {
        when(medicationService.requireOwned(1L, 10L)).thenReturn(med);
        when(inventoryRepository.findByMedicationId(10L)).thenReturn(Optional.of(inventory));

        assertTrue(service.update(1L, 10L, new InventoryRequest(bd("5"), bd("5"))).lowStock());   // equal = low
        assertFalse(service.update(1L, 10L, new InventoryRequest(bd("6"), bd("5"))).lowStock());
        assertTrue(service.update(1L, 10L, new InventoryRequest(bd("6"), bd("8"))).lowStock());   // threshold raised
    }

    @Test
    void parentCannotUpdateAnotherParentsStock() {
        when(medicationService.requireOwned(99L, 10L)).thenThrow(ApiException.notFound("Medication not found"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.update(99L, 10L, new InventoryRequest(bd("1"), bd("1"))));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void approvedChildCanViewStock() {
        when(medicationService.requireViewable(2L, Role.CHILD, 10L)).thenReturn(med);
        when(inventoryRepository.findByMedicationId(10L)).thenReturn(Optional.of(inventory));

        InventoryResponse response = service.get(2L, Role.CHILD, 10L);

        assertEquals(0, bd("20").compareTo(response.currentQuantity()));
    }

    @Test
    void childWithoutAccessCannotViewStock() {
        when(medicationService.requireViewable(2L, Role.CHILD, 10L)).thenThrow(ApiException.forbidden("no access"));

        ApiException ex = assertThrows(ApiException.class, () -> service.get(2L, Role.CHILD, 10L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // ---------- tablets / capsules: stock is the strip ----------
    @Test
    void tabletStockUpdateResizesTheStripThroughTabletService() {
        Medication tablet = new Medication(med.getParent(), "Paracetamol", MedicationType.TABLET, null, BigDecimal.ONE,
                "Twice daily", FoodInstruction.ANY_TIME, null, LocalDate.now(), null);
        ReflectionTestUtils.setField(tablet, "id", 11L);
        MedicationInventory strip = new MedicationInventory(tablet, bd("12"), bd("3"));
        when(medicationService.requireOwned(1L, 11L)).thenReturn(tablet);
        when(inventoryRepository.findByMedicationIdForUpdate(11L)).thenReturn(Optional.of(strip));
        doAnswer(call -> { strip.syncCounts(bd("15"), bd("15")); return null; })
                .when(tabletService).setStock(tablet, strip, bd("15"));

        InventoryResponse response = service.update(1L, 11L, new InventoryRequest(bd("15"), bd("4")));

        verify(tabletService).setStock(tablet, strip, bd("15"));
        assertEquals(0, bd("15").compareTo(response.currentQuantity()));
        assertEquals(0, bd("4").compareTo(response.lowStockThreshold()));
        assertEquals(QuantityUnit.TABLET, response.unit());
        verify(inventoryRepository, never()).findByMedicationId(any());   // used the locking lookup instead
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
