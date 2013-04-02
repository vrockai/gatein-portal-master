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

package org.gatein.security.oauth.twitter;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.gatein.common.exception.GateInException;
import org.gatein.security.oauth.common.OAuthProviderProcessor;
import twitter4j.Twitter;
import twitter4j.User;
import twitter4j.auth.AccessToken;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public interface TwitterProcessor {

    TwitterInteractionState processTwitterAuthInteraction(HttpServletRequest request, HttpServletResponse response) throws
            IOException, GateInException;

    String getStringFromAccessToken(AccessToken accessToken);

    TwitterAccessTokenContext getAccessTokenFromString(String accessTokenString);

    Twitter getAuthorizedTwitterInstance(TwitterAccessTokenContext accessTokenContext);
}
