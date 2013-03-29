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

package org.gatein.security.oauth.google;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.util.ObjectParser;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfo;
import com.google.api.services.plus.Plus;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.web.security.security.SecureRandomService;
import org.gatein.common.exception.GateInException;
import org.gatein.common.exception.GateInExceptionConstants;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.gatein.security.oauth.common.OAuthConstants;
import org.gatein.security.oauth.utils.OAuthUtils;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class GoogleProcessorImpl implements GoogleProcessor {

    private static Logger log = LoggerFactory.getLogger(GoogleProcessorImpl.class);

    private static final String ACCESS_TOKEN_DELIMITER = "@_@_@";

    private final String redirectURL;
    private final String clientID;
    private final String clientSecret;
    private final List<String> scopes;
    private final String accessType;
    private final String applicationName;

    private final SecureRandomService secureRandomService;

    /**
     * Default HTTP transport to use to make HTTP requests.
     */
    private final HttpTransport TRANSPORT = new NetHttpTransport();

    /**
     * Default JSON factory to use to deserialize JSON.
     */
    private final JacksonFactory JSON_FACTORY = new JacksonFactory();

    // Only for unit test purpose
    public GoogleProcessorImpl() {
        this.redirectURL = null;
        this.clientID = null;
        this.clientSecret = null;
        this.scopes = null;
        this.accessType = null;
        this.applicationName = null;
        secureRandomService = null;
    }

    public GoogleProcessorImpl(ExoContainerContext context, InitParams params, SecureRandomService secureRandomService) {
        this.clientID = params.getValueParam("clientId").getValue();
        this.clientSecret = params.getValueParam("clientSecret").getValue();
        String redirectURLParam = params.getValueParam("redirectURL").getValue();
        String scope = params.getValueParam("scope").getValue();
        this.accessType = params.getValueParam("accessType").getValue();
        ValueParam appNameParam = params.getValueParam("applicationName");
        if (appNameParam != null && appNameParam.getValue() != null) {
            applicationName = appNameParam.getValue();
        } else {
            applicationName = "GateIn portal";
        }

        if (clientID == null || clientID.length() == 0 || clientID.trim().equals("<<to be replaced>>")) {
            throw new IllegalArgumentException("Property 'clientId' needs to be provided. The value should be " +
                    "clientId of your Google application");
        }

        if (clientSecret == null || clientSecret.length() == 0 || clientSecret.trim().equals("<<to be replaced>>")) {
            throw new IllegalArgumentException("Property 'clientSecret' needs to be provided. The value should be " +
                    "clientSecret of your Google application");
        }


        if (redirectURLParam == null || redirectURLParam.length() == 0) {
            this.redirectURL = "http://localhost:8080/" + context.getName() + OAuthConstants.GOOGLE_AUTHENTICATION_URL_PATH;
        }  else {
            this.redirectURL = redirectURLParam.replaceAll("@@portal.container.name@@", context.getName());
        }

        this.scopes = Arrays.asList(scope.split(" "));

        log.debug("configuration: clientId=" + clientID +
                ", clientSecret=" + clientSecret +
                ", redirectURL=" + redirectURL +
                ", scope=" + scopes +
                ", accessType=" + accessType +
                ", applicationName=" + applicationName);

        this.secureRandomService = secureRandomService;
    }

    @Override
    public GoogleInteractionState processGoogleAuthInteraction(HttpServletRequest request, HttpServletResponse response) throws
            IOException {
        HttpSession session = request.getSession();
        String state = (String) session.getAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE);

        // Very initial request to portal
        if (state == null || state.isEmpty()) {
            return initialInteraction(request, response);
        } else if (state.equals(GoogleInteractionState.STATE.AUTH.name())) {
            GoogleTokenResponse tokenResponse = obtainAccessToken(request);
            validateToken(tokenResponse);
            Userinfo userInfo = obtainUserInfo(tokenResponse);

            // Clear session parameters
            session.removeAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE);
            session.removeAttribute(OAuthConstants.ATTRIBUTE_VERIFICATION_STATE);
            return new GoogleInteractionState(GoogleInteractionState.STATE.FINISH, tokenResponse, userInfo);
        }

        // Likely shouldn't happen...
        return new GoogleInteractionState(GoogleInteractionState.STATE.valueOf(state), null, null);
    }

    protected GoogleInteractionState initialInteraction(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String verificationState = String.valueOf(secureRandomService.getSecureRandom().nextLong());
        String authorizeUrl = new GoogleAuthorizationCodeRequestUrl(clientID, redirectURL, scopes).
                setState(verificationState).setAccessType(accessType).build();
        if (log.isTraceEnabled()) {
            log.trace("Starting OAuth2 interaction with Google+");
            log.trace("URL to send to Google+: " + authorizeUrl);
        }

        HttpSession session = request.getSession();
        session.setAttribute(OAuthConstants.ATTRIBUTE_VERIFICATION_STATE, verificationState);
        session.setAttribute(OAuthConstants.ATTRIBUTE_AUTH_STATE, GoogleInteractionState.STATE.AUTH.name());
        response.sendRedirect(authorizeUrl);
        return new GoogleInteractionState(GoogleInteractionState.STATE.AUTH, null, null);
    }

    protected GoogleTokenResponse obtainAccessToken(HttpServletRequest request) throws IOException {
        HttpSession session = request.getSession();
        String stateFromSession = (String)session.getAttribute(OAuthConstants.ATTRIBUTE_VERIFICATION_STATE);
        String stateFromRequest = request.getParameter("state");
        if (stateFromSession == null || stateFromRequest == null || !stateFromSession.equals(stateFromRequest)) {
            throw new GateInException(GateInExceptionConstants.EXCEPTION_CODE_INVALID_STATE, "Validation of state parameter failed. stateFromSession="
                    + stateFromSession + ", stateFromRequest=" + stateFromRequest);
        }

        String code = request.getParameter("code");

        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(TRANSPORT, JSON_FACTORY, clientID,
                clientSecret, code, redirectURL).execute();

        if (log.isTraceEnabled()) {
            log.trace("Successfully obtained accessToken from google" + tokenResponse);
        }

        return tokenResponse;
    }

    protected void validateToken(GoogleTokenResponse tokenResponse) throws IOException {
        Oauth2 oauth2 = getOAuth2Instance(tokenResponse);
        GoogleCredential credential = getGoogleCredential(tokenResponse);
        Tokeninfo tokenInfo = oauth2.tokeninfo().setAccessToken(credential.getAccessToken()).execute();

        // If there was an error in the token info, abort.
        if (tokenInfo.containsKey("error")) {
            throw new GateInException(GateInExceptionConstants.EXCEPTION_CODE_GOOGLE_ERROR, "Error during token validation: " + tokenInfo.get("error").toString());
        }

        if (!tokenInfo.getIssuedTo().equals(clientID)) {
            throw new GateInException(GateInExceptionConstants.EXCEPTION_CODE_GOOGLE_ERROR, "Token's client ID does not match app's. clientID from tokenINFO: " + tokenInfo.getIssuedTo());
        }

        if (log.isTraceEnabled()) {
            log.trace("Successfully validated accessToken from google: " + tokenInfo);
        }
    }

    @Override
    public Userinfo obtainUserInfo(GoogleTokenResponse tokenResponse) throws IOException {
        Oauth2 oauth2 = getOAuth2Instance(tokenResponse);
        Userinfo uinfo = oauth2.userinfo().v2().me().get().execute();

        if (log.isTraceEnabled()) {
            log.trace("Successfully obtained userInfo from google: " + uinfo);
        }

        return uinfo;
    }

    @Override
    public Oauth2 getOAuth2Instance(GoogleTokenResponse tokenResponse) {
        GoogleCredential credential = getGoogleCredential(tokenResponse);
        return new Oauth2.Builder(TRANSPORT, JSON_FACTORY, credential).setApplicationName(applicationName).build();
    }

    @Override
    public Plus getPlusService(GoogleTokenResponse tokenData) {
        // Build credential from stored token data.
        GoogleCredential credential = getGoogleCredential(tokenData);

        // Create a new authorized API client.
        Plus service = new Plus.Builder(TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
        return service;
    }

    private GoogleCredential getGoogleCredential(GoogleTokenResponse tokenResponse) {
        return new GoogleCredential.Builder()
                .setJsonFactory(JSON_FACTORY)
                .setTransport(TRANSPORT)
                .setClientSecrets(clientID, clientSecret).build()
                .setFromTokenResponse(tokenResponse);
    }

    @Override
    public void revokeToken(GoogleTokenResponse tokenData) {
        try {
            HttpResponse revokeResponse = TRANSPORT.createRequestFactory()
                .buildGetRequest(new GenericUrl("https://accounts.google.com/o/oauth2/revoke?token=" + tokenData.getAccessToken())).execute();
            if (log.isTraceEnabled()) {
                log.trace("Revoked token " + tokenData);
            }
        } catch (IOException ioe) {
            throw new GateInException(GateInExceptionConstants.EXCEPTION_CODE_GOOGLE_ERROR, "Error when revoking token", ioe);
        }
    }

    @Override
    public String getStringFromToken(GoogleTokenResponse tokenData) {
        try {
            return JSON_FACTORY.toString(tokenData);
        } catch (IOException ioe) {
            throw new GateInException(GateInExceptionConstants.EXCEPTION_CODE_GOOGLE_ERROR, "Error during parsing", ioe);
        }
    }

    @Override
    public GoogleTokenResponse getTokenResponseFromString(String tokenResponseString) {
        try {
            ObjectParser parser = new JsonObjectParser(JSON_FACTORY);
            Reader reader = new StringReader(tokenResponseString);
            return parser.parseAndClose(reader, GoogleTokenResponse.class);
        } catch (IOException ioe) {
            throw new GateInException(GateInExceptionConstants.EXCEPTION_CODE_GOOGLE_ERROR, "Error during parsing", ioe);
        }
    }
}
