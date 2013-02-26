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
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONException;
import org.json.JSONObject;
import org.picketlink.social.facebook.FacebookConstants;
import org.picketlink.social.facebook.FacebookProcessor;
import org.picketlink.social.facebook.OAuthConstants;

/**
 * Modified version of FacebookProcessor for portal purpose
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class GateInFacebookProcessor extends FacebookProcessor {

    protected String returnURL;

    public GateInFacebookProcessor(String clientID, String clientSecret, String scope, String returnURL) {
        // Use empty rolesList because we don't need rolesList for GateIn integration
        super(clientID,  clientSecret, scope, returnURL, Arrays.asList(new String[]{}));

        // This is needed because returnURL is not accessible from superclass :/
        this.returnURL = returnURL;
    }

    // TODO: Needs to copy/paste this method because FacebookProcessor has catalina specific param types :(
    public boolean initialInteraction(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession();
        Map<String, String> params = new HashMap<String, String>();
        params.put(OAuthConstants.REDIRECT_URI_PARAMETER, returnURL);
        params.put(OAuthConstants.CLIENT_ID_PARAMETER, clientID);

        if (scope != null) {
            params.put(OAuthConstants.SCOPE_PARAMETER, scope);
        }

        String location = new StringBuilder(FacebookConstants.SERVICE_URL).append("?").append(util.createQueryString(params))
                .toString();
        try {
            session.setAttribute("STATE", STATES.AUTH.name());
            if (trace)
                log.trace("Redirect:" + location);
            response.sendRedirect(location);
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Principal getPrincipal(HttpServletRequest request, HttpServletResponse response) {
        return handleAuthenticationResponse(request, response);
    }

    // Needs to be forked because we need to change private method readInIdentity :-/
    protected Principal handleAuthenticationResponse(HttpServletRequest request, HttpServletResponse response) {
        String error = request.getParameter(OAuthConstants.ERROR_PARAMETER);
        if (error != null) {
            throw new RuntimeException("error:" + error);
        } else {
            String returnUrl = returnURL;
            String authorizationCode = request.getParameter(OAuthConstants.CODE_PARAMETER);
            if (authorizationCode == null) {
                log.error("Authorization code parameter not found");
                return null;
            }

            URLConnection connection = sendAccessTokenRequest(returnUrl, authorizationCode, response);

            Map<String, String> params = formUrlDecode(readUrlContent(connection));
            String accessToken = params.get(OAuthConstants.ACCESS_TOKEN_PARAMETER);
            String expires = params.get(FacebookConstants.EXPIRES);

            if (trace)
                log.trace("Access Token=" + accessToken + " :: Expires=" + expires);

            if (accessToken == null) {
                throw new RuntimeException("No access token found");
            }

            return readInIdentity(request, response, accessToken, returnUrl);
        }
    }

    // Needs to be forked because we need to change private method readInIdentity :-/
    private Map<String, String> formUrlDecode(String encodedData) {
        Map<String, String> params = new HashMap<String, String>();
        String[] elements = encodedData.split("&");
        for (String element : elements) {
            String[] pair = element.split("=");
            if (pair.length == 2) {
                String paramName = pair[0];
                String paramValue;
                try {
                    paramValue = URLDecoder.decode(pair[1], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                params.put(paramName, paramValue);
            } else {
                throw new RuntimeException("Unexpected name-value pair in response: " + element);
            }
        }
        return params;
    }

    // Needs to be forked because we need to change private method readInIdentity :-/
    private String readUrlContent(URLConnection connection) {
        StringBuilder result = new StringBuilder();
        try {
            Reader reader = new InputStreamReader(connection.getInputStream());
            char[] buffer = new char[50];
            int nrOfChars;
            while ((nrOfChars = reader.read(buffer)) != -1) {
                result.append(buffer, 0, nrOfChars);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result.toString();
    }

    // Needs to be forked and overriden because we need username
    private Principal readInIdentity(HttpServletRequest request, HttpServletResponse response, String accessToken,
                                     String returnUrl) {
        FacebookPrincipal facebookPrincipal = null;
        try {
            String urlString = new StringBuilder(FacebookConstants.PROFILE_ENDPOINT_URL).append("?access_token=")
                    .append(URLEncoder.encode(accessToken, "UTF-8")).toString();
            if (trace)
                log.trace("Profile read:" + urlString);

            URL profileUrl = new URL(urlString);
            String profileContent = readUrlContent(profileUrl.openConnection());
            JSONObject jsonObject = new JSONObject(profileContent);

            facebookPrincipal = new FacebookPrincipal();
            facebookPrincipal.setAccessToken(accessToken);
            facebookPrincipal.setId(jsonObject.getString("id"));
            facebookPrincipal.setUsername(jsonObject.getString("username"));
            facebookPrincipal.setName(jsonObject.getString("name"));
            facebookPrincipal.setFirstName(jsonObject.getString("first_name"));
            facebookPrincipal.setLastName(jsonObject.getString("last_name"));
            facebookPrincipal.setGender(jsonObject.getString("gender"));
            facebookPrincipal.setTimezone(jsonObject.getString("timezone"));
            facebookPrincipal.setLocale(jsonObject.getString("locale"));
            if (jsonObject.getString("email") != null) {
                facebookPrincipal.setEmail(jsonObject.getString("email"));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return facebookPrincipal;
    }
}
