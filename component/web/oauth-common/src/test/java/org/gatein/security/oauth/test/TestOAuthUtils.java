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

package org.gatein.security.oauth.test;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import junit.framework.TestCase;
import org.gatein.security.oauth.google.GoogleProcessor;
import org.gatein.security.oauth.google.GoogleProcessorImpl;
import org.gatein.security.oauth.twitter.TwitterAccessTokenContext;
import org.gatein.security.oauth.twitter.TwitterProcessor;
import org.gatein.security.oauth.twitter.TwitterProcessorImpl;
import twitter4j.auth.AccessToken;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class TestOAuthUtils extends TestCase {

    public void testTwitterConversion() {
        TwitterProcessor twitterProcessor = new TwitterProcessorImpl();
        AccessToken accessToken = new AccessToken("12557789-koko", "someTokenSecrets");

        String accessTokenStr = twitterProcessor.getStringFromAccessToken(accessToken);
        TwitterAccessTokenContext context = twitterProcessor.getAccessTokenFromString(accessTokenStr);
        assertEquals("12557789-koko", context.getAccessToken());
        assertEquals("someTokenSecrets", context.getAccessTokenSecret());
    }

    public void testGoogleConversion() {
        GoogleProcessor googleProcessor = new GoogleProcessorImpl();
        GoogleTokenResponse tokenData = new GoogleTokenResponse();
        tokenData.setAccessToken("someAccessToken").setExpiresInSeconds(12345l).setIdToken("someIDToken").
                setRefreshToken("someRefreshTkn").setScope("https://someScope");

        String accessTokenStr = googleProcessor.getStringFromToken(tokenData);
        GoogleTokenResponse tokenResponseParsed = googleProcessor.getTokenResponseFromString(accessTokenStr);
        assertEquals("someAccessToken", tokenResponseParsed.getAccessToken());
        assertEquals(new Long(12345), tokenResponseParsed.getExpiresInSeconds());
        assertEquals("someIDToken", tokenResponseParsed.getIdToken());
        assertEquals("someRefreshTkn", tokenResponseParsed.getRefreshToken());
        assertEquals("https://someScope", tokenResponseParsed.getScope());
    }

}
