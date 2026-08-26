package com.api.fespot.global.oauth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleClientSecretFactoryTest {

    private static final String TEAM_ID = "TEAM123456";
    private static final String CLIENT_ID = "com.example.fespot";
    private static final String KEY_ID = "KEY1234567";
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    private KeyPair keyPair;
    private String privateKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    @Test
    void signsClientSecretWithAppleRequiredClaims() throws Exception {
        AppleClientSecretFactory factory = AppleClientSecretFactory.create(
                TEAM_ID, CLIENT_ID, KEY_ID, privateKeyPem, Clock.fixed(NOW, ZoneOffset.UTC));

        SignedJWT jwt = SignedJWT.parse(factory.clientSecret());

        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void reusesCachedSecretUntilExpiryApproaches() {
        MutableClock clock = new MutableClock(NOW);
        AppleClientSecretFactory factory = AppleClientSecretFactory.create(
                TEAM_ID, CLIENT_ID, KEY_ID, privateKeyPem, clock);

        String first = factory.clientSecret();
        clock.advance(Duration.ofMinutes(1));
        String cached = factory.clientSecret();
        clock.advance(Duration.ofMinutes(4));
        String regenerated = factory.clientSecret();

        assertThat(cached).isEqualTo(first);
        assertThat(regenerated).isNotEqualTo(first);
    }

    @Test
    void parsesPemWithLiteralNewlineEscapes() {
        String escaped = privateKeyPem.replace("\n", "\\n");

        AppleClientSecretFactory factory = AppleClientSecretFactory.create(
                TEAM_ID, CLIENT_ID, KEY_ID, escaped, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(factory.clientSecret()).isNotBlank();
    }

    @Test
    void rejectsInvalidPrivateKey() {
        assertThatThrownBy(() -> AppleClientSecretFactory.create(
                TEAM_ID, CLIENT_ID, KEY_ID, "not-a-key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppleClientSecretFactory.create(
                TEAM_ID, CLIENT_ID, KEY_ID, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
