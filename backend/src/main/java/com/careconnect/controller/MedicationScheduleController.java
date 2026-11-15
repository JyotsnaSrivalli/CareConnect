package com.careconnect.controller;

import com.careconnect.dto.ScheduleRequest;
import com.careconnect.dto.ScheduleResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.MedicationScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medications/{medicationId}/schedules")
public class MedicationScheduleController {

    private final MedicationScheduleService scheduleService;

    public MedicationScheduleController(MedicationScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<ScheduleResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                       @PathVariable Long medicationId) {
        return scheduleService.list(caller.id(), caller.role(), medicationId);
    }

    @PostMapping                                   // PARENT only
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse add(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long medicationId,
                                @Valid @RequestBody ScheduleRequest request) {
        return scheduleService.add(caller.id(), medicationId, request);
    }

    @PutMapping("/{scheduleId}")                   // PARENT only
    public ScheduleResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long medicationId,
                                   @PathVariable Long scheduleId, @Valid @RequestBody ScheduleRequest request) {
        return scheduleService.update(caller.id(), medicationId, scheduleId, request);
    }

    @DeleteMapping("/{scheduleId}")                // PARENT only
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long medicationId,
                       @PathVariable Long scheduleId) {
        scheduleService.remove(caller.id(), medicationId, scheduleId);
    }
}
