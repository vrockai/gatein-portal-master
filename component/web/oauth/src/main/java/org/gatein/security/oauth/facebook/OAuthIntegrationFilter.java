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

package org.gatein.security.oauth.facebook;

import java.io.IOException;
import java.security.Principal;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.impl.UserImpl;
import org.exoplatform.web.security.AuthenticationRegistry;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.gatein.security.oauth.data.SocialNetworkService;
import org.gatein.security.oauth.utils.OAuthConstants;
import org.gatein.sso.agent.GenericAgent;
import org.gatein.sso.agent.filter.api.AbstractSSOInterceptor;
import org.picketlink.social.standalone.fb.FacebookPrincipal;
import org.picketlink.social.standalone.fb.FacebookProcessor;

/**
 * This filter has already access to authenticated OAuth principal, so it's work starts after successful OAuth authentication.
 * Responsibility of this filter is to handle integration with GateIn (Redirect to GateIn registration if needed, establish context
 * and redirect to JAAS to finish GateIn authentication etc)
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class OAuthIntegrationFilter extends AbstractSSOInterceptor {

    private static Logger log = LoggerFactory.getLogger(OAuthIntegrationFilter.class);

    private String loginUrl;
    private String registrationUrl;
    private boolean attachUsernamePasswordToLoginURL;

    private SocialNetworkService socialNetworkService;
    private AuthenticationRegistry authenticationRegistry;

    @Override
    protected void initImpl() {
        this.loginUrl = getInitParameter("loginUrl");
        this.registrationUrl = getInitParameter("registrationUrl");
        if (registrationUrl == null) {
            registrationUrl = "/" + getExoContainer().getContext().getName() + "/";
        }

        String attachUsernamePasswordToLoginURLConfig = getInitParameter("attachUsernamePasswordToLoginURL");
        this.attachUsernamePasswordToLoginURL = attachUsernamePasswordToLoginURLConfig == null ? true : Boolean.parseBoolean(attachUsernamePasswordToLoginURLConfig);

        log.info("OAuthIntegrationFilter configuration: loginURL=" + loginUrl +
                ", registrationUrl=" + this.registrationUrl +
                ", attachUsernamePasswordToLoginURL=" + this.attachUsernamePasswordToLoginURL);

        socialNetworkService = (SocialNetworkService)getExoContainer().getComponentInstanceOfType(SocialNetworkService.class);
        authenticationRegistry = (AuthenticationRegistry)getExoContainer().getComponentInstanceOfType(AuthenticationRegistry.class);
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        // Simply continue with request if we are in the middle of registration process
        User oauthAuthenticatedUser = (User)authenticationRegistry.getAttributeOfClient(httpRequest, OAuthConstants.ATTRIBUTE_AUTHENTICATED_PORTAL_USER);
        if (oauthAuthenticatedUser != null) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpRequest.getSession();
        String state = (String) session.getAttribute(FacebookProcessor.FB_AUTH_STATE_SESSION_ATTRIBUTE);

        if (FacebookProcessor.STATES.FINISH.name().equals(state)) {
            Principal principal = (Principal)authenticationRegistry.getAttributeOfClient(httpRequest, OAuthConstants.ATTRIBUTE_AUTHENTICATED_OAUTH_PRINCIPAL);
            if (principal == null) {
                log.error("Principal was null. Maybe login modules need to be configured properly.");
            }

            processPrincipal(httpRequest, httpResponse, (FacebookPrincipal)principal);
            return;
        }  else {
            chain.doFilter(request, response);
        }
    }


    protected void processPrincipal(HttpServletRequest httpRequest, HttpServletResponse httpResponse, FacebookPrincipal principal) throws IOException {
        User portalUser = socialNetworkService.findUserByFacebookUsername(principal.getUsername());

        if (portalUser == null) {
            // This means that user has been successfully authenticated via OAuth, but doesn't exist in GateIn. So we need to establish context
            // with AuthenticationRegistry and redirect to GateIn registration form
            handleRedirectToRegistrationForm(httpRequest, httpResponse, principal);
        } else {
            // This means that user has been successfully authenticated via OAuth and exist GateIn. So we need to establish SSO context
            // and clean our own local context from AuthenticationRegistry. Then redirect to loginUrl to perform GateIn WCI login
            handleRedirectToPortalLogin(httpRequest, httpResponse, portalUser, principal.getAccessToken());
            cleanAuthenticationContext(httpRequest);
        }
    }


    protected void handleRedirectToRegistrationForm(HttpServletRequest httpRequest, HttpServletResponse httpResponse, FacebookPrincipal principal)
            throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("Not found portalUser with username " + principal.getUsername() + ". Redirecting to registration form");
        }

        User gateInUser = convertOAuthPrincipalToGateInUser(principal);
        authenticationRegistry.setAttributeOfClient(httpRequest, OAuthConstants.ATTRIBUTE_AUTHENTICATED_PORTAL_USER, gateInUser);

        String registrationRedirectUrl = httpResponse.encodeRedirectURL(registrationUrl);
        httpResponse.sendRedirect(registrationRedirectUrl);
    }


    protected void handleRedirectToPortalLogin(HttpServletRequest httpRequest, HttpServletResponse httpResponse, User portalUser, String accessToken)
            throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("Found portalUser " + portalUser + " corresponding to facebookPrincipal");
        }

        // TODO: Refactor this by made the method saveSSOCredentials public instead of protected
        new GenericAgent() {

            @Override
            public void saveSSOCredentials(String username, HttpServletRequest httpRequest) {
                super.saveSSOCredentials(username, httpRequest);
            }

        }.saveSSOCredentials(portalUser.getUserName(), httpRequest);

        socialNetworkService.saveFacebookAccessToken(portalUser.getUserName(), accessToken);

        // Now Facebook authentication handshake is finished and credentials are in session. We can redirect to JAAS authentication
        String loginRedirectURL = httpResponse.encodeRedirectURL(getLoginRedirectUrl(httpRequest, portalUser.getUserName()));
        httpResponse.sendRedirect(loginRedirectURL);
    }


    protected String getLoginRedirectUrl(HttpServletRequest req, String username) {
        StringBuilder url = new StringBuilder(this.loginUrl);

        if (attachUsernamePasswordToLoginURL) {
            String fakePassword = req.getSession().getId() + "_" + String.valueOf(System.currentTimeMillis());

            // Use sessionId and system millis as password (similar like spnego is doing)
            url.append("?username=").append(username).append("&password=").append(fakePassword);
        }

        return url.toString();
    }


    protected void cleanAuthenticationContext(HttpServletRequest httpRequest) {
        authenticationRegistry.removeAttributeOfClient(httpRequest, OAuthConstants.ATTRIBUTE_AUTHENTICATED_OAUTH_PRINCIPAL);
        authenticationRegistry.removeAttributeOfClient(httpRequest, OAuthConstants.ATTRIBUTE_AUTHENTICATED_PORTAL_USER);
        httpRequest.getSession().removeAttribute(FacebookProcessor.FB_AUTH_STATE_SESSION_ATTRIBUTE);
    }


    private User convertOAuthPrincipalToGateInUser(FacebookPrincipal principal) {
        User gateinUser = new UserImpl(principal.getUsername());
        gateinUser.setFirstName(principal.getFirstName());
        gateinUser.setLastName(principal.getLastName());
        gateinUser.setEmail(principal.getEmail());
        gateinUser.setDisplayName(principal.getAttribute("name"));
        return gateinUser;
    }
}
