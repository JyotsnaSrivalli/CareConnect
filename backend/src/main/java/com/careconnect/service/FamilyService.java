package com.careconnect.service;

import com.careconnect.dto.ApproveRequest;
import com.careconnect.dto.ConnectionResponse;
import com.careconnect.dto.InviteResponse;
import com.careconnect.entity.*;
import com.careconnect.exception.ApiException;
import com.careconnect.repository.FamilyConnectionRepository;
import com.careconnect.repository.InvitationTokenRepository;
import com.careconnect.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class FamilyService {

    // No 0/O/1/I so codes are easy to read out over the phone
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final Duration CODE_VALIDITY = Duration.ofHours(24);
    private static final String BAD_CODE = "This invitation code has expired or was already used";

    private final SecureRandom random = new SecureRandom();   // cryptographically secure, unlike java.util.Random

    private final UserRepository userRepository;
    private final InvitationTokenRepository tokenRepository;
    private final FamilyConnectionRepository connectionRepository;

    public FamilyService(UserRepository userRepository,
                         InvitationTokenRepository tokenRepository,
                         FamilyConnectionRepository connectionRepository) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.connectionRepository = connectionRepository;
    }

    /** Parent generates a single-use code valid for 24 hours. */
    @Transactional
    public InviteResponse createInvitation(Long parentId) {
        User parent = requireUser(parentId, Role.PARENT, "Only parents can generate invitation codes");
        InvitationToken token = tokenRepository.save(
                new InvitationToken(parent, generateUniqueCode(), LocalDateTime.now().plus(CODE_VALIDITY)));
        return new InviteResponse(token.getCode(), token.getExpiresAt());
    }

    /** Child submits a code -> PENDING connection (no data is shared until the parent approves). */
    @Transactional
    public ConnectionResponse connect(Long childId, String rawCode) {
        User child = requireUser(childId, Role.CHILD, "Only child accounts can use invitation codes");
        String code = rawCode.trim().toUpperCase(Locale.ROOT);

        InvitationToken token = tokenRepository.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("Invalid invitation code"));
        if (token.isUsed() || token.isExpired(LocalDateTime.now())) {
            throw ApiException.badRequest(BAD_CODE);
        }

        User parent = token.getParent();
        Optional<FamilyConnection> existing = connectionRepository.findByParentIdAndChildId(parent.getId(), childId);
        FamilyConnection connection;
        if (existing.isPresent()) {
            connection = existing.get();
            if (connection.getStatus() != ConnectionStatus.REJECTED) {
                throw ApiException.conflict("You are already linked to this parent or waiting for approval");
            }
            connection.reopen();
        } else {
            connection = new FamilyConnection(parent, child);
        }

        // Atomic single-use guard: if two requests race, only one gets 1 here
        if (tokenRepository.markUsed(token.getId()) == 0) {
            throw ApiException.badRequest(BAD_CODE);
        }
        return ConnectionResponse.from(connectionRepository.save(connection));
    }

    /** Parent approves/rejects. The lookup includes parentId, so other parents' requests simply "don't exist". */
    @Transactional
    public ConnectionResponse decide(Long parentId, ApproveRequest request) {
        FamilyConnection connection = connectionRepository
                .findByIdAndParentId(request.connectionId(), parentId)
                .orElseThrow(() -> ApiException.notFound("Connection request not found"));

        if (request.approve()) {
            if (connection.getStatus() != ConnectionStatus.PENDING) {
                throw ApiException.conflict("Only pending requests can be approved");
            }
            connection.approve(Boolean.TRUE.equals(request.shareCheckins()));
        } else {
            if (connection.getStatus() == ConnectionStatus.REJECTED) {
                throw ApiException.conflict("This connection is already rejected");
            }
            connection.reject();   // also works as "revoke access" for an approved link
        }
        return ConnectionResponse.from(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> listConnections(Long userId, Role role) {
        List<FamilyConnection> list = (role == Role.PARENT)
                ? connectionRepository.findAllForParent(userId)
                : connectionRepository.findAllForChild(userId);
        return list.stream().map(ConnectionResponse::from).toList();
    }

    /**
     * Reusable guard for Day 3-5: call this before returning ANY parent data to a child.
     * Throws 403 unless an APPROVED link exists between exactly this child and this parent.
     */
    @Transactional(readOnly = true)
    public void assertChildCanAccessParent(Long childId, Long parentId) {
        if (!connectionRepository.existsByParentIdAndChildIdAndStatus(parentId, childId, ConnectionStatus.APPROVED)) {
            throw ApiException.forbidden("You do not have access to this parent's information");
        }
    }

    /**
     * Day 5: check-ins are more personal than medicines, so on top of the normal APPROVED check the parent must
     * have chosen "share check-ins" when approving. A revoked connection fails the first check already.
     */
    public void assertChildCanViewCheckins(Long childId, Long parentId) {
        assertChildCanAccessParent(childId, parentId);
        if (!connectionRepository.existsByParentIdAndChildIdAndStatusAndShareCheckinsTrue(
                parentId, childId, ConnectionStatus.APPROVED)) {
            throw ApiException.forbidden("This parent has not shared their check-ins with you");
        }
    }

    private User requireUser(Long id, Role expectedRole, String wrongRoleMessage) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        if (user.getRole() != expectedRole) {
            throw ApiException.forbidden(wrongRoleMessage);
        }
        return user;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!tokenRepository.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate a unique invitation code");
    }
}
