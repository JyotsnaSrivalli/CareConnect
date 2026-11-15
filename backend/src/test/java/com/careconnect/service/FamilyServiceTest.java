package com.careconnect.service;

import com.careconnect.dto.ApproveRequest;
import com.careconnect.dto.ConnectionResponse;
import com.careconnect.dto.InviteResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.FamilyConnectionRepository;
import com.careconnect.repository.InvitationTokenRepository;
import com.careconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private InvitationTokenRepository tokenRepository;
    @Mock private FamilyConnectionRepository connectionRepository;

    private FamilyService service;
    private User parent;
    private User child;

    @BeforeEach
    void setUp() {
        service = new FamilyService(userRepository, tokenRepository, connectionRepository);
        parent = user(1L, "Asha", Role.PARENT);
        child = user(2L, "Ravi", Role.CHILD);
    }

    // ---------- invitation ----------
    @Test
    void childCannotGenerateInvitation() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(child));

        ApiException ex = assertThrows(ApiException.class, () -> service.createInvitation(2L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void parentGetsEightCharacterCodeValidForAboutOneDay() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(tokenRepository.existsByCode(anyString())).thenReturn(false);
        when(tokenRepository.save(any(InvitationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        InviteResponse response = service.createInvitation(1L);

        assertEquals(8, response.code().length());
        assertTrue(response.expiresAt().isAfter(LocalDateTime.now().plusHours(23)));
    }

    // ---------- connect ----------
    @Test
    void unknownCodeIsNotFound() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(child));
        when(tokenRepository.findByCode("ZZZZZZZZ")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.connect(2L, "zzzzzzzz"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void expiredCodeIsBadRequest() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(child));
        InvitationToken expired = new InvitationToken(parent, "ABCD2345", LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByCode("ABCD2345")).thenReturn(Optional.of(expired));

        ApiException ex = assertThrows(ApiException.class, () -> service.connect(2L, "ABCD2345"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void codeConsumedByAnotherRequestIsBadRequest() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(child));
        InvitationToken token = validToken(10L);
        when(tokenRepository.findByCode("ABCD2345")).thenReturn(Optional.of(token));
        when(connectionRepository.findByParentIdAndChildId(1L, 2L)).thenReturn(Optional.empty());
        when(tokenRepository.markUsed(10L)).thenReturn(0);   // lost the race

        ApiException ex = assertThrows(ApiException.class, () -> service.connect(2L, "ABCD2345"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validCodeCreatesPendingConnectionWithNoSharing() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(child));
        when(tokenRepository.findByCode("ABCD2345")).thenReturn(Optional.of(validToken(10L)));
        when(connectionRepository.findByParentIdAndChildId(1L, 2L)).thenReturn(Optional.empty());
        when(tokenRepository.markUsed(10L)).thenReturn(1);
        when(connectionRepository.save(any(FamilyConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        ConnectionResponse response = service.connect(2L, "  abcd2345 ");   // lower-case + spaces are normalised

        assertEquals(ConnectionStatus.PENDING, response.status());
        assertFalse(response.shareCheckins());
        assertEquals("Asha", response.parent().name());
        assertEquals("Ravi", response.child().name());
    }

    @Test
    void alreadyPendingConnectionIsConflict() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(child));
        when(tokenRepository.findByCode("ABCD2345")).thenReturn(Optional.of(validToken(10L)));
        when(connectionRepository.findByParentIdAndChildId(1L, 2L))
                .thenReturn(Optional.of(new FamilyConnection(parent, child)));

        ApiException ex = assertThrows(ApiException.class, () -> service.connect(2L, "ABCD2345"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ---------- approve / reject ----------
    @Test
    void parentCannotDecideAnotherParentsConnection() {
        when(connectionRepository.findByIdAndParentId(5L, 99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.decide(99L, new ApproveRequest(5L, true, false)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void approvingPendingRequestSetsApprovedAndSharing() {
        FamilyConnection pending = new FamilyConnection(parent, child);
        when(connectionRepository.findByIdAndParentId(5L, 1L)).thenReturn(Optional.of(pending));
        when(connectionRepository.save(any(FamilyConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        ConnectionResponse response = service.decide(1L, new ApproveRequest(5L, true, true));

        assertEquals(ConnectionStatus.APPROVED, response.status());
        assertTrue(response.shareCheckins());
        assertNotNull(response.approvedAt());
    }

    @Test
    void rejectingApprovedConnectionRevokesAccess() {
        FamilyConnection approved = new FamilyConnection(parent, child);
        approved.approve(true);
        when(connectionRepository.findByIdAndParentId(5L, 1L)).thenReturn(Optional.of(approved));
        when(connectionRepository.save(any(FamilyConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        ConnectionResponse response = service.decide(1L, new ApproveRequest(5L, false, null));

        assertEquals(ConnectionStatus.REJECTED, response.status());
        assertFalse(response.shareCheckins());
    }

    @Test
    void approvingAnAlreadyRejectedRequestIsConflict() {
        FamilyConnection rejected = new FamilyConnection(parent, child);
        rejected.reject();
        when(connectionRepository.findByIdAndParentId(5L, 1L)).thenReturn(Optional.of(rejected));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.decide(1L, new ApproveRequest(5L, true, false)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ---------- data-access guard used from Day 3 onwards ----------
    @Test
    void childWithoutApprovedLinkIsForbiddenFromParentData() {
        when(connectionRepository.existsByParentIdAndChildIdAndStatus(1L, 2L, ConnectionStatus.APPROVED))
                .thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.assertChildCanAccessParent(2L, 1L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void childWithApprovedLinkPassesTheGuard() {
        when(connectionRepository.existsByParentIdAndChildIdAndStatus(1L, 2L, ConnectionStatus.APPROVED))
                .thenReturn(true);

        assertDoesNotThrow(() -> service.assertChildCanAccessParent(2L, 1L));
    }

    // ---------- Day 5: check-ins need the parent's extra consent ----------
    @Test
    void childWhoIsNotApprovedCannotViewCheckins() {
        when(connectionRepository.existsByParentIdAndChildIdAndStatus(1L, 2L, ConnectionStatus.APPROVED))
                .thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.assertChildCanViewCheckins(2L, 1L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        // stopped at the first check: the sharing flag was never even looked at
        verify(connectionRepository, never()).existsByParentIdAndChildIdAndStatusAndShareCheckinsTrue(
                1L, 2L, ConnectionStatus.APPROVED);
    }

    @Test
    void approvedChildCannotViewCheckinsWhenParentDidNotShareThem() {
        when(connectionRepository.existsByParentIdAndChildIdAndStatus(1L, 2L, ConnectionStatus.APPROVED))
                .thenReturn(true);
        when(connectionRepository.existsByParentIdAndChildIdAndStatusAndShareCheckinsTrue(1L, 2L, ConnectionStatus.APPROVED))
                .thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.assertChildCanViewCheckins(2L, 1L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("not shared"));
    }

    @Test
    void approvedChildCanViewCheckinsWhenParentSharesThem() {
        when(connectionRepository.existsByParentIdAndChildIdAndStatus(1L, 2L, ConnectionStatus.APPROVED))
                .thenReturn(true);
        when(connectionRepository.existsByParentIdAndChildIdAndStatusAndShareCheckinsTrue(1L, 2L, ConnectionStatus.APPROVED))
                .thenReturn(true);

        assertDoesNotThrow(() -> service.assertChildCanViewCheckins(2L, 1L));
    }

    // ---------- helpers ----------
    private User user(long id, String name, Role role) {
        User u = new User(name, name.toLowerCase() + "@test.com", "hash", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private InvitationToken validToken(long id) {
        InvitationToken t = new InvitationToken(parent, "ABCD2345", LocalDateTime.now().plusHours(1));
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }
}
