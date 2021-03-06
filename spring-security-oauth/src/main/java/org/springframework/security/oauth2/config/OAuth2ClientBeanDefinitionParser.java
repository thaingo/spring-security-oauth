/*
 * Copyright 2008-2009 Web Cohesion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.oauth2.config;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.security.oauth.config.ConfigUtils;
import org.springframework.security.oauth2.consumer.OAuth2ClientContextFilter;
import org.springframework.security.oauth2.consumer.OAuth2ClientProcessingFilter;
import org.springframework.security.oauth2.consumer.OAuth2ProfileChain;
import org.springframework.security.oauth2.consumer.rememberme.HttpSessionOAuth2RememberMeServices;
import org.springframework.security.oauth2.consumer.token.InMemoryOAuth2ClientTokenServices;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Parser for the OAuth "provider" element.
 *
 * @author Ryan Heaton
 */
public class OAuth2ClientBeanDefinitionParser implements BeanDefinitionParser {

  public BeanDefinition parse(Element element, ParserContext parserContext) {
    List<BeanMetadataElement> filterChain = ConfigUtils.findFilterChain(parserContext, element.getAttribute("filter-chain-ref"));
    String tokenServicesRef = element.getAttribute("token-services-ref");
    String resourceDetailsServiceRef = element.getAttribute("resource-details-service-ref");
    String rememberMeServicesRef = element.getAttribute("remember-me-services-ref");
    String profileManagerRef = element.getAttribute("profile-manager-ref");
    String requireAuthenticated = element.getAttribute("require-authenticated");
    String redirectStrategyRef = element.getAttribute("redirect-strategy-ref");

    if (!StringUtils.hasText(tokenServicesRef)) {
      tokenServicesRef = "oauth2ClientTokenServices";
      BeanDefinitionBuilder tokenServices = BeanDefinitionBuilder.rootBeanDefinition(InMemoryOAuth2ClientTokenServices.class);
      parserContext.getRegistry().registerBeanDefinition(tokenServicesRef, tokenServices.getBeanDefinition());
    }

    if (!StringUtils.hasText(rememberMeServicesRef)) {
      rememberMeServicesRef = "oauth2ClientRememberMeServices";
      BeanDefinitionBuilder rememberMeServices = BeanDefinitionBuilder.rootBeanDefinition(HttpSessionOAuth2RememberMeServices.class);
      parserContext.getRegistry().registerBeanDefinition(rememberMeServicesRef, rememberMeServices.getBeanDefinition());
    }

    if (!StringUtils.hasText(resourceDetailsServiceRef)) {
      resourceDetailsServiceRef = "oauth2ResourceDetailsService";
      BeanDefinitionBuilder resourceDetailsService = BeanDefinitionBuilder.rootBeanDefinition(ResourceDetailsServiceFactoryBean.class);
      parserContext.getRegistry().registerBeanDefinition(resourceDetailsServiceRef, resourceDetailsService.getBeanDefinition());
    }

    if (!StringUtils.hasText(profileManagerRef)) {
      profileManagerRef = "oauth2ClientProfileManager";
      BeanDefinitionBuilder profileManager = BeanDefinitionBuilder.rootBeanDefinition(OAuth2ProfileChain.class);
      if ("false".equalsIgnoreCase(requireAuthenticated)) {
        profileManager.addPropertyValue("requireAuthenticated", "false");
      }
      profileManager.addPropertyReference("tokenServices", tokenServicesRef);
      parserContext.getRegistry().registerBeanDefinition(profileManagerRef, profileManager.getBeanDefinition());
    }

    BeanDefinitionBuilder clientContextFilterBean = BeanDefinitionBuilder.rootBeanDefinition(OAuth2ClientContextFilter.class);
    clientContextFilterBean.addPropertyReference("profileManager", profileManagerRef);
    clientContextFilterBean.addPropertyReference("rememberMeServices", rememberMeServicesRef);

    if (StringUtils.hasText(redirectStrategyRef)) {
      clientContextFilterBean.addPropertyReference("redirectStrategy", redirectStrategyRef);
    }

    int filterIndex = insertIndex(filterChain);
    parserContext.getRegistry().registerBeanDefinition("oauth2ClientContextFilter", clientContextFilterBean.getBeanDefinition());
    filterChain.add(filterIndex++, new RuntimeBeanReference("oauth2ClientContextFilter"));

    BeanDefinition fids = ConfigUtils.createSecurityMetadataSource(element, parserContext);

    if (fids != null) {
      BeanDefinitionBuilder consumerFilterBean = BeanDefinitionBuilder.rootBeanDefinition(OAuth2ClientProcessingFilter.class);

      consumerFilterBean.addPropertyValue("objectDefinitionSource", fids);
      consumerFilterBean.addPropertyReference("resourceDetailsService", resourceDetailsServiceRef);
      parserContext.getRegistry().registerBeanDefinition("oauth2ClientSecurityFilter", consumerFilterBean.getBeanDefinition());
      filterChain.add(filterIndex, new RuntimeBeanReference("oauth2ClientSecurityFilter"));
    }

    return null;
  }

  /**
   * Attempts to find the place in the filter chain to insert the spring security oauth filters. Currently,
   * these filters are inserted after the ExceptionTranslationFilter.
   *
   * @param filterChain The filter chain configuration.
   * @return The insert index.
   */
  private int insertIndex(List<BeanMetadataElement> filterChain) {
    int i;
    for (i = 0; i < filterChain.size(); i++) {
      BeanMetadataElement filter = filterChain.get(i);
      if (filter instanceof BeanDefinition) {
        String beanName = ((BeanDefinition) filter).getBeanClassName();
        if (beanName.equals(ExceptionTranslationFilter.class.getName())) {
          return i + 1;
        }
      }
    }
    return filterChain.size();
  }
}
