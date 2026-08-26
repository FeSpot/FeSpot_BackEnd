package com.api.fespot.domain.auth.service.social;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SocialUnlinkGatewayResolver {

    private final Map<OAuthProvider, SocialUnlinkGateway> gateways;

    public SocialUnlinkGatewayResolver(List<SocialUnlinkGateway> gateways) {
        EnumMap<OAuthProvider, SocialUnlinkGateway> indexedGateways = new EnumMap<>(OAuthProvider.class);
        for (SocialUnlinkGateway gateway : gateways) {
            OAuthProvider provider = gateway.provider();
            if (provider == null) {
                throw new IllegalStateException("Social unlink gateway provider must not be null");
            }
            if (indexedGateways.putIfAbsent(provider, gateway) != null) {
                throw new IllegalStateException("Duplicate social unlink gateway: " + provider);
            }
        }
        this.gateways = Map.copyOf(indexedGateways);
    }

    /** 연동 해제는 best-effort이므로 미지원 제공자는 예외 대신 empty로 알린다. */
    public Optional<SocialUnlinkGateway> resolve(OAuthProvider provider) {
        return Optional.ofNullable(gateways.get(provider));
    }
}
