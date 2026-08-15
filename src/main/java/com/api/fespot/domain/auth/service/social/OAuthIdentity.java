package com.api.fespot.domain.auth.service.social;

import com.api.fespot.domain.auth.enums.OAuthProvider;

public record OAuthIdentity(
        OAuthProvider provider,
        String providerUserId,
        String email,
        String name,
        boolean emailVerified
) {
}
