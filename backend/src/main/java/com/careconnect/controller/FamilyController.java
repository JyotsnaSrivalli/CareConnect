package com.careconnect.controller;

import com.careconnect.dto.ApproveRequest;
import com.careconnect.dto.ConnectRequest;
import com.careconnect.dto.ConnectionResponse;
import com.careconnect.dto.InviteResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping("/invite")                    // PARENT only (SecurityConfig)
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse invite(@AuthenticationPrincipal AuthenticatedUser caller) {
        return familyService.createInvitation(caller.id());
    }

    @PostMapping("/connect")                   // CHILD only
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionResponse connect(@AuthenticationPrincipal AuthenticatedUser caller,
                                      @Valid @RequestBody ConnectRequest request) {
        return familyService.connect(caller.id(), request.code());
    }

    @PostMapping("/approve")                   // PARENT only
    public ConnectionResponse approve(@AuthenticationPrincipal AuthenticatedUser caller,
                                      @Valid @RequestBody ApproveRequest request) {
        return familyService.decide(caller.id(), request);
    }

    @GetMapping("/connections")                // any logged-in user; returns only THEIR connections
    public List<ConnectionResponse> connections(@AuthenticationPrincipal AuthenticatedUser caller) {
        return familyService.listConnections(caller.id(), caller.role());
    }
}
