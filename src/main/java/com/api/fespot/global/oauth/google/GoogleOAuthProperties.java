package com.api.fespot.global.oauth.google;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;

@ConfigurationProperties(prefix = "oauth.google")
@Validated
public class GoogleOAuthProperties {

    @NotBlank
    @Size(max = 255)
    private String clientId;

    @NotBlank
    private String jwkSetUri = "https://www.googleapis.com/oauth2/v3/certs";

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

    @AssertTrue(message = "Google JWK Set URI must be the trusted Google endpoint")
    public boolean isTrustedJwkSetUri() {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(jwkSetUri);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "www.googleapis.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null
                    && "/oauth2/v3/certs".equals(uri.getPath())
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
