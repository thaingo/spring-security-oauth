package org.springframework.security.oauth2.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.common.DefaultThrowableAnalyzer;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.security.oauth2.provider.token.OAuth2ProviderTokenServices;
import org.springframework.security.web.util.ThrowableAnalyzer;
import org.springframework.util.Assert;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

/**
 * @author Ryan Heaton
 * @author Dave Syer
 */
public class OAuth2ProtectedResourceFilter extends GenericFilterBean {

  private OAuth2ProviderTokenServices tokenServices;
  private ThrowableAnalyzer throwableAnalyzer = new DefaultThrowableAnalyzer();

  @Override
  public void afterPropertiesSet() throws ServletException {
    super.afterPropertiesSet();
    Assert.notNull(getTokenServices(), "OAuth 2 token services must be supplied.");
  }

  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    try {
      String token = parseToken(request);
      if (token != null) {
        OAuth2Authentication auth = getTokenServices().loadAuthentication(token);

        if (auth == null) {
          throw new InvalidTokenException("Invalid token: " + token);
        }

        SecurityContextHolder.getContext().setAuthentication(auth);
      }

      chain.doFilter(request, response);

      if (logger.isDebugEnabled()) {
        logger.debug("Chain processed normally");
      }
    }
    catch (IOException ex) {
      throw ex;
    }
    catch (Exception ex) {
      // Try to extract a SpringSecurityException from the stacktrace
      Throwable[] causeChain = getThrowableAnalyzer().determineCauseChain(ex);
      RuntimeException ase = (AuthenticationException)
        getThrowableAnalyzer().getFirstThrowableOfType(AuthenticationException.class, causeChain);

      if (ase == null) {
        ase = (AccessDeniedException) getThrowableAnalyzer().getFirstThrowableOfType(AccessDeniedException.class, causeChain);
      }

      if (ase != null) {
        String error = null;
        String errorMessage = null;
        Map<String, String> additionalParams = null;
        if (ase instanceof OAuth2Exception) {
          error = ((OAuth2Exception) ase).getOAuth2ErrorCode();
          errorMessage = ase.getMessage();
          additionalParams = ((OAuth2Exception) ase).getAdditionalInformation();
        }
        setAuthenticateHeader(response, error, errorMessage, additionalParams);
        throw ase;
      }
      else {
        // Rethrow ServletExceptions and RuntimeExceptions as-is
        if (ex instanceof ServletException) {
          throw (ServletException) ex;
        }
        else if (ex instanceof RuntimeException) {
          throw (RuntimeException) ex;
        }

        // Wrap other Exceptions. These are not expected to happen
        throw new RuntimeException(ex);
      }
    }
  }

  protected void setAuthenticateHeader(HttpServletResponse response, String error, String errorMessage, Map<String, String> additionalParams) throws IOException {
    //if a security exception is thrown during an access attempt for a protected resource, we add throw WWW-Authenticate header.
    StringBuilder builder = new StringBuilder(OAuth2AccessToken.BEARER_TYPE);
    String delim = " ";

    if (error != null) {
      builder.append(delim).append("error=\"").append(error).append("\"");
      delim = ", ";
    }

    if (errorMessage != null) {
      builder.append(delim).append("error_description=\"").append(errorMessage).append("\"");
      delim = ", ";
    }

    if (additionalParams != null) {
      for (Map.Entry<String, String> param : additionalParams.entrySet()) {
        builder.append(delim).append(param.getKey()).append("=\"").append(param.getValue()).append("\"");
        delim = ", ";
      }
    }

    // TODO: scope

    response.addHeader("WWW-Authenticate", builder.toString());
  }

  protected String parseToken(HttpServletRequest request) {
    //first check the header...
    String token = parseHeaderToken(request);

    if (token == null) {
      token = request.getParameter("oauth_token");
    }

    return token;
  }

  /**
   * Parse the OAuth header parameters. The parameters will be oauth-decoded.
   *
   * @param request The request.
   * @return The parsed parameters, or null if no OAuth authorization header was supplied.
   */
  protected String parseHeaderToken(HttpServletRequest request) {
    Enumeration<String> headers = request.getHeaders("Authorization");
    while (headers.hasMoreElements()) {
      String value = headers.nextElement();
      if ((value.toLowerCase().startsWith(OAuth2AccessToken.BEARER_TYPE.toLowerCase()))) {
        String authHeaderValue = value.substring(OAuth2AccessToken.BEARER_TYPE.length()).trim();

        if (authHeaderValue.contains("oauth_signature_method") || authHeaderValue.contains("oauth_verifier")) {
          //presence of oauth_signature_method or oauth_verifier implies an oauth 1.x request
          continue;
        }

        int commaIndex = authHeaderValue.indexOf(',');
        if (commaIndex > 0) {
          authHeaderValue = authHeaderValue.substring(0, commaIndex);
        }

        //todo: parse any parameters...

        return authHeaderValue;
      }
      else {
        //todo: support additional authorization schemes for different token types, e.g. "MAC" specified by http://tools.ietf.org/html/draft-hammer-oauth-v2-mac-token
      }
    }

    return null;
  }

  public ThrowableAnalyzer getThrowableAnalyzer() {
    return throwableAnalyzer;
  }

  @Autowired ( required = false )
  public void setThrowableAnalyzer(ThrowableAnalyzer throwableAnalyzer) {
    this.throwableAnalyzer = throwableAnalyzer;
  }

  public OAuth2ProviderTokenServices getTokenServices() {
    return tokenServices;
  }

  @Autowired
  public void setTokenServices(OAuth2ProviderTokenServices tokenServices) {
    this.tokenServices = tokenServices;
  }

}
