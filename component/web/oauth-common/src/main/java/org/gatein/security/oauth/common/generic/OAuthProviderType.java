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

package org.gatein.security.oauth.common.generic;

import org.gatein.security.oauth.common.utils.OAuthConstants;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public enum OAuthProviderType {
    FACEBOOK(OAuthConstants.PROFILE_FACEBOOK_USERNAME,
            OAuthConstants.PROFILE_FACEBOOK_ACCESS_TOKEN,
            OAuthConstants.FACEBOOK_AUTHENTICATION_URL_PATH + "?" + OAuthConstants.PARAM_START_INTERACTION + "=true"),
    GOOGLE(OAuthConstants.PROFILE_GOOGLE_USERNAME,
            OAuthConstants.PROFILE_GOOGLE_ACCESS_TOKEN,
            OAuthConstants.GOOGLE_AUTHENTICATION_URL_PATH + "?" + OAuthConstants.PARAM_START_INTERACTION + "=true");

    private final String userNameAttrName;
    private final String accessTokenAttrName;
    private final String initOAuthURL;

    OAuthProviderType(String userNameAttrName, String accessTokenAttrName, String initOAuthURL) {
        this.userNameAttrName = userNameAttrName;
        this.accessTokenAttrName = accessTokenAttrName;
        this.initOAuthURL = initOAuthURL;
    }

    public String getUserNameAttrName() {
        return userNameAttrName;
    }

    public String getAccessTokenAttrName() {
        return accessTokenAttrName;
    }

    public String getInitOAuthURL(String contextPath) {
        return contextPath + initOAuthURL;
    }
}
