package com.careconnect.controller;

import com.careconnect.dto.AdherenceSummaryResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.AdherenceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/adherence")
public class AdherenceController {

    private final AdherenceService adherenceService;

    public AdherenceController(AdherenceService adherenceService) {
        this.adherenceService = adherenceService;
    }

    /**
     * Read-only. date is optional (default today, never in the future).
     * Parent: own summary. Child: add ?parentId= of an approved parent.
     */
    @GetMapping("/summary")
    public AdherenceSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser caller,
                                            @RequestParam(required = false) Long parentId,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return adherenceService.summary(caller.id(), caller.role(), parentId, date);
    }
}
