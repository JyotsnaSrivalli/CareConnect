package com.careconnect.controller;

import com.careconnect.dto.InventoryRequest;
import com.careconnect.dto.InventoryResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medications/{medicationId}/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public InventoryResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long medicationId) {
        return inventoryService.get(caller.id(), caller.role(), medicationId);
    }

    @PutMapping                                    // PARENT only
    public InventoryResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long medicationId,
                                    @Valid @RequestBody InventoryRequest request) {
        return inventoryService.update(caller.id(), medicationId, request);
    }
}
