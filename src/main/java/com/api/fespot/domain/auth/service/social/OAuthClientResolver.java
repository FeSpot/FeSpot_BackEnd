package com.api.fespot.domain.auth.service.social;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.exception.UnsupportedOAuthProviderException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class OAuthClientResolver {

    private final Map<OAuthProvider, OAuthClient> clients;

    public OAuthClientResolver(List<OAuthClient> clients) {
        EnumMap<OAuthProvider, OAuthClient> indexedClients = new EnumMap<>(OAuthProvider.class);
        for (OAuthClient client : clients) {
            OAuthProvider provider = client.provider();
            if (provider == null) {
                throw new IllegalStateException("OAuth client provider must not be null");
            }
            if (indexedClients.putIfAbsent(provider, client) != null) {
                throw new IllegalStateException("Duplicate OAuth client: " + provider);
            }
        }
        this.clients = Map.copyOf(indexedClients);
    }

    public OAuthClient resolve(OAuthProvider provider) {
        OAuthClient client = clients.get(provider);
        if (client == null) {
            throw new UnsupportedOAuthProviderException();
        }
        return client;
    }
}
