package com.careconnect.service;

import com.careconnect.dto.CheckinRequest;
import com.careconnect.dto.CheckinResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.DailyCheckinRepository;
import com.careconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @Mock private DailyCheckinRepository checkinRepository;
    @Mock private UserRepository userRepository;
    @Mock private FamilyService familyService;

    private CheckinService service;
    private User parent;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneId.of("UTC"));
        service = new CheckinService(checkinRepository, userRepository, familyService, clock);
        parent = user(1L, "Asha", Role.PARENT);
    }

    // ---------- creating ----------
    @Test
    void parentCreatesNewCheckinAndGets201Semantics() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(checkinRepository.findByParentIdAndCheckinDate(1L, TODAY)).thenReturn(Optional.empty());
        when(checkinRepository.save(any(DailyCheckin.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckinService.SaveResult result = service.save(1L, Role.PARENT,
                new CheckinRequest(TODAY, Feeling.GOOD, EnergyLevel.HIGH, "  mild cough ", "slept well"));

        assertTrue(result.created());
        assertEquals(Feeling.GOOD, result.checkin().feeling());
        assertEquals(EnergyLevel.HIGH, result.checkin().energyLevel());
        assertEquals("mild cough", result.checkin().symptoms());   // trimmed
        assertEquals("slept well", result.checkin().notes());
        assertEquals(TODAY, result.checkin().date());
    }

    @Test
    void blankSymptomsAndNotesAreStoredAsNothing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(checkinRepository.findByParentIdAndCheckinDate(1L, TODAY)).thenReturn(Optional.empty());
        when(checkinRepository.save(any(DailyCheckin.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckinResponse saved = service.save(1L, Role.PARENT,
                new CheckinRequest(TODAY, Feeling.OKAY, EnergyLevel.MODERATE, "   ", null)).checkin();

        assertNull(saved.symptoms());
        assertNull(saved.notes());
    }

    // ---------- updating / no duplicates ----------
    @Test
    void savingAgainForTheSameDateUpdatesTheExistingRowInsteadOfCreatingAnother() {
        DailyCheckin existing = new DailyCheckin(parent, TODAY, Feeling.GOOD, EnergyLevel.HIGH, null, "first");
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(checkinRepository.findByParentIdAndCheckinDate(1L, TODAY)).thenReturn(Optional.of(existing));
        when(checkinRepository.save(any(DailyCheckin.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckinService.SaveResult result = service.save(1L, Role.PARENT,
                new CheckinRequest(TODAY, Feeling.NOT_GOOD, EnergyLevel.LOW, "headache", "second"));

        assertFalse(result.created());
        // the SAME object was changed and saved: no second row
        ArgumentCaptor<DailyCheckin> saved = ArgumentCaptor.forClass(DailyCheckin.class);
        verify(checkinRepository, times(1)).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertEquals(Feeling.NOT_GOOD, existing.getFeeling());
        assertEquals(EnergyLevel.LOW, existing.getEnergyLevel());
        assertEquals("headache", existing.getSymptoms());
        assertEquals("second", existing.getNotes());
    }

    @Test
    void theDuplicateLookupUsesTheLoggedInParentsIdAndTheRequestedDate() {
        LocalDate earlier = TODAY.minusDays(3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(checkinRepository.findByParentIdAndCheckinDate(1L, earlier)).thenReturn(Optional.empty());
        when(checkinRepository.save(any(DailyCheckin.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save(1L, Role.PARENT, new CheckinRequest(earlier, Feeling.OKAY, EnergyLevel.LOW, null, null));

        verify(checkinRepository).findByParentIdAndCheckinDate(1L, earlier);
    }

    // ---------- validation ----------
    @Test
    void futureDateIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.save(1L, Role.PARENT,
                new CheckinRequest(TODAY.plusDays(1), Feeling.GOOD, EnergyLevel.HIGH, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(checkinRepository, never()).save(any());
    }

    @Test
    void oldStyleFeelingValueIsRejectedForNewCheckins() {
        ApiException ex = assertThrows(ApiException.class, () -> service.save(1L, Role.PARENT,
                new CheckinRequest(TODAY, Feeling.VERY_GOOD, EnergyLevel.HIGH, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(checkinRepository, never()).save(any());
    }

    @Test
    void tokenOfADeletedUserIsUnauthorized() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.save(1L, Role.PARENT,
                new CheckinRequest(TODAY, Feeling.GOOD, EnergyLevel.HIGH, null, null)));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    // ---------- child can never write ----------
    @Test
    void childCannotSaveACheckinEvenIfTheSecurityRuleWereMissing() {
        ApiException ex = assertThrows(ApiException.class, () -> service.save(2L, Role.CHILD,
                new CheckinRequest(TODAY, Feeling.GOOD, EnergyLevel.HIGH, null, null)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(checkinRepository);
    }

    // ---------- history: parent ----------
    @Test
    void parentHistoryIsLoadedForTheParentsOwnIdNewestFirst() {
        DailyCheckin newer = new DailyCheckin(parent, TODAY, Feeling.GOOD, EnergyLevel.HIGH, null, null);
        DailyCheckin older = new DailyCheckin(parent, TODAY.minusDays(1), Feeling.OKAY, EnergyLevel.LOW, null, null);
        when(checkinRepository.findByParentIdOrderByCheckinDateDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(newer, older));

        List<CheckinResponse> history = service.history(1L, Role.PARENT, null, null);

        assertEquals(2, history.size());
        assertEquals(TODAY, history.get(0).date());
        assertEquals(TODAY.minusDays(1), history.get(1).date());
    }

    @Test
    void parentCannotReadAnotherParentsCheckinsByPassingTheirId() {
        when(checkinRepository.findByParentIdOrderByCheckinDateDesc(eq(1L), any(Pageable.class))).thenReturn(List.of());

        service.history(1L, Role.PARENT, 99L, null);   // 99 is somebody else's id

        verify(checkinRepository).findByParentIdOrderByCheckinDateDesc(eq(1L), any(Pageable.class));
        verify(checkinRepository, never()).findByParentIdOrderByCheckinDateDesc(eq(99L), any(Pageable.class));
        verifyNoInteractions(familyService);
    }

    @Test
    void historyUsesDefaultLimitOf30AndRejectsSillyLimits() {
        when(checkinRepository.findByParentIdOrderByCheckinDateDesc(eq(1L), any(Pageable.class))).thenReturn(List.of());
        service.history(1L, Role.PARENT, null, null);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(checkinRepository).findByParentIdOrderByCheckinDateDesc(eq(1L), page.capture());
        assertEquals(30, page.getValue().getPageSize());

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.history(1L, Role.PARENT, null, 0)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.history(1L, Role.PARENT, null, 101)).getStatus());
    }

    // ---------- history: child ----------
    @Test
    void approvedChildWhoIsAllowedToSeeCheckinsGetsTheParentsHistory() {
        DailyCheckin one = new DailyCheckin(parent, TODAY, Feeling.GOOD, EnergyLevel.HIGH, null, null);
        doNothing().when(familyService).assertChildCanViewCheckins(2L, 1L);
        when(checkinRepository.findByParentIdOrderByCheckinDateDesc(eq(1L), any(Pageable.class))).thenReturn(List.of(one));

        List<CheckinResponse> history = service.history(2L, Role.CHILD, 1L, null);

        assertEquals(1, history.size());
        verify(familyService).assertChildCanViewCheckins(2L, 1L);
    }

    @Test
    void childWithoutApprovalOrWithRevokedAccessOrWithoutSharingIsForbiddenAndNoDataIsLoaded() {
        // FamilyService throws 403 for: no link, PENDING, REJECTED/revoked, or share_checkins = false
        doThrow(ApiException.forbidden("no access")).when(familyService).assertChildCanViewCheckins(2L, 1L);

        ApiException ex = assertThrows(ApiException.class, () -> service.history(2L, Role.CHILD, 1L, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(checkinRepository);
    }

    @Test
    void childMustSayWhichParent() {
        ApiException ex = assertThrows(ApiException.class, () -> service.history(2L, Role.CHILD, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(checkinRepository);
    }

    // ---------- one date ----------
    @Test
    void forDateReturnsTheCheckinWhenItExists() {
        DailyCheckin c = new DailyCheckin(parent, TODAY, Feeling.OKAY, EnergyLevel.MODERATE, null, "ok");
        when(checkinRepository.findByParentIdAndCheckinDate(1L, TODAY)).thenReturn(Optional.of(c));

        assertEquals(Feeling.OKAY, service.forDate(1L, Role.PARENT, null, TODAY).feeling());
    }

    @Test
    void forDateIs404WhenThereIsNoCheckin() {
        when(checkinRepository.findByParentIdAndCheckinDate(1L, TODAY)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.forDate(1L, Role.PARENT, null, TODAY));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void forDateIsForbiddenForAChildWithoutAccess() {
        doThrow(ApiException.forbidden("no access")).when(familyService).assertChildCanViewCheckins(2L, 1L);

        ApiException ex = assertThrows(ApiException.class, () -> service.forDate(2L, Role.CHILD, 1L, TODAY));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(checkinRepository);
    }

    // ---------- rows written before Day 5 ----------
    @Test
    void oldRowWithoutEnergyLevelOrUpdatedAtStillReadsFine() {
        DailyCheckin legacy = new DailyCheckin(parent, TODAY.minusDays(9), Feeling.FEELING_UNWELL, null, null, "old note");
        LocalDateTime created = LocalDateTime.of(2026, 11, 3, 8, 0);
        ReflectionTestUtils.setField(legacy, "createdAt", created);
        when(checkinRepository.findByParentIdOrderByCheckinDateDesc(eq(1L), any(Pageable.class))).thenReturn(List.of(legacy));

        CheckinResponse r = service.history(1L, Role.PARENT, null, null).get(0);

        assertEquals(Feeling.FEELING_UNWELL, r.feeling());
        assertNull(r.energyLevel());
        assertEquals(created, r.updatedAt());   // falls back to created_at
    }

    // ---------- helpers ----------
    private User user(long id, String name, Role role) {
        User u = new User(name, name.toLowerCase() + "@test.com", "hash", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
