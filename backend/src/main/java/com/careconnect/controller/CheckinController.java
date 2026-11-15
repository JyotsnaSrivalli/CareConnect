package com.careconnect.controller;

import com.careconnect.dto.CheckinRequest;
import com.careconnect.dto.CheckinResponse;
import com.careconnect.security.AuthenticatedUser;
import com.careconnect.service.CheckinService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/checkins")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    /** Parent only. 201 when a new check-in is created, 200 when the existing one for that date is updated. */
    @PostMapping
    public ResponseEntity<CheckinResponse> save(@AuthenticationPrincipal AuthenticatedUser caller,
                                                @Valid @RequestBody CheckinRequest request) {
        CheckinService.SaveResult result = checkinService.save(caller.id(), caller.role(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.checkin());
    }

    /** History, newest first. Parent: own. Child: add ?parentId= (approved parent who shares check-ins). */
    @GetMapping
    public List<CheckinResponse> history(@AuthenticationPrincipal AuthenticatedUser caller,
                                         @RequestParam(required = false) Long parentId,
                                         @RequestParam(required = false) Integer limit) {
        return checkinService.history(caller.id(), caller.role(), parentId, limit);
    }

    /** One day's check-in (404 if there is none). Used to pre-fill the form when the parent picks a date. */
    @GetMapping("/{date}")
    public CheckinResponse forDate(@AuthenticationPrincipal AuthenticatedUser caller,
                                   @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) Long parentId) {
        return checkinService.forDate(caller.id(), caller.role(), parentId, date);
    }
}
