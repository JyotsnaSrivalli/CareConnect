package com.careconnect.controller;

import com.careconnect.dto.DoseResponse;
import com.careconnect.dto.SummaryResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.MedicationLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/medication-logs")
public class MedicationLogController {

    private final MedicationLogService logService;

    public MedicationLogController(MedicationLogService logService) {
        this.logService = logService;
    }

    /** Parent: own doses today. Child: add ?parentId= of an approved parent (read-only). */
    @GetMapping("/today")
    public List<DoseResponse> today(@AuthenticationPrincipal AuthenticatedUser caller,
                                    @RequestParam(required = false) Long parentId) {
        return logService.doses(caller.id(), caller.role(), parentId, null);
    }

    /** Doses for a past day (date defaults to today). */
    @GetMapping
    public List<DoseResponse> byDate(@AuthenticationPrincipal AuthenticatedUser caller,
                                     @RequestParam(required = false) Long parentId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return logService.doses(caller.id(), caller.role(), parentId, date);
    }

    @GetMapping("/summary")
    public SummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser caller,
                                   @RequestParam(required = false) Long parentId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return logService.summary(caller.id(), caller.role(), parentId, date);
    }

    @PostMapping("/{scheduleId}/taken")             // PARENT only (SecurityConfig)
    @ResponseStatus(HttpStatus.CREATED)
    public DoseResponse markTaken(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long scheduleId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return logService.markTaken(caller.id(), scheduleId, date);
    }

    /** Undo: keeps the log row (status UNDONE), restores stock. Returns the dose, now PENDING. */
    @DeleteMapping("/{logId}")                      // PARENT only
    public DoseResponse undo(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long logId) {
        return logService.undo(caller.id(), logId);
    }
}
