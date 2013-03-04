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

package org.exoplatform.portal.webui.register;

import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.organization.User;
import org.exoplatform.web.security.AuthenticationRegistry;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.gatein.security.oauth.utils.OAuthConstants;

/**
 * Registration form for user, which has been successfully authenticated via OAuth2
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ComponentConfig(template = "system:/groovy/portal/webui/portal/UIRegisterOAuthForm.gtmpl")
public class UIRegisterOAuth extends UIContainer {

    static final String REGISTER_FORM_CONFIG_ID = "UIRegisterFormOAuth";

    private User portalUser;

    public UIRegisterOAuth() throws Exception {
        addChild(UIRegisterForm.class, REGISTER_FORM_CONFIG_ID, null);

        // TODO: Verify if we can always do it in constructor and not in render phase
        AuthenticationRegistry authRegistry = getApplicationComponent(AuthenticationRegistry.class);
        User portalUser = (User)authRegistry.getAttributeOfClient(Util.getPortalRequestContext().getRequest(), OAuthConstants.ATTRIBUTE_AUTHENTICATED_PORTAL_USER);
        if (portalUser == null) {
            // TODO
            throw new RuntimeException("portalUser is null!!");
        }
        this.portalUser = portalUser;
        setupUserToRegisterForm();
    }

    public static class ResetActionListener extends EventListener<UIRegisterForm> {

        @Override
        public void execute(Event<UIRegisterForm> event) throws Exception {
            UIRegisterForm registerForm = event.getSource();
            UIRegisterOAuth uiRegisterOAuth = registerForm.getAncestorOfType(UIRegisterOAuth.class);
            uiRegisterOAuth.setupUserToRegisterForm();
        }

    }

    private void setupUserToRegisterForm() {
        UIRegisterForm uiRegisterForm = getChild(UIRegisterForm.class);
        UIRegisterInputSet uiRegisterInputSet = uiRegisterForm.getChild(UIRegisterInputSet.class);

        uiRegisterInputSet.getUIStringInput(UIRegisterInputSet.USER_NAME).setValue(portalUser.getUserName());
        uiRegisterInputSet.getUIStringInput(UIRegisterInputSet.FIRST_NAME).setValue(portalUser.getFirstName());
        uiRegisterInputSet.getUIStringInput(UIRegisterInputSet.LAST_NAME).setValue(portalUser.getLastName());
        uiRegisterInputSet.getUIStringInput(UIRegisterInputSet.EMAIL_ADDRESS).setValue(portalUser.getEmail());
        uiRegisterInputSet.getUIStringInput(UIRegisterInputSet.PASSWORD).setValue(null);
        uiRegisterInputSet.getUIStringInput(UIRegisterInputSet.CONFIRM_PASSWORD).setValue(null);
        uiRegisterInputSet.getUIStringInput(UIRegisterInputSet.DISPLAY_NAME).setValue(null);
    }
}
