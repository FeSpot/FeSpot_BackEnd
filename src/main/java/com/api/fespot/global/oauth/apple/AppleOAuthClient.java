package com.api.fespot.global.oauth.apple;

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
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.StreamSupport;

public class AppleOAuthClient implements OAuthClient {

    private static final Set<String> ALLOWED_ISSUERS = Set.of("https://appleid.apple.com");
    private static final int MAX_CREDENTIAL_LENGTH = 16_384;
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    private static final JsonMapper JSON_MAPPER = new JsonMapper();
    private static final OAuth2Error INVALID_ISSUER = new OAuth2Error(
            "invalid_token", "The Apple ID Token issuer is invalid", null
    );
    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "The Apple ID Token audience is invalid", null
    );
    private static final OAuth2Error INVALID_ISSUED_AT = new OAuth2Error(
            "invalid_token", "The Apple ID Token issued-at claim is invalid", null
    );

    private final String clientId;
    private final NimbusJwtDecoder jwtDecoder;
    private final Clock clock;

    public AppleOAuthClient(String clientId, NimbusJwtDecoder jwtDecoder) {
        this(clientId, jwtDecoder, Clock.systemUTC());
    }

    AppleOAuthClient(String clientId, NimbusJwtDecoder jwtDecoder, Clock clock) {
        this.clientId = clientId;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;

        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> ALLOWED_ISSUERS.contains(
                jwt.getIssuer() == null ? null : jwt.getIssuer().toString()
        ) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_ISSUER);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
        OAuth2TokenValidator<Jwt> issuedAtValidator = this::validateIssuedAt;
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        this.jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerValidator,
                audienceValidator,
                issuedAtValidator
        ));
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.APPLE;
    }

    @Override
    public OAuthIdentity authenticate(String credential) {
        throw new InvalidOAuthTokenException();
    }

    @Override
    public OAuthIdentity authenticate(String credential, String expectedNonce) {
        if (expectedNonce == null || expectedNonce.isBlank() || expectedNonce.length() > 255) {
            throw new InvalidOAuthTokenException();
        }
        try {
            validateRawClaims(credential, expectedNonce);
            Jwt jwt = jwtDecoder.decode(credential);
            if (!matchesExpectedNonce(jwt.getClaimAsString("nonce"), expectedNonce)) {
                throw new InvalidOAuthTokenException();
            }
            return toIdentity(jwt);
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

    private OAuth2TokenValidatorResult validateIssuedAt(Jwt jwt) {
        if (jwt.getIssuedAt() == null
                || jwt.getIssuedAt().isAfter(clock.instant().plus(CLOCK_SKEW))) {
            return OAuth2TokenValidatorResult.failure(INVALID_ISSUED_AT);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private void validateRawClaims(String credential, String expectedNonce) throws IOException {
        if (credential == null || credential.isBlank() || credential.length() > MAX_CREDENTIAL_LENGTH) {
            throw new IllegalArgumentException("Invalid Apple identity token size");
        }
        String[] segments = credential.split("\\.", -1);
        if (segments.length != 3 || segments[0].isEmpty() || segments[1].isEmpty()) {
            throw new IllegalArgumentException("Invalid Apple identity token format");
        }
        JsonNode header = decodeJsonSegment(segments[0]);
        if (!header.isObject()
                || !"RS256".equals(header.path("alg").textValue())
                || !header.path("kid").isTextual()
                || header.path("kid").textValue().isBlank()) {
            throw new IllegalArgumentException("Invalid Apple identity token header");
        }
        byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
        if (payload.length > MAX_CREDENTIAL_LENGTH) {
            throw new IllegalArgumentException("Invalid Apple identity token payload size");
        }
        JsonNode claims = JSON_MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
        if (claims == null || !claims.isObject()) {
            throw new IllegalArgumentException("Invalid Apple identity token claims");
        }

        requireString(claims, "iss");
        if (!ALLOWED_ISSUERS.contains(claims.path("iss").textValue())) {
            throw new IllegalArgumentException("Invalid Apple identity token issuer");
        }
        requireString(claims, "sub");
        if (claims.path("sub").textValue().length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("Invalid Apple identity token subject");
        }
        requireAudience(claims);
        requireIntegralNumber(claims, "exp");
        requireIntegralNumber(claims, "iat");
        requireOptionalIntegralNumber(claims, "nbf");
        requireString(claims, "nonce");
        if (!matchesExpectedNonce(claims.path("nonce").textValue(), expectedNonce)) {
            throw new IllegalArgumentException("Invalid Apple identity token nonce");
        }
        validateOptionalEmailClaims(claims);
    }

    // FE 라이브러리(invertase RN, expo-apple-authentication 등)는 raw nonce를 SHA-256 해시하여
    // Apple 요청에 싣기 때문에, 토큰의 nonce 클레임은 SHA256(rawNonce) hex다.
    // raw nonce 그대로의 일치는 허용하지 않는다: JWT payload는 평문이라 토큰 탈취자가
    // nonce 클레임을 읽어 그대로 재전송하면 검증이 무력화된다. 해시 비교만 허용하면
    // 탈취자는 raw nonce(해시 원상)를 알 수 없어 재생이 차단된다.
    // (nonce가 평문으로 실리는 web appleid.auth.js 흐름은 서버측 일회용 nonce 저장소 없이는 지원하지 않는다.)
    private boolean matchesExpectedNonce(String claimedNonce, String expectedNonce) {
        if (claimedNonce == null) {
            return false;
        }
        return constantTimeEquals(claimedNonce, sha256Hex(expectedNonce));
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private JsonNode decodeJsonSegment(String segment) throws IOException {
        byte[] decoded = Base64.getUrlDecoder().decode(segment);
        if (decoded.length > MAX_CREDENTIAL_LENGTH) {
            throw new IllegalArgumentException("Invalid Apple identity token segment size");
        }
        return JSON_MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    private void requireAudience(JsonNode claims) {
        JsonNode audience = claims.path("aud");
        if (audience.isTextual() && !audience.textValue().isBlank()) {
            return;
        }
        if (audience.isArray()
                && audience.size() > 0
                && StreamSupport.stream(audience.spliterator(), false)
                .allMatch(value -> value.isTextual() && !value.textValue().isBlank())) {
            return;
        }
        throw new IllegalArgumentException("Invalid Apple identity token audience");
    }

    private void validateOptionalEmailClaims(JsonNode claims) {
        JsonNode email = claims.path("email");
        if (email.isMissingNode() || email.isNull()) {
            if (!claims.path("email_verified").isMissingNode()) {
                throw new IllegalArgumentException("Invalid Apple identity token email claims");
            }
            return;
        }
        if (!email.isTextual() || email.textValue().isBlank()) {
            throw new IllegalArgumentException("Invalid Apple identity token email");
        }
        JsonNode verified = claims.path("email_verified");
        boolean validBoolean = verified.isBoolean();
        boolean validString = verified.isTextual()
                && ("true".equals(verified.textValue()) || "false".equals(verified.textValue()));
        if (!validBoolean && !validString) {
            throw new IllegalArgumentException("Invalid Apple identity token email verification");
        }
    }

    private void requireString(JsonNode claims, String claimName) {
        JsonNode value = claims.path(claimName);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Invalid Apple identity token claim: " + claimName);
        }
    }

    private void requireIntegralNumber(JsonNode claims, String claimName) {
        if (!claims.path(claimName).isIntegralNumber()) {
            throw new IllegalArgumentException("Invalid Apple identity token claim: " + claimName);
        }
    }

    private void requireOptionalIntegralNumber(JsonNode claims, String claimName) {
        JsonNode value = claims.path(claimName);
        if (!value.isMissingNode() && !value.isIntegralNumber()) {
            throw new IllegalArgumentException("Invalid Apple identity token claim: " + claimName);
        }
    }

    private OAuthIdentity toIdentity(Jwt jwt) {
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        boolean emailVerified = email != null && isEmailVerified(jwt.getClaim("email_verified"));
        String name = emailVerified ? emailLocalPart(email) : null;
        return new OAuthIdentity(OAuthProvider.APPLE, subject, email, name, emailVerified);
    }

    private boolean isEmailVerified(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    private String emailLocalPart(String email) {
        int separator = email.indexOf('@');
        if (separator <= 0) {
            return null;
        }
        String localPart = email.substring(0, separator).trim();
        return localPart.isEmpty() ? null : localPart;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
