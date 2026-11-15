package com.careconnect.controller;

import com.careconnect.dto.StripResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.TabletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tablets")
public class TabletController {

    private final TabletService tabletService;

    public TabletController(TabletService tabletService) {
        this.tabletService = tabletService;
    }

    /** Marks ONE compartment as taken. PARENT only (SecurityConfig). Returns the updated strip. */
    @PostMapping("/{tabletId}/take")
    public StripResponse take(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long tabletId) {
        return tabletService.take(caller.id(), tabletId);
    }

    /** Makes that exact compartment available again and gives one unit of stock back. PARENT only. */
    @PostMapping("/{tabletId}/undo")
    public StripResponse undo(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long tabletId) {
        return tabletService.undo(caller.id(), tabletId);
    }
}
