package com.careconnect.service;

import com.careconnect.dto.CheckinRequest;
import com.careconnect.dto.CheckinResponse;
import com.careconnect.entity.DailyCheckin;
import com.careconnect.entity.Role;
import com.careconnect.entity.User;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.DailyCheckinRepository;
import com.careconnect.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Daily well-being check-in. Rules enforced here (not in the controller, not in the browser):
 *  - only a PARENT can save a check-in, and only for themselves (the id comes from the JWT)
 *  - one check-in per parent per date: saving again for the same date UPDATES it
 *  - a child can only READ, and only when the connection is APPROVED *and* the parent chose to share check-ins
 */
@Service
public class CheckinService {

    public static final int DEFAULT_LIMIT = 30;
    public static final int MAX_LIMIT = 100;

    /** "created" lets the controller answer 201 (new) or 200 (updated). */
    public record SaveResult(CheckinResponse checkin, boolean created) { }

    private final DailyCheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final FamilyService familyService;
    private final Clock clock;

    public CheckinService(DailyCheckinRepository checkinRepository, UserRepository userRepository,
                          FamilyService familyService, Clock clock) {
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
        this.familyService = familyService;
        this.clock = clock;
    }

    // ================= writing (parent only) =================

    @Transactional
    public SaveResult save(Long callerId, Role role, CheckinRequest request) {
        if (role != Role.PARENT) {
            throw ApiException.forbidden("Only a parent can save a check-in");
        }
        if (request.feeling().isLegacy()) {
            throw ApiException.badRequest("Overall feeling must be GOOD, OKAY or NOT_GOOD");
        }
        if (request.date().isAfter(LocalDate.now(clock))) {
            throw ApiException.badRequest("Date cannot be in the future");
        }
        User parent = userRepository.findById(callerId)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));

        String symptoms = clean(request.symptoms());
        String notes = clean(request.notes());

        // The parent id in this lookup comes from the JWT, so a parent can only ever touch their own rows.
        Optional<DailyCheckin> existing = checkinRepository.findByParentIdAndCheckinDate(callerId, request.date());
        if (existing.isPresent()) {
            DailyCheckin checkin = existing.get();
            checkin.update(request.feeling(), request.energyLevel(), symptoms, notes);
            return new SaveResult(CheckinResponse.from(checkinRepository.save(checkin)), false);
        }
        DailyCheckin created = new DailyCheckin(parent, request.date(), request.feeling(),
                request.energyLevel(), symptoms, notes);
        return new SaveResult(CheckinResponse.from(checkinRepository.save(created)), true);
    }

    // ================= reading (parent: own, child: approved + shared) =================

    @Transactional(readOnly = true)
    public List<CheckinResponse> history(Long callerId, Role role, Long parentId, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiException.badRequest("limit must be between 1 and " + MAX_LIMIT);
        }
        Long ownerId = resolveOwner(callerId, role, parentId);
        return checkinRepository.findByParentIdOrderByCheckinDateDesc(ownerId, PageRequest.of(0, size))
                .stream().map(CheckinResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CheckinResponse forDate(Long callerId, Role role, Long parentId, LocalDate date) {
        Long ownerId = resolveOwner(callerId, role, parentId);
        return checkinRepository.findByParentIdAndCheckinDate(ownerId, date)
                .map(CheckinResponse::from)
                .orElseThrow(() -> ApiException.notFound("No check-in for " + date));
    }

    // ================= helpers =================

    /** Parent: always themselves. Child: must name a parent, be APPROVED, and the parent must share check-ins. */
    private Long resolveOwner(Long callerId, Role role, Long parentId) {
        if (role == Role.PARENT) {
            return callerId;
        }
        if (parentId == null) {
            throw ApiException.badRequest("parentId is required");
        }
        familyService.assertChildCanViewCheckins(callerId, parentId);
        return parentId;
    }

    /** Trim spaces; an empty text is stored as "nothing" (null), not as an empty string. */
    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
