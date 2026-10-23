package com.pesachain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class SecurityConfigurationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void csrfEndpointIssuesCookieForBrowserClients() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void protectedApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/borrowers/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loanOfficerRouteRejectsBorrowerAndAdministratorRoles() throws Exception {
        mockMvc.perform(get("/api/v1/officer/applications")
                        .with(SecurityMockMvcRequestPostProcessors.user("borrower").roles("BORROWER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/officer/applications")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMINISTRATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRegistrationCannotSkipCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
}
