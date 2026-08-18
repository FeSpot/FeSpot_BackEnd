package com.api.fespot.global.security.jwt;

import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.security.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider implements InitializingBean {

    private static final String AUTHORITIES_KEY = "auth";
    private static final String ID_KEY = "id";
    private static final String TOKEN_TYPE_KEY = "typ";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private SecretKey key;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void afterPropertiesSet() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecretKey());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        long now = System.currentTimeMillis();
        Date validity = new Date(now + jwtProperties.getAccessExpiration() * 1000);

        // iat는 사용자 단위 일괄 차단(블랙리스트 경계) 판정에 사용된다.
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(ID_KEY, user.getId())
                .claim(AUTHORITIES_KEY, toAuthority(user))
                .claim(TOKEN_TYPE_KEY, TOKEN_TYPE_ACCESS)
                .issuedAt(new Date(now))
                .expiration(validity)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(User user, Duration ttl) {
        Date validity = new Date(System.currentTimeMillis() + ttl.toMillis());

        // jti로 매 발급마다 고유한 토큰을 보장한다. 로테이션 시 신·구 토큰이 반드시 달라야 한다.
        return Jwts.builder()
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .claim(ID_KEY, user.getId())
                .claim(TOKEN_TYPE_KEY, TOKEN_TYPE_REFRESH)
                .expiration(validity)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY, String.class).split(","))
                        .filter(authority -> !authority.isBlank())
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        Long id = claims.get(ID_KEY, Long.class);
        String username = claims.getSubject();
        AuthPrincipal principal = AuthPrincipal.authenticated(id, username, authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return TOKEN_TYPE_ACCESS.equals(claims.get(TOKEN_TYPE_KEY, String.class));
        } catch (io.jsonwebtoken.security.SignatureException | MalformedJwtException e) {
            log.warn("Invalid JWT signature.");
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token.");
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token.");
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or invalid.");
        }
        return false;
    }

    public Long getUserIdFromRefreshToken(String refreshToken) throws JwtException {
        Claims claims = parseClaims(refreshToken);
        if (!TOKEN_TYPE_REFRESH.equals(claims.get(TOKEN_TYPE_KEY, String.class))) {
            throw new MalformedJwtException("Token is not a refresh token");
        }
        return claims.get(ID_KEY, Long.class);
    }

    public Long getExpiration(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }

    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get(ID_KEY, Long.class);
    }

    public Instant getIssuedAt(String token) {
        Date issuedAt = parseClaims(token).getIssuedAt();
        return issuedAt == null ? null : issuedAt.toInstant();
    }

    private String toAuthority(User user) {
        return "ROLE_" + user.getRole().name();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
