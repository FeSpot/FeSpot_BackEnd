package com.api.fespot.global.oauth.apple;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;

@ConfigurationProperties(prefix = "oauth.apple")
@Validated
public class AppleOAuthProperties {

    private static final String DEFAULT_JWK_SET_URI = "https://appleid.apple.com/auth/keys";

    @NotBlank(message = "Apple OAuth client ID must not be blank")
    @Size(max = 255)
    private String clientId;

    @NotBlank(message = "Apple JWK Set URI must not be blank")
    @Size(max = 2_048)
    private String jwkSetUri = DEFAULT_JWK_SET_URI;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    @AssertTrue(message = "Apple JWK Set URI must be the canonical HTTPS endpoint")
    public boolean isTrustedJwkSetUri() {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            return true;
        }
        try {
            URI uri = new URI(jwkSetUri);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "appleid.apple.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && "/auth/keys".equals(uri.getPath())
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
