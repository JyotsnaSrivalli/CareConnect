package com.careconnect.controller;

import com.careconnect.dto.StripResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.TabletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/strips")
public class StripController {

    private final TabletService tabletService;

    public StripController(TabletService tabletService) {
        this.tabletService = tabletService;
    }

    /** Parent: own strips. Child: add ?parentId= of an approved parent (read-only). */
    @GetMapping
    public List<StripResponse> strips(@AuthenticationPrincipal AuthenticatedUser caller,
                                      @RequestParam(required = false) Long parentId) {
        return tabletService.strips(caller.id(), caller.role(), parentId);
    }
}
