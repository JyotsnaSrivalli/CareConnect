package com.careconnect.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Maps to `family_connections`: one row per parent<->child link.
 * Access to a parent's data is allowed ONLY while status == APPROVED.
 */
@Entity
@Table(name = "family_connections",
        uniqueConstraints = @UniqueConstraint(name = "uq_family_pair", columnNames = {"parent_id", "child_id"}))
public class FamilyConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private User parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private User child;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status;

    @Column(name = "share_checkins", nullable = false)
    private boolean shareCheckins;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    protected FamilyConnection() { }

    public FamilyConnection(User parent, User child) {
        this.parent = parent;
        this.child = child;
        this.status = ConnectionStatus.PENDING;
        this.shareCheckins = false;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // ---- state changes live on the entity so the rules are in one place ----
    public void approve(boolean shareCheckins) {
        this.status = ConnectionStatus.APPROVED;
        this.shareCheckins = shareCheckins;
        this.approvedAt = LocalDateTime.now();
    }

    /** Used both for rejecting a pending request and for revoking an approved one. */
    public void reject() {
        this.status = ConnectionStatus.REJECTED;
        this.shareCheckins = false;
        this.approvedAt = null;
    }

    /** A child may re-request after a rejection; the unique (parent, child) row is reused. */
    public void reopen() {
        this.status = ConnectionStatus.PENDING;
        this.shareCheckins = false;
        this.approvedAt = null;
    }

    public Long getId() { return id; }
    public User getParent() { return parent; }
    public User getChild() { return child; }
    public ConnectionStatus getStatus() { return status; }
    public boolean isShareCheckins() { return shareCheckins; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
}
