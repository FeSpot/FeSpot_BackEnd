package com.api.fespot.global.oauth.apple;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Apple 토큰 엔드포인트(/auth/token, /auth/revoke) 호출에 필요한 client_secret(ES256 JWT)을
 * p8 개인 키로 서명해 생성한다. Apple 규격: iss=Team ID, sub=Client ID, aud=https://appleid.apple.com.
 */
public final class AppleClientSecretFactory {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final Duration SECRET_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(1);

    private final String teamId;
    private final String clientId;
    private final String keyId;
    private final ECPrivateKey privateKey;
    private final Clock clock;

    private volatile CachedSecret cached;

    AppleClientSecretFactory(String teamId, String clientId, String keyId, ECPrivateKey privateKey, Clock clock) {
        this.teamId = teamId;
        this.clientId = clientId;
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.clock = clock;
    }

    public static AppleClientSecretFactory create(
            String teamId,
            String clientId,
            String keyId,
            String privateKeyPem
    ) {
        return new AppleClientSecretFactory(
                teamId, clientId, keyId, parsePrivateKey(privateKeyPem), Clock.systemUTC());
    }

    static AppleClientSecretFactory create(
            String teamId,
            String clientId,
            String keyId,
            String privateKeyPem,
            Clock clock
    ) {
        return new AppleClientSecretFactory(teamId, clientId, keyId, parsePrivateKey(privateKeyPem), clock);
    }

    /** 유효한 캐시가 있으면 재사용하고, 만료 임박 시 새로 서명한다. */
    public String clientSecret() {
        Instant now = clock.instant();
        CachedSecret current = cached;
        if (current != null && now.isBefore(current.expiresAt.minus(REFRESH_MARGIN))) {
            return current.value;
        }
        String secret = sign(now);
        cached = new CachedSecret(secret, now.plus(SECRET_TTL));
        return secret;
    }

    private String sign(Instant now) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(keyId)
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(teamId)
                .subject(clientId)
                .audience(APPLE_AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(SECRET_TTL)))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to sign Apple client secret", exception);
        }
        return jwt.serialize();
    }

    /**
     * PKCS#8 PEM(p8 파일 내용)을 EC 개인 키로 파싱한다.
     * 환경변수 주입 시 개행이 리터럴 {@code \n}으로 들어오는 경우도 허용한다.
     */
    static ECPrivateKey parsePrivateKey(String privateKeyPem) {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalArgumentException("Apple private key must not be blank");
        }
        String base64 = privateKeyPem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException
                 | ClassCastException exception) {
            throw new IllegalArgumentException("Invalid Apple private key (expected PKCS#8 EC p8 PEM)", exception);
        }
    }

    private record CachedSecret(String value, Instant expiresAt) {
    }
}
