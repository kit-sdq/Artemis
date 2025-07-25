package de.tum.cit.aet.artemis.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.SecurityConfiguration;
import de.tum.cit.aet.artemis.core.security.filter.SpaWebFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Test class for the ClientForwardController REST controller.
 *
 * @see SpaWebFilter
 * @see SecurityConfiguration
 */
class ClientForwardTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private JWTCookieService jwtCookieService;

    @Test
    void testClientEndpoint() throws Exception {
        ResultActions perform = request.performMvcRequest(get("/non-existent-mapping"));
        perform.andExpect(status().isOk()).andExpect(forwardedUrl("/"));
    }

    @Test
    void testNestedClientEndpoint() throws Exception {
        request.performMvcRequest(get("/admin/user-management")).andExpect(status().isOk()).andExpect(forwardedUrl("/"));
    }

    @Test
    void getUnmappedNestedDottedEndpoint() throws Exception {
        request.performMvcRequest(get("/foo/bar.js")).andExpect(status().isUnauthorized());
    }

    @Test
    void getWebsocketInfoEndpoint() throws Exception {
        request.performMvcRequest(get("/websocket/info")).andExpect(status().isOk());
    }

    @Test
    void getWebsocketEndpointFailedHandshakeNoCookie() throws Exception {
        request.performMvcRequest(get("/websocket/308/sessionId/websocket")).andExpect(status().isOk()); // Failed handshake without cookie returns 200
    }

    @Test
    void getWebsocketEndpointWithInvalidCookie() throws Exception {
        Cookie cookie = new Cookie(Constants.JWT_COOKIE_NAME, "invalidCookie");
        request.performMvcRequest(get("/websocket/308/sessionId/websocket").cookie(cookie)).andExpect(status().isOk()); // Failed handshake with invalid cookie returns 200
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getWebsocketEndpointWithCookie() throws Exception {
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(true);
        Cookie cookie = new Cookie(responseCookie.getName(), responseCookie.getValue());
        request.performMvcRequest(get("/websocket/308/sessionId/websocket").cookie(cookie)).andExpect(status().isBadRequest())
                .andExpect(content().string("Can \"Upgrade\" only to \"WebSocket\".")); // Handshake is successfull but connection fails to upgrade using MockMvc
    }

    @Test
    void getWebsocketFallbackEndpoint() throws Exception {
        request.performMvcRequest(get("/websocket/308/sessionId/xhr_streaming")).andExpect(status().isNotFound());
    }
}
