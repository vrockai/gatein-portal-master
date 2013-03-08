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

package org.exoplatform.portal.application;

import org.exoplatform.portal.webui.register.UIRegisterOAuth;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.portal.webui.workspace.UIMaskWorkspace;
import org.exoplatform.portal.webui.workspace.UIPortalApplication;
import org.exoplatform.services.organization.User;
import org.exoplatform.web.application.Application;
import org.exoplatform.web.application.ApplicationLifecycle;
import org.exoplatform.web.application.ApplicationRequestPhaseLifecycle;
import org.exoplatform.web.application.Phase;
import org.exoplatform.web.application.RequestFailure;
import org.exoplatform.web.security.AuthenticationRegistry;
import org.exoplatform.webui.core.UIComponent;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.gatein.security.oauth.utils.OAuthConstants;

/**
 * Lifecycle which is used to display Registration form after successful OAuth authentication. It's used only if user, which was
 * authenticated through OAuth2 doesn't exist in portal
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class OAuthRegistrationLifecycle implements ApplicationLifecycle<PortalRequestContext> {

    /** . */
    private final Logger log = LoggerFactory.getLogger(OAuthRegistrationLifecycle.class);

    private AuthenticationRegistry authRegistry;

    @Override
    public void onInit(Application app) throws Exception {
        this.authRegistry = (AuthenticationRegistry)app.getApplicationServiceContainer().getComponentInstanceOfType(AuthenticationRegistry.class);
    }

    @Override
    public void onStartRequest(Application app, PortalRequestContext context) throws Exception {
        User oauthAuthenticatedUser = (User)authRegistry.getAttributeOfClient(context.getRequest(), OAuthConstants.ATTRIBUTE_AUTHENTICATED_PORTAL_USER);

        if (oauthAuthenticatedUser != null) {
            UIPortalApplication uiApp = Util.getUIPortalApplication();
            UIMaskWorkspace uiMaskWS = uiApp.getChildById(UIPortalApplication.UI_MASK_WS_ID);

            if (log.isTraceEnabled()) {
                log.trace("Found user, which has been authenticated through OAuth. Username is " + oauthAuthenticatedUser.getUserName());
            }

            if (!uiMaskWS.isShow() || !uiMaskWS.getUIComponent().getClass().equals(UIRegisterOAuth.class)) {
                if (log.isTraceEnabled()) {
                    log.trace("Showing registration form for OAuth registration");
                }
                UIComponent uiLogin = uiMaskWS.createUIComponent(UIRegisterOAuth.class, null, null);
                uiMaskWS.setUIComponent(uiLogin);
                Util.getPortalRequestContext().addUIComponentToUpdateByAjax(uiMaskWS);
            }
        }
    }

    @Override
    public void onFailRequest(Application app, PortalRequestContext context, RequestFailure failureType) {
    }

    @Override
    public void onEndRequest(Application app, PortalRequestContext context) throws Exception {
    }

    @Override
    public void onDestroy(Application app) throws Exception {
    }
}
