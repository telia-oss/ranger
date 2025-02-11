/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 *
 */
package org.apache.ranger.security.web.authentication;

import org.apache.ranger.biz.SessionMgr;
import org.apache.ranger.biz.XUserMgr;
import org.apache.ranger.common.JSONUtil;
import org.apache.ranger.common.PropertiesUtil;
import org.apache.ranger.entity.XXAuthSession;
import org.apache.ranger.view.VXResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 *
 */
public class RangerAuthSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RangerAuthSuccessHandler.class);

    String ajaxLoginSuccessPage;

    @Autowired
    SessionMgr sessionMgr;

    @Autowired
    JSONUtil jsonUtil;

    @Autowired
    XUserMgr xUserMgr;

    public RangerAuthSuccessHandler() {
        super();

        if (ajaxLoginSuccessPage == null) {
            ajaxLoginSuccessPage = PropertiesUtil.getProperty("ranger.ajax.auth.success.page", "/ajax_success.html");
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.security.web.authentication.
     * SavedRequestAwareAuthenticationSuccessHandler
     * #onAuthenticationSuccess(javax.servlet.http.HttpServletRequest,
     * javax.servlet.http.HttpServletResponse,
     * org.springframework.security.core.Authentication)
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        RangerSessionFixationProtectionStrategy rangerSessionFixationProtectionStrategy = new RangerSessionFixationProtectionStrategy();

        rangerSessionFixationProtectionStrategy.onAuthentication(authentication, request, response);

        WebAuthenticationDetails details       = (WebAuthenticationDetails) authentication.getDetails();
        String                   remoteAddress = details != null ? details.getRemoteAddress() : "";
        String                   sessionId     = details != null ? details.getSessionId() : "";
        String                   userAgent     = request.getHeader("User-Agent");

        boolean isValidUser                = sessionMgr.isValidXAUser(authentication.getName());
        String  rangerAuthenticationMethod = PropertiesUtil.getProperty("ranger.authentication.method", "NONE");

        if (!isValidUser && !"NONE".equalsIgnoreCase(rangerAuthenticationMethod)) {
            xUserMgr.createServiceConfigUser(authentication.getName());

            isValidUser = sessionMgr.isValidXAUser(authentication.getName());
        }

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Frame-Options", "DENY");

        VXResponse vXResponse = new VXResponse();

        if (!isValidUser) {
            sessionMgr.processFailureLogin(XXAuthSession.AUTH_STATUS_USER_NOT_FOUND, XXAuthSession.AUTH_TYPE_PASSWORD, authentication.getName(), remoteAddress, sessionId, userAgent);
            authentication.setAuthenticated(false);

            vXResponse.setStatusCode(HttpServletResponse.SC_PRECONDITION_FAILED);
            vXResponse.setMsgDesc("Auth Succeeded but user is not synced yet or federated-user for " + authentication.getName());

            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            response.getWriter().write(jsonUtil.writeObjectAsString(vXResponse));

            // response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
            LOG.info("Auth Succeeded but user is not synced yet for {}", authentication.getName());
        } else {
            String ajaxRequestHeader = request.getHeader("X-Requested-With");

            LOG.debug("commence() X-Requested-With={}", ajaxRequestHeader);

            if ("XMLHttpRequest".equalsIgnoreCase(ajaxRequestHeader)) {
                // logger.debug("Forwarding AJAX login request success to "
                // + ajaxLoginSuccessPage + " for user "
                // + authentication.getName());
                // }
                // request.getRequestDispatcher(ajaxLoginSuccessPage).forward(request,
                // response);

                String jsonResp = "";

                try {
                    vXResponse.setStatusCode(HttpServletResponse.SC_OK);
                    vXResponse.setMsgDesc("Login Successful");

                    response.setStatus(HttpServletResponse.SC_OK);

                    jsonResp = jsonUtil.writeObjectAsString(vXResponse);

                    response.getWriter().write(jsonResp);
                } catch (IOException e) {
                    LOG.info("Error while writing JSON in HttpServletResponse");
                }

                LOG.debug("Sending login success response : {}", jsonResp);

                clearAuthenticationAttributes(request);
            } else {
                String jsonResp = "";

                try {
                    vXResponse.setStatusCode(HttpServletResponse.SC_OK);
                    vXResponse.setMsgDesc("Login Successful");

                    response.setStatus(HttpServletResponse.SC_OK);

                    jsonResp = jsonUtil.writeObjectAsString(vXResponse);

                    response.getWriter().write(jsonResp);
                } catch (IOException e) {
                    LOG.info("Error while writing JSON in HttpServletResponse");
                }
                LOG.debug("Sending login success response : {}", jsonResp);
            }
        }
    }
}
