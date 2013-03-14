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
import javax.servlet.http.HttpSession;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.gatein.common.exception.GateInException;
import org.gatein.common.exception.GateInExceptionConstants;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.gatein.security.oauth.common.OAuthConstants;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class TwitterProcessorImpl implements TwitterProcessor {

    private static Logger log = LoggerFactory.getLogger(TwitterProcessorImpl.class);

    private final String redirectURL;
    private final String clientID;
    private final String clientSecret;
    private final TwitterFactory twitterFactory;

    public TwitterProcessorImpl(ExoContainerContext context, InitParams params) {
        this.clientID = params.getValueParam("clientId").getValue();
        this.clientSecret = params.getValueParam("clientSecret").getValue();
        String redirectURLParam = params.getValueParam("redirectURL").getValue();

        if (clientID == null || clientID.length() == 0 || clientID.trim().equals("<<to be replaced>>")) {
            throw new IllegalArgumentException("Property 'clientId' needs to be provided. The value should be " +
                    "clientId of your Twitter application");
        }

        if (clientSecret == null || clientSecret.length() == 0 || clientSecret.trim().equals("<<to be replaced>>")) {
            throw new IllegalArgumentException("Property 'clientSecret' needs to be provided. The value should be " +
                    "clientSecret of your Twitter application");
        }

        if (redirectURLParam == null || redirectURLParam.length() == 0) {
            this.redirectURL = "http://localhost:8080/" + context.getName() + OAuthConstants.TWITTER_AUTHENTICATION_URL_PATH;
        }  else {
            this.redirectURL = redirectURLParam.replaceAll("@@portal.container.name@@", context.getName());
        }

        log.debug("configuration: clientId=" + clientID +
                ", clientSecret=" + clientSecret +
                ", redirectURL=" + redirectURL);

        twitterFactory = new TwitterFactory();
    }

    @Override
    public TwitterInteractionState processTwitterAuthInteraction(HttpServletRequest request, HttpServletResponse response) throws
            IOException, GateInException {
        Twitter twitter = twitterFactory.getInstance();
        twitter.setOAuthConsumer(clientID, clientSecret);

        HttpSession session = request.getSession();

        //See if we are a callback
        RequestToken requestToken = (RequestToken) session.getAttribute(OAuthConstants.ATTRIBUTE_TWITTER_REQUEST_TOKEN);

        try {
            if (requestToken == null) {
                requestToken = twitter.getOAuthRequestToken(redirectURL);

                // Save requestToken to session, but only temporarily until oauth workflow is finished
                session.setAttribute(OAuthConstants.ATTRIBUTE_TWITTER_REQUEST_TOKEN, requestToken);

                // Redirect to twitter to perform authentication
                response.sendRedirect(requestToken.getAuthenticationURL());

                return new TwitterInteractionState(TwitterInteractionState.State.AUTH, requestToken, null, null);
            } else {
                String verifier = request.getParameter(OAuthConstants.OAUTH_VERIFIER);

                // Obtain accessToken and user object from twitter
                AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, verifier);
                User twitterUser = twitter.verifyCredentials();

                // Remove requestToken from session
                session.removeAttribute(OAuthConstants.ATTRIBUTE_TWITTER_REQUEST_TOKEN);

                return new TwitterInteractionState(TwitterInteractionState.State.FINISH, null, accessToken, twitterUser);
            }
        } catch (TwitterException twitterException) {
            throw new GateInException(GateInExceptionConstants.EXCEPTION_CODE_TWITTER_ERROR, twitterException);
        }
    }


}
