/*
 * JBoss, a division of Red Hat
 * Copyright 2013, Red Hat Middleware, LLC, and individual
 * contributors as indicated by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.gatein.security.oauth.generic;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.gatein.common.exception.GateInException;
import org.gatein.common.exception.GateInExceptionConstants;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.gatein.security.oauth.common.utils.OAuthConstants;
import org.gatein.security.oauth.data.SocialNetworkService;
import org.gatein.sso.agent.filter.api.AbstractSSOInterceptor;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class OAuthLinkAccountFilter extends AbstractSSOInterceptor {

    private static Logger log = LoggerFactory.getLogger(OAuthLinkAccountFilter.class);

    private SocialNetworkService socialNetworkService;

    @Override
    protected void initImpl() {
        socialNetworkService = (SocialNetworkService)getExoContainer().getComponentInstanceOfType(SocialNetworkService.class);
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;
        HttpSession session = httpRequest.getSession();

        // Do nothing for anonymous user
        if (httpRequest.getRemoteUser() == null) {
            chain.doFilter(request, response);
            return;
        }

        OAuthPrincipal oauthPrincipal = (OAuthPrincipal)request.getAttribute(OAuthConstants.ATTRIBUTE_AUTHENTICATED_OAUTH_PRINCIPAL);

        if (oauthPrincipal == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            socialNetworkService.updateOAuthInfo(oauthPrincipal.getOauthProviderType(), httpRequest.getRemoteUser(),
                    oauthPrincipal.getUserName(), oauthPrincipal.getAccessToken());

            // Add some attribute to session, which will be read by OAuthLifecycle
            session.setAttribute(OAuthConstants.ATTRIBUTE_LINKED_OAUTH_PROVIDER_USERNAME_ATTR_NAME, oauthPrincipal.getOauthProviderType().getUserNameAttrName());
        } catch (GateInException gtnOauthException) {
            // Show warning message if user with this facebookUsername (or googleUsername) already exists
            if (gtnOauthException.getExceptionCode() == GateInExceptionConstants.EXCEPTION_CODE_DUPLICATE_OAUTH_PROVIDER_USERNAME) {
                // Add some attribute to session, which will be read by OAuthLifecycle
                session.setAttribute(OAuthConstants.ATTRIBUTE_EXCEPTION_AFTER_FAILED_LINK, gtnOauthException);
            } else {
                throw gtnOauthException;
            }
        }

        String urlToRedirect = (String)session.getAttribute(OAuthConstants.ATTRIBUTE_URL_TO_REDIRECT_AFTER_LINK_SOCIAL_ACCOUNT);
        if (urlToRedirect == null) {
            urlToRedirect = httpRequest.getContextPath();
        } else {
            session.removeAttribute(OAuthConstants.ATTRIBUTE_URL_TO_REDIRECT_AFTER_LINK_SOCIAL_ACCOUNT);
        }


        if (log.isTraceEnabled()) {
            log.trace("User profile successfully updated with new userName and accessToken. oauthProvider=" + oauthPrincipal.getOauthProviderType() +
                    ", username=" + httpRequest.getRemoteUser() + ", oauthUsername=" + oauthPrincipal.getUserName());
            log.trace("Will redirect user to URL: " + urlToRedirect);
        }

        httpResponse.sendRedirect(httpResponse.encodeRedirectURL(urlToRedirect));
    }
}
