package org.springframework.security.oauth2.consumer.webserver;

import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.consumer.OAuth2AccessTokenSupport;
import org.springframework.security.oauth2.consumer.OAuth2Profile;
import org.springframework.security.oauth2.consumer.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.consumer.OAuth2SecurityContext;
import org.springframework.security.oauth2.consumer.OAuth2SecurityContextHolder;
import org.springframework.security.oauth2.consumer.UserRedirectRequiredException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Implementation of the web server client oauth2 profile.
 * 
 * @author Ryan Heaton
 * @author Dave Syer
 */
public class WebServerProfile extends OAuth2AccessTokenSupport implements OAuth2Profile {

	public boolean supportsResource(OAuth2ProtectedResourceDetails resource) {
		return resource instanceof WebServerProfileResourceDetails
				&& "authorization_code".equals(resource.getGrantType());
	}

	public OAuth2AccessToken obtainNewAccessToken(OAuth2ProtectedResourceDetails details)
			throws UserRedirectRequiredException, AccessDeniedException {

		WebServerProfileResourceDetails resource = (WebServerProfileResourceDetails) details;
		OAuth2SecurityContext context = OAuth2SecurityContextHolder.getContext();

		if (context != null && context.getErrorParameters() != null) {

			// there was an oauth error...
			throw getSerializationService().deserializeError(context.getErrorParameters());

		} else if (context.getAuthorizationCode() == null) {

			throw getRedirectForAuthorization(resource, context);

		} else {

			return retrieveToken(getParametersForTokenRequest(resource, context), resource);

		}

	}

	private MultiValueMap<String, String> getParametersForTokenRequest(WebServerProfileResourceDetails resource,
			OAuth2SecurityContext context) {

		MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", resource.getClientId());
		form.add("code", context.getAuthorizationCode());

		Object state = context == null ? null : context.getPreservedState();
		if (state == null) {
			// no state preserved? check for a pre-established redirect uri.
			state = resource.getPreEstablishedRedirectUri();
		}

		if (state == null) {
			// still no redirect uri? just try the one for the current context...
			state = context == null ? null : context.getUserAuthorizationRedirectUri();
		}

		form.add("redirect_uri", String.valueOf(state));

		return form;

	}

	private UserRedirectRequiredException getRedirectForAuthorization(WebServerProfileResourceDetails resource,
			OAuth2SecurityContext context) {

		// we don't have an authorization code yet. So first get that.
		TreeMap<String, String> requestParameters = new TreeMap<String, String>();
		requestParameters.put("response_type", "code"); // oauth2 spec, section 3
		requestParameters.put("client_id", resource.getClientId());

		String redirectUri = resource.getPreEstablishedRedirectUri();
		if (redirectUri == null) {

			if (context == null) {
				throw new IllegalStateException(
						"No OAuth 2 security context has been established: unable to determine the redirect URI for the current context.");
			}
			redirectUri = context.getUserAuthorizationRedirectUri();
			if (redirectUri == null) {
				throw new IllegalStateException(
						"No redirect URI has been established for the current OAuth 2 security context.");
			}
			requestParameters.put("redirect_uri", redirectUri);

		} else {

			redirectUri = null;

		}

		if (resource.isScoped()) {

			StringBuilder builder = new StringBuilder();
			List<String> scope = resource.getScope();

			if (scope != null) {
				Iterator<String> scopeIt = scope.iterator();
				while (scopeIt.hasNext()) {
					builder.append(scopeIt.next());
					if (scopeIt.hasNext()) {
						builder.append(' ');
					}
				}
			}

			requestParameters.put("scope", builder.toString());
		}

		String stateKey = resource.getState();
		if (stateKey != null) {
			requestParameters.put("state", stateKey);
		}

		UserRedirectRequiredException redirectException = new UserRedirectRequiredException(
				resource.getUserAuthorizationUri(), requestParameters);

		if (redirectUri != null) {
			redirectException.setStateKey(resource.getState());
			redirectException.setStateToPreserve(redirectUri);
		}

		return redirectException;

	}

}
