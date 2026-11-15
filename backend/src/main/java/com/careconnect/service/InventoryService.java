package com.careconnect.service;

import com.careconnect.dto.InventoryRequest;
import com.careconnect.dto.InventoryResponse;
import com.careconnect.entity.Medication;
import com.careconnect.entity.MedicationInventory;
import com.careconnect.entity.Role;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.MedicationInventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Physical stock only. Nothing here knows about doses taken (that is Day 4, separate table). */
@Service
public class InventoryService {

    private final MedicationInventoryRepository inventoryRepository;
    private final MedicationService medicationService;
    private final TabletService tabletService;

    public InventoryService(MedicationInventoryRepository inventoryRepository, MedicationService medicationService,
                            TabletService tabletService) {
        this.inventoryRepository = inventoryRepository;
        this.medicationService = medicationService;
        this.tabletService = tabletService;
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(Long userId, Role role, Long medicationId) {
        Medication medication = medicationService.requireViewable(userId, role, medicationId);
        return InventoryResponse.from(findInventory(medication));
    }

    @Transactional
    public InventoryResponse update(Long parentId, Long medicationId, InventoryRequest request) {
        Medication medication = medicationService.requireOwned(parentId, medicationId);

        if (medication.getType().usesBlisterStrip()) {
            // Tablets/capsules: stock IS the number of available compartments, so the strip is resized with it.
            // Lock the stock row first so this cannot run at the same time as a tablet being taken.
            MedicationInventory inventory = inventoryRepository.findByMedicationIdForUpdate(medication.getId())
                    .orElseThrow(() -> ApiException.notFound("Inventory not found"));
            tabletService.setStock(medication, inventory, request.currentQuantity());
            inventory.changeThreshold(request.lowStockThreshold());
            inventoryRepository.save(inventory);
            return InventoryResponse.from(inventory);
        }

        MedicationInventory inventory = findInventory(medication);
        inventory.updateStock(request.currentQuantity(), request.lowStockThreshold());
        inventoryRepository.save(inventory);
        return InventoryResponse.from(inventory);
    }

    private MedicationInventory findInventory(Medication medication) {
        return inventoryRepository.findByMedicationId(medication.getId())
                .orElseThrow(() -> ApiException.notFound("Inventory not found"));
    }
}
