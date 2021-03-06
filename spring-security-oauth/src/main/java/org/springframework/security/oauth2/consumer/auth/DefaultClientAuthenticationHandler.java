package org.springframework.security.oauth2.consumer.auth;

import org.springframework.http.client.ClientHttpRequest;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.oauth2.consumer.OAuth2ProtectedResourceDetails;
import org.springframework.util.MultiValueMap;

import java.io.UnsupportedEncodingException;

/**
 * Default implementation of the client authentication handler.
 *
 * @author Ryan Heaton
 */
public class DefaultClientAuthenticationHandler implements ClientAuthenticationHandler {

  public void authenticateTokenRequest(OAuth2ProtectedResourceDetails resource, MultiValueMap<String, String> form, ClientHttpRequest request) {
    if (resource.isSecretRequired()) {
      ClientAuthenticationScheme scheme = ClientAuthenticationScheme.http_basic;
      if (resource.getClientAuthenticationScheme() != null) {
        scheme = ClientAuthenticationScheme.valueOf(resource.getClientAuthenticationScheme());
      }

      try {
        switch (scheme) {
          case http_basic:
            request.getHeaders().add("Authorization", String.format("Basic %s", new String(Base64.encode(String.format("%s:%s", resource.getClientId(), resource.getClientSecret()).getBytes("UTF-8")), "UTF-8")));
            break;
          case form:
            form.add("client_secret", String.valueOf(resource.getClientSecret()));
            break;
          default:
            throw new IllegalStateException("Default authentication handler doesn't know how to handle scheme: " + scheme);
        }
      }
      catch (UnsupportedEncodingException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
