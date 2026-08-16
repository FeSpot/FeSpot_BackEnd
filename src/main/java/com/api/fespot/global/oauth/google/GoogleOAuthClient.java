package com.api.fespot.global.oauth.google;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.exception.InvalidOAuthTokenException;
import com.api.fespot.domain.auth.exception.OAuthProviderUnavailableException;
import com.api.fespot.domain.auth.service.social.OAuthClient;
import com.api.fespot.domain.auth.service.social.OAuthIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.jwk.source.JWKSetUnavailableException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

public final class GoogleOAuthClient implements OAuthClient {

    private static final Set<String> GOOGLE_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );
    private static final OAuth2Error INVALID_ISSUER = new OAuth2Error("invalid_token", "Invalid issuer", null);
    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error("invalid_token", "Invalid audience", null);
    private static final OAuth2Error INVALID_ISSUED_AT = new OAuth2Error("invalid_token", "Invalid issued-at", null);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    private static final JsonMapper OBJECT_MAPPER = new JsonMapper();

    private final NimbusJwtDecoder jwtDecoder;

    public GoogleOAuthClient(String clientId, NimbusJwtDecoder jwtDecoder) {
        this(clientId, jwtDecoder, Clock.systemUTC());
    }

    GoogleOAuthClient(String clientId, NimbusJwtDecoder jwtDecoder, Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.jwtDecoder.setClaimSetConverter(MappedJwtClaimSetConverter.withDefaults(Map.of(
                "iss", value -> normalizedIssuerUrl((String) value)
        )));
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> jwt.getIssuer() != null
                && GOOGLE_ISSUERS.contains(jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_ISSUER);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> hasValidAudience(jwt, clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> issuedAtValidator = jwt -> hasValidIssuedAt(jwt, clock)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_ISSUED_AT);
        this.jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuedAtValidator,
                issuerValidator,
                audienceValidator
        ));
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    private boolean hasValidAudience(Jwt jwt, String clientId) {
        if (!jwt.getAudience().contains(clientId)) {
            return false;
        }
        String authorizedParty = jwt.getClaimAsString("azp");
        if (jwt.getAudience().size() > 1) {
            return clientId.equals(authorizedParty);
        }
        return authorizedParty == null || clientId.equals(authorizedParty);
    }

    private boolean hasValidIssuedAt(Jwt jwt, Clock clock) {
        Instant issuedAt = jwt.getIssuedAt();
        return issuedAt != null && !issuedAt.isAfter(clock.instant().plus(CLOCK_SKEW));
    }

    @Override
    public OAuthIdentity authenticate(String credential) {
        if (credential == null || credential.isBlank() || credential.length() > 16384) {
            throw new InvalidOAuthTokenException();
        }
        try {
            validateRawClaims(credential);
            return toIdentity(jwtDecoder.decode(credential));
        } catch (JwtException exception) {
            if (hasCause(exception, JWKSetUnavailableException.class)
                    || hasCause(exception, RemoteKeySourceException.class)) {
                throw new OAuthProviderUnavailableException(exception);
            }
            throw new InvalidOAuthTokenException();
        } catch (IllegalArgumentException | IOException exception) {
            throw new InvalidOAuthTokenException();
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void validateRawClaims(String credential) throws IOException {
        String[] segments = credential.split("\\.", -1);
        if (segments.length != 3) {
            throw new IllegalArgumentException("Invalid Google ID Token format");
        }
        JsonNode claims = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(segments[1]));
        requireText(claims, "iss");
        if (!GOOGLE_ISSUERS.contains(claims.path("iss").textValue())) {
            throw new IllegalArgumentException("Invalid Google ID Token issuer");
        }
        requireText(claims, "sub");
        requireAudience(claims.get("aud"));
        requireIntegralNumericDate(claims, "exp");
        requireIntegralNumericDate(claims, "iat");
        if (claims.has("nbf")) {
            requireIntegralNumericDate(claims, "nbf");
        }
        requireText(claims, "email");
        if (!claims.path("email_verified").isBoolean()) {
            throw new IllegalArgumentException("Invalid Google ID Token claim type");
        }
        if (claims.has("azp")) {
            requireText(claims, "azp");
        }
        if (claims.has("name")) {
            requireText(claims, "name");
        }
    }

    private void requireText(JsonNode claims, String name) {
        if (!claims.path(name).isTextual()) {
            throw new IllegalArgumentException("Invalid Google ID Token claim type");
        }
    }

    private void requireAudience(JsonNode audience) {
        if (audience != null && audience.isTextual()) {
            return;
        }
        if (audience != null
                && audience.isArray()
                && audience.size() > 0
                && StreamSupport.stream(audience.spliterator(), false)
                .allMatch(JsonNode::isTextual)) {
            return;
        }
        throw new IllegalArgumentException("Invalid Google ID Token audience type");
    }

    private void requireIntegralNumericDate(JsonNode claims, String name) {
        if (!claims.path(name).isIntegralNumber()) {
            throw new IllegalArgumentException("Invalid Google ID Token timestamp type");
        }
    }

    private Object normalizedIssuerUrl(String issuer) {
        String normalizedIssuer = "accounts.google.com".equals(issuer)
                ? "https://accounts.google.com"
                : issuer;
        try {
            return URI.create(normalizedIssuer).toURL();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid Google ID Token issuer", exception);
        }
    }

    private OAuthIdentity toIdentity(Jwt jwt) {
        Object subjectClaim = jwt.getClaims().get("sub");
        Object emailClaim = jwt.getClaims().get("email");
        Object emailVerifiedClaim = jwt.getClaims().get("email_verified");
        if (!(subjectClaim instanceof String subject) || subject.isBlank()
                || !(emailClaim instanceof String email) || email.isBlank()
                || !(emailVerifiedClaim instanceof Boolean emailVerified) || !emailVerified) {
            throw new InvalidOAuthTokenException();
        }
        String name = resolveName(jwt.getClaims().get("name"), email);
        return new OAuthIdentity(
                OAuthProvider.GOOGLE,
                subject,
                email,
                name,
                true
        );
    }

    private String resolveName(Object nameClaim, String email) {
        if (nameClaim instanceof String name && !name.isBlank()) {
            return name;
        }
        int separator = email.indexOf('@');
        if (separator <= 0) {
            throw new InvalidOAuthTokenException();
        }
        return email.substring(0, separator);
    }
}
