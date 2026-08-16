package com.api.fespot.domain.auth.repository;

import java.time.Duration;

/**
 * Single-use refresh token state boundary. Implementations must make rotate atomic.
 */
public interface RefreshSessionStore {

    void register(String refreshToken, Long userId, Duration ttl);

    /**
     * Atomically replaces a refresh session. A {@link RotationOutcome#REUSED} result advances
     * the user's generation only when the reused token belongs to the current generation,
     * so replay of a pre-revocation token cannot invalidate sessions registered afterward.
     */
    RotationOutcome rotate(
            String currentRefreshToken,
            String replacementRefreshToken,
            Long userId,
            Duration replacementTtl
    );

    /**
     * Revokes one session. A confirmed Redis no-op is successful so logout remains idempotent;
     * an absent script result or an unexpected result must fail.
     */
    void revoke(String refreshToken, Long userId);

    /**
     * Invalidates every session registered before this call while allowing later registrations.
     * Implementations must fail rather than report success when the revocation boundary cannot advance.
     */
    void revokeAll(Long userId);

    enum RotationOutcome {
        ROTATED,
        REVOKED,
        REUSED,
        UNKNOWN
    }
}
