package com.careconnect.controller;

import com.careconnect.dto.MedicationRequest;
import com.careconnect.dto.MedicationResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.MedicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    @PostMapping                                   // PARENT only (SecurityConfig)
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
                                     @Valid @RequestBody MedicationRequest request) {
        return medicationService.create(caller.id(), request);
    }

    /** Parent: own medications. Child: pass ?parentId= of an approved parent. */
    @GetMapping
    public List<MedicationResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                         @RequestParam(required = false) Long parentId) {
        return medicationService.list(caller.id(), caller.role(), parentId);
    }

    @GetMapping("/{id}")
    public MedicationResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return medicationService.get(caller.id(), caller.role(), id);
    }

    @PutMapping("/{id}")                           // PARENT only
    public MedicationResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id,
                                     @Valid @RequestBody MedicationRequest request) {
        return medicationService.update(caller.id(), id, request);
    }

    @DeleteMapping("/{id}")                        // PARENT only
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        medicationService.delete(caller.id(), id);
    }
}
