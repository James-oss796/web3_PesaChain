package com.pesachain.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesachain.blockchain.BlockchainOutboxRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "pesachain.blockchain.worker.enabled=false")
@AutoConfigureMockMvc
class BorrowerWorkflowIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine3.24")
            .withDatabaseName("pesachain_workflow_test")
            .withUsername("pesachain_test")
            .withPassword("local-test-only");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("pesachain.simulation.hmac-secret", () -> "test-only-simulation-signing-secret-32-bytes-minimum");
        registry.add("pesachain.blockchain.rpc-url", () -> "http://127.0.0.1:1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BlockchainOutboxRepository blockchainOutbox;

    @Test
    void borrowerCanRegisterSubmitAndReadOnlyTheirOwnApplication() throws Exception {
        TestBorrower first = registerBorrower("First Borrower");
        MvcResult profile = mockMvc.perform(withSession(get("/api/v1/borrowers/me/profile"), first.session()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("First Borrower", objectMapper.readTree(profile.getResponse().getContentAsString()).path("name").asText());

        Csrf csrf = csrf(first.session());
        mockMvc.perform(withSession(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/borrowers/me/profile")
                                .cookie(csrf.cookie())
                                .header(csrf.headerName(), csrf.token())
                                .contentType("application/json")
                                .content("""
                                        {"fullName": "First Borrower Updated", "phone": "+254 733 222 111"}
                                        """),
                        first.session()))
                .andExpect(status().isOk());
        MvcResult refreshedAccount = mockMvc.perform(withSession(get("/api/v1/auth/me"), first.session()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("First Borrower Updated", objectMapper.readTree(refreshedAccount.getResponse().getContentAsString()).path("name").asText());

        MvcResult submitted = mockMvc.perform(withSession(
                        post("/api/v1/applications")
                                .cookie(csrf.cookie())
                                .header(csrf.headerName(), csrf.token())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content("""
                                        {"requestedAmount": 10000.00, "purpose": "Equipment", "requestedTermMonths": 6}
                                        """),
                        first.session()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode application = objectMapper.readTree(submitted.getResponse().getContentAsString());
        String applicationId = application.path("id").asText();
        assertEquals("PENDING", application.path("status").asText());
        assertEquals("KES", application.path("currency").asText());

        MvcResult listed = mockMvc.perform(withSession(get("/api/v1/applications/mine"), first.session()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, objectMapper.readTree(listed.getResponse().getContentAsString()).path("totalItems").asInt());

        TestBorrower second = registerBorrower("Second Borrower");
        mockMvc.perform(withSession(get("/api/v1/applications/" + applicationId), second.session()))
                .andExpect(status().isNotFound());

        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, UUID.fromString(first.userId()));
        assertNotNull(passwordHash);
        assertNotEquals("secure-password-for-test", passwordHash);
        assertEquals("BORROWER", jdbc.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, UUID.fromString(first.userId())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE borrower_id=?", Integer.class,UUID.fromString(first.userId())));
        assertEquals(6,jdbc.queryForObject("SELECT requested_term_months FROM loan_applications WHERE id=?",Integer.class,UUID.fromString(applicationId)));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE actor_id = ? AND action = 'APPLICATION_SUBMITTED'",
                Integer.class,
                UUID.fromString(first.userId())));

        Csrf loginCsrf = csrf(null);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(loginCsrf.cookie())
                        .header(loginCsrf.headerName(), loginCsrf.token())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Login(first.email(), "secure-password-for-test"))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession loginSession = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(loginSession);
        Csrf logoutCsrf = csrf(loginSession);
        mockMvc.perform(withSession(post("/api/v1/auth/logout")
                        .cookie(logoutCsrf.cookie())
                        .header(logoutCsrf.headerName(), logoutCsrf.token()), loginSession))
                .andExpect(status().isNoContent());
        mockMvc.perform(withSession(get("/api/v1/borrowers/me/profile"), loginSession))
                .andExpect(status().isUnauthorized());

        Csrf invalidLoginCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(invalidLoginCsrf.cookie())
                        .header(invalidLoginCsrf.headerName(), invalidLoginCsrf.token())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Login(first.email(), "incorrect-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applicationSubmissionIsIdempotentAcrossRetriesAndRejectsKeyReuseWithDifferentDetails() throws Exception {
        TestBorrower borrower = registerBorrower("Idempotent Applicant");
        Csrf csrf = csrf(borrower.session());
        String key = UUID.randomUUID().toString();
        String request = "{\"requestedAmount\": 2400.00, \"purpose\": \"Supplies\", \"requestedTermMonths\": 4}";
        MvcResult first = mockMvc.perform(withSession(post("/api/v1/applications")
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token()).header("Idempotency-Key", key)
                        .contentType("application/json").content(request), borrower.session()))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(first.getResponse().getContentAsString()).path("id").asText();

        MvcResult retry = mockMvc.perform(withSession(post("/api/v1/applications")
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token()).header("Idempotency-Key", key)
                        .contentType("application/json").content(request), borrower.session()))
                .andExpect(status().isCreated()).andReturn();
        assertEquals(id, objectMapper.readTree(retry.getResponse().getContentAsString()).path("id").asText());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE borrower_id=?", Integer.class, UUID.fromString(borrower.userId())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='APPLICATION_SUBMITTED'", Integer.class, UUID.fromString(id)));

        mockMvc.perform(withSession(post("/api/v1/applications")
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token()).header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"requestedAmount\": 2400.00, \"purpose\": \"Different request\", \"requestedTermMonths\": 4}"), borrower.session()))
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE borrower_id=?", Integer.class, UUID.fromString(borrower.userId())));
    }

    @Test
    void applicationSubmissionRequiresAValidIdempotencyKey() throws Exception {
        TestBorrower borrower = registerBorrower("Missing Key Applicant");
        Csrf csrf = csrf(borrower.session());
        mockMvc.perform(withSession(post("/api/v1/applications")
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token()).contentType("application/json")
                        .content("{\"requestedAmount\": 2400.00, \"purpose\": \"Supplies\", \"requestedTermMonths\": 4}"), borrower.session()))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE borrower_id=?", Integer.class, UUID.fromString(borrower.userId())));
    }

    @Test
    void concurrentApplicationRetriesWithOneKeyCreateExactlyOneRecord() throws Exception {
        TestBorrower borrower = registerBorrower("Concurrent Applicant");
        Csrf csrf = csrf(borrower.session());
        String key = UUID.randomUUID().toString();
        String body = "{\"requestedAmount\": 3100.00, \"purpose\": \"Materials\", \"requestedTermMonths\": 5}";
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var submit = (java.util.concurrent.Callable<MvcResult>) () -> {
                start.await();
                return mockMvc.perform(withSession(post("/api/v1/applications")
                                .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token()).header("Idempotency-Key", key)
                                .contentType("application/json").content(body), borrower.session()))
                        .andReturn();
            };
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            start.countDown();
            MvcResult resultA = first.get();
            MvcResult resultB = second.get();
            assertEquals(201, resultA.getResponse().getStatus());
            assertEquals(201, resultB.getResponse().getStatus());
            assertEquals(objectMapper.readTree(resultA.getResponse().getContentAsString()).path("id").asText(),
                    objectMapper.readTree(resultB.getResponse().getContentAsString()).path("id").asText());
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE borrower_id=?", Integer.class, UUID.fromString(borrower.userId())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor_id=? AND action='APPLICATION_SUBMITTED'", Integer.class, UUID.fromString(borrower.userId())));
    }

    @Test
    void administratorsProvisionStaffAndDeactivationRevokesExistingSession() throws Exception {
        UUID adminId=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'ADMINISTRATOR')",adminId,"admin-"+adminId+"@example.test",passwordEncoder.encode("secure-admin-password"));
        var adminSession=login("admin-"+adminId+"@example.test","secure-admin-password");
        String officerEmail="officer-"+UUID.randomUUID()+"@example.test";
        Csrf csrf=csrf(adminSession);
        MvcResult created=mockMvc.perform(withSession(post("/api/v1/admin/users").cookie(csrf.cookie()).header(csrf.headerName(),csrf.token())
                        .contentType("application/json").content(objectMapper.writeValueAsString(new StaffCreate(officerEmail,"temporary-secure-password","LOAN_OFFICER"))),adminSession))
                .andExpect(status().isCreated()).andReturn();
        assertEquals("LOAN_OFFICER",objectMapper.readTree(created.getResponse().getContentAsString()).path("role").asText());
        UUID officerId=jdbc.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,officerEmail);
        assertEquals(true,jdbc.queryForObject("SELECT password_change_required FROM users WHERE id=?",Boolean.class,officerId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor_id=? AND target_id=? AND action='STAFF_ACCOUNT_CREATED'",Integer.class,adminId,officerId));
        var officerSession=login(officerEmail,"temporary-secure-password");
        mockMvc.perform(withSession(get("/api/v1/admin/users"),officerSession)).andExpect(status().isForbidden());
        Csrf updateCsrf=csrf(adminSession);
        mockMvc.perform(withSession(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/users/"+officerId)
                        .cookie(updateCsrf.cookie()).header(updateCsrf.headerName(),updateCsrf.token()).contentType("application/json").content("{\"status\":\"SUSPENDED\"}"),adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/v1/admin/users"),officerSession)).andExpect(status().isUnauthorized());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='USER_STATUS_CHANGED'",Integer.class,officerId));
    }

    @Test
    void newStaffMustChangeTemporaryPasswordBeforeRoleProtectedAccess() throws Exception {
        UUID adminId=UUID.randomUUID();
        String adminEmail="admin-"+adminId+"@example.test";
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'ADMINISTRATOR')",adminId,adminEmail,passwordEncoder.encode("secure-admin-password"));
        MockHttpSession adminSession=login(adminEmail,"secure-admin-password");
        String officerEmail="officer-"+UUID.randomUUID()+"@example.test";
        Csrf createCsrf=csrf(adminSession);
        mockMvc.perform(withSession(post("/api/v1/admin/users").cookie(createCsrf.cookie()).header(createCsrf.headerName(),createCsrf.token())
                        .contentType("application/json").content(objectMapper.writeValueAsString(new StaffCreate(officerEmail,"temporary-secure-password","LOAN_OFFICER"))),adminSession))
                .andExpect(status().isCreated());
        MockHttpSession officerSession=login(officerEmail,"temporary-secure-password");
        mockMvc.perform(withSession(get("/api/v1/officer/applications"),officerSession)).andExpect(status().isForbidden());
        Csrf changeCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/auth/change-password").cookie(changeCsrf.cookie()).header(changeCsrf.headerName(),changeCsrf.token())
                        .contentType("application/json").content("{\"currentPassword\":\"temporary-secure-password\",\"newPassword\":\"new-secure-password-for-officer\"}"),officerSession))
                .andExpect(status().isOk());
        MvcResult me=mockMvc.perform(withSession(get("/api/v1/auth/me"),officerSession)).andExpect(status().isOk()).andReturn();
        assertEquals(false,objectMapper.readTree(me.getResponse().getContentAsString()).path("passwordChangeRequired").asBoolean());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='PASSWORD_CHANGED' AND target_id=(SELECT id FROM users WHERE email=?)",Integer.class,officerEmail));
    }

    @Test
    void lastActiveAdministratorCannotBeDemotedOrSuspended() throws Exception {
        UUID adminId=UUID.randomUUID();
        String email="sole-admin-"+adminId+"@example.test";
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'ADMINISTRATOR')",adminId,email,passwordEncoder.encode("secure-admin-password"));
        MockHttpSession session=login(email,"secure-admin-password");
        Csrf csrf=csrf(session);
        mockMvc.perform(withSession(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/users/"+adminId)
                        .cookie(csrf.cookie()).header(csrf.headerName(),csrf.token()).contentType("application/json").content("{\"role\":\"LOAN_OFFICER\"}"),session))
                .andExpect(status().isConflict());
        Csrf statusCsrf=csrf(session);
        mockMvc.perform(withSession(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/users/"+adminId)
                        .cookie(statusCsrf.cookie()).header(statusCsrf.headerName(),statusCsrf.token()).contentType("application/json").content("{\"status\":\"SUSPENDED\"}"),session))
                .andExpect(status().isConflict());
        assertEquals("ADMINISTRATOR",jdbc.queryForObject("SELECT role FROM users WHERE id=?",String.class,adminId));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM users WHERE id=?",String.class,adminId));
    }

    @Test
    void loanOfficerCanApproveAdjustedPrincipalAndBorrowerSeesFinalTerms() throws Exception {
        TestBorrower borrower=registerBorrower("Loan Applicant");
        UUID applicationId=submitApplication(borrower,new BigDecimal("15000.00"),6,"Business equipment");
        MockHttpSession officerSession=createOfficerSession();
        MvcResult queue=mockMvc.perform(withSession(get("/api/v1/officer/applications"),officerSession)).andExpect(status().isOk()).andReturn();
        assertEquals(true,objectMapper.readTree(queue.getResponse().getContentAsString()).path("items").toString().contains(applicationId.toString()));
        Csrf officerCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+applicationId+"/decision")
                        .cookie(officerCsrf.cookie()).header(officerCsrf.headerName(),officerCsrf.token())
                        .contentType("application/json").content(objectMapper.writeValueAsString(new Decision("APPROVE",new BigDecimal("12000.00"),6,new BigDecimal("10.00"),"Approved at an affordable amount",null))),officerSession))
                .andExpect(status().isOk());
        assertEquals(0,new BigDecimal("15000.00").compareTo(jdbc.queryForObject("SELECT requested_amount FROM loan_applications WHERE id=?",BigDecimal.class,applicationId)));
        assertEquals(6,jdbc.queryForObject("SELECT requested_term_months FROM loan_applications WHERE id=?",Integer.class,applicationId));
        assertEquals(0,new BigDecimal("12000.00").compareTo(jdbc.queryForObject("SELECT approved_principal FROM loan_applications WHERE id=?",BigDecimal.class,applicationId)));
        assertEquals(0,new BigDecimal("1200.00").compareTo(jdbc.queryForObject("SELECT calculated_interest FROM loan_applications WHERE id=?",BigDecimal.class,applicationId)));
        assertEquals(0,new BigDecimal("13200.00").compareTo(jdbc.queryForObject("SELECT total_repayable FROM loan_applications WHERE id=?",BigDecimal.class,applicationId)));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM loan_applications WHERE id=? AND decision_by IS NOT NULL AND decision_at IS NOT NULL AND decision_rationale='Approved at an affordable amount'",Integer.class,applicationId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM loans WHERE application_id=? AND principal=12000.00 AND term_months=6",Integer.class,applicationId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE source_type='APPLICATION' AND source_id=? AND event_type='LOAN_APPROVED' AND status='PENDING' AND octet_length(aggregate_key)=32",Integer.class,applicationId));
        MvcResult borrowerView=mockMvc.perform(withSession(get("/api/v1/applications/"+applicationId),borrower.session())).andExpect(status().isOk()).andReturn();
        JsonNode app=objectMapper.readTree(borrowerView.getResponse().getContentAsString());
        assertEquals(0,new BigDecimal("15000.00").compareTo(new BigDecimal(app.path("requestedAmount").asText())));
        assertEquals(true,app.path("requestedAmount").isTextual());
        assertEquals(6,app.path("requestedTermMonths").asInt());
        assertEquals(0,new BigDecimal("12000.00").compareTo(new BigDecimal(app.path("approvedPrincipal").asText())));
        assertEquals(0,new BigDecimal("13200.00").compareTo(new BigDecimal(app.path("totalRepayable").asText())));
        Csrf duplicateCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+applicationId+"/decision")
                        .cookie(duplicateCsrf.cookie()).header(duplicateCsrf.headerName(),duplicateCsrf.token())
                        .contentType("application/json").content(objectMapper.writeValueAsString(new Decision("REJECT",null,null,null,"Duplicate attempt","Not now"))),officerSession))
                .andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='APPLICATION_APPROVED'",Integer.class,applicationId));

        UUID loanId=jdbc.queryForObject("SELECT id FROM loans WHERE application_id=?",UUID.class,applicationId);
        MvcResult officerLoans=mockMvc.perform(withSession(get("/api/v1/loans"),officerSession)).andExpect(status().isOk()).andReturn();
        assertEquals(loanId.toString(),objectMapper.readTree(officerLoans.getResponse().getContentAsString()).path("items").get(0).path("id").asText());
        mockMvc.perform(withSession(get("/api/v1/loans"),borrower.session())).andExpect(status().isForbidden());
        mockMvc.perform(withSession(get("/api/v1/loans"),createAdminSession())).andExpect(status().isForbidden());
        Csrf partialCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/loans/"+loanId+"/disbursement")
                        .cookie(partialCsrf.cookie()).header(partialCsrf.headerName(),partialCsrf.token()).contentType("application/json")
                        .content("{\"amount\":11999.00}"),officerSession)).andExpect(status().isBadRequest());
        Csrf fullCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/loans/"+loanId+"/disbursement")
                        .cookie(fullCsrf.cookie()).header(fullCsrf.headerName(),fullCsrf.token()).contentType("application/json")
                        .content("{\"amount\":12000.00}"),officerSession)).andExpect(status().isOk());
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM loans WHERE id=?",String.class,loanId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM disbursements WHERE loan_id=? AND amount=12000.00",Integer.class,loanId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE source_type='DISBURSEMENT' AND source_id=? AND event_type='LOAN_DISBURSED' AND amount_minor_units=1200000 AND status='PENDING'",Integer.class,loanId));
        var history=jdbc.query("SELECT to_status FROM loan_status_history WHERE loan_id=? ORDER BY sequence_id",(rs,n)->rs.getString(1),loanId);
        assertEquals(java.util.List.of("APPROVED","DISBURSED","ACTIVE"),history);
        Csrf duplicateDisbursementCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/loans/"+loanId+"/disbursement")
                        .cookie(duplicateDisbursementCsrf.cookie()).header(duplicateDisbursementCsrf.headerName(),duplicateDisbursementCsrf.token()).contentType("application/json")
                        .content("{\"amount\":12000.00}"),officerSession)).andExpect(status().isConflict());
        MvcResult loanView=mockMvc.perform(withSession(get("/api/v1/loans/"+loanId),borrower.session())).andExpect(status().isOk()).andReturn();
        assertEquals("ACTIVE",objectMapper.readTree(loanView.getResponse().getContentAsString()).path("status").asText());
        assertEquals("DISBURSED",objectMapper.readTree(loanView.getResponse().getContentAsString()).path("history").get(1).path("toStatus").asText());
    }

    @Test
    void requestedPrincipalZeroInterestRoundingRejectionAndDecisionValidation() throws Exception {
        MockHttpSession officerSession=createOfficerSession();
        TestBorrower borrower=registerBorrower("Loan Applicant Two");
        UUID smallId=submitApplication(borrower,new BigDecimal("0.05"),2,"Small amount");
        Csrf borrowerCsrf=csrf(borrower.session());
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+smallId+"/decision")
                        .cookie(borrowerCsrf.cookie()).header(borrowerCsrf.headerName(),borrowerCsrf.token()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Decision("APPROVE",new BigDecimal("0.05"),2,BigDecimal.TEN,"Borrower cannot decide",null))),borrower.session()))
                .andExpect(status().isForbidden());
        Csrf officerCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+smallId+"/decision")
                        .cookie(officerCsrf.cookie()).header(officerCsrf.headerName(),officerCsrf.token()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Decision("APPROVE",new BigDecimal("0.05"),2,new BigDecimal("10"),"Half-up rounding case",null))),officerSession))
                .andExpect(status().isOk());
        assertEquals(0,new BigDecimal("0.01").compareTo(jdbc.queryForObject("SELECT calculated_interest FROM loan_applications WHERE id=?",BigDecimal.class,smallId)));
        assertEquals(0,new BigDecimal("0.06").compareTo(jdbc.queryForObject("SELECT total_repayable FROM loan_applications WHERE id=?",BigDecimal.class,smallId)));
        assertEquals(0,new BigDecimal("0.05").compareTo(jdbc.queryForObject("SELECT approved_principal FROM loan_applications WHERE id=?",BigDecimal.class,smallId)));

        UUID zeroId=submitApplication(borrower,new BigDecimal("1000.00"),3,"Zero interest");
        Csrf zeroCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+zeroId+"/decision")
                        .cookie(zeroCsrf.cookie()).header(zeroCsrf.headerName(),zeroCsrf.token()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Decision("APPROVE",new BigDecimal("1000.00"),2,BigDecimal.ZERO,"Approved at zero interest",null))),officerSession))
                .andExpect(status().isOk());
        assertEquals(3,jdbc.queryForObject("SELECT requested_term_months FROM loan_applications WHERE id=?",Integer.class,zeroId));
        assertEquals(2,jdbc.queryForObject("SELECT approved_term_months FROM loan_applications WHERE id=?",Integer.class,zeroId));
        assertEquals(0,new BigDecimal("0.00").compareTo(jdbc.queryForObject("SELECT calculated_interest FROM loan_applications WHERE id=?",BigDecimal.class,zeroId)));
        assertEquals(0,new BigDecimal("1000.00").compareTo(jdbc.queryForObject("SELECT total_repayable FROM loan_applications WHERE id=?",BigDecimal.class,zeroId)));

        UUID rejectId=submitApplication(borrower,new BigDecimal("500.00"),1,"Rejection test");
        Csrf rejectCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+rejectId+"/decision")
                        .cookie(rejectCsrf.cookie()).header(rejectCsrf.headerName(),rejectCsrf.token()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Decision("REJECT",null,null,null,"  ","Not acceptable"))),officerSession))
                .andExpect(status().isBadRequest());
        Csrf missingReasonCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+rejectId+"/decision")
                        .cookie(missingReasonCsrf.cookie()).header(missingReasonCsrf.headerName(),missingReasonCsrf.token()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Decision("REJECT",null,null,null,"Assessment completed",null))),officerSession))
                .andExpect(status().isBadRequest());
        Csrf validRejectCsrf=csrf(officerSession);
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+rejectId+"/decision")
                        .cookie(validRejectCsrf.cookie()).header(validRejectCsrf.headerName(),validRejectCsrf.token()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Decision("REJECT",null,null,null,"Assessment completed","The requested repayment term is not currently available."))),officerSession))
                .andExpect(status().isOk());
        assertEquals("REJECTED",jdbc.queryForObject("SELECT status FROM loan_applications WHERE id=?",String.class,rejectId));
        assertEquals("The requested repayment term is not currently available.",jdbc.queryForObject("SELECT rejection_reason FROM loan_applications WHERE id=?",String.class,rejectId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE source_type='APPLICATION' AND source_id=? AND event_type='LOAN_REJECTED' AND amount_minor_units=0",Integer.class,rejectId));
    }

    @Test
    void simulatedRepaymentMustBeOfficerVerifiedAndRecordedBeforeBalanceChanges() throws Exception {
        var borrower=registerBorrower("Repayment Borrower");
        var officer=createOfficerSession();
        UUID loanId=createActiveLoan(borrower,officer,new BigDecimal("100.00"));
        UUID eventKey=UUID.randomUUID();
        var event=createSimulation(borrower,loanId,"40.00",eventKey);
        assertEquals("PENDING",event.path("status").asText());
        assertEquals(true,event.path("amount").isTextual());
        assertEquals(false,event.has("signature"));
        assertEquals(false,event.has("idempotencyKey"));
        assertEquals(0,new BigDecimal("100.00").compareTo(jdbc.queryForObject("SELECT outstanding_balance FROM loans WHERE id=?",BigDecimal.class,loanId)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM repayments WHERE loan_id=?",Integer.class,loanId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE event_type='REPAYMENT_RECORDED' AND aggregate_id=?",Integer.class,loanId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE event_type='LOAN_DISBURSED' AND aggregate_id=?",Integer.class,loanId));

        var borrowerCsrf=csrf(borrower.session());
        mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+event.path("id").asText()+"/verify")
                        .cookie(borrowerCsrf.cookie()).header(borrowerCsrf.headerName(),borrowerCsrf.token()),borrower.session()))
                .andExpect(status().isForbidden());
        var borrowerRecordCsrf=csrf(borrower.session());
        mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+event.path("id").asText()+"/record")
                        .cookie(borrowerRecordCsrf.cookie()).header(borrowerRecordCsrf.headerName(),borrowerRecordCsrf.token()),borrower.session()))
                .andExpect(status().isForbidden());
        var adminSession=createAdminSession();
        var adminCsrf=csrf(adminSession);
        mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+event.path("id").asText()+"/verify")
                        .cookie(adminCsrf.cookie()).header(adminCsrf.headerName(),adminCsrf.token()),adminSession))
                .andExpect(status().isForbidden());
        var adminRecordCsrf=csrf(adminSession);
        mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+event.path("id").asText()+"/record")
                        .cookie(adminRecordCsrf.cookie()).header(adminRecordCsrf.headerName(),adminRecordCsrf.token()),adminSession))
                .andExpect(status().isForbidden());

        verifySimulation(officer,event.path("id").asText());
        var repayment=recordSimulation(officer,event.path("id").asText());
        assertEquals(0,new BigDecimal("40.00").compareTo(new BigDecimal(objectMapper.readTree(repayment.getResponse().getContentAsString()).path("amountApplied").asText())));
        assertEquals(true,objectMapper.readTree(repayment.getResponse().getContentAsString()).path("amountApplied").isTextual());
        assertEquals(0,new BigDecimal("60.00").compareTo(jdbc.queryForObject("SELECT outstanding_balance FROM loans WHERE id=?",BigDecimal.class,loanId)));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM repayments WHERE loan_id=?",Integer.class,loanId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE source_type='REPAYMENT' AND event_type='REPAYMENT_RECORDED' AND amount_minor_units=4000 AND status='PENDING' AND aggregate_id=?",Integer.class,loanId));
        assertEquals("RECORDED",jdbc.queryForObject("SELECT status FROM simulated_repayment_events WHERE id=?",String.class,UUID.fromString(event.path("id").asText())));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='SIMULATED_REPAYMENT_VERIFIED' AND target_id=?",Integer.class,UUID.fromString(event.path("id").asText())));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='REPAYMENT_RECORDED' AND target_id=?",Integer.class,loanId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM loan_status_history WHERE loan_id=? AND to_status='COMPLETED'",Integer.class,loanId));
        MvcResult ownRepayments=mockMvc.perform(withSession(get("/api/v1/loans/"+loanId+"/repayments"),borrower.session())).andExpect(status().isOk()).andReturn();
        assertEquals(1,objectMapper.readTree(ownRepayments.getResponse().getContentAsString()).size());

        var borrowerReplayCsrf=csrf(borrower.session());
        mockMvc.perform(withSession(post("/api/v1/loans/"+loanId+"/simulated-repayments")
                        .header("Idempotency-Key",eventKey.toString()).cookie(borrowerReplayCsrf.cookie()).header(borrowerReplayCsrf.headerName(),borrowerReplayCsrf.token())
                        .contentType("application/json").content("{\"amount\":40.00}"),borrower.session()))
                .andExpect(status().isCreated());
        var conflictKeyCsrf=csrf(borrower.session());
        mockMvc.perform(withSession(post("/api/v1/loans/"+loanId+"/simulated-repayments")
                        .header("Idempotency-Key",eventKey.toString()).cookie(conflictKeyCsrf.cookie()).header(conflictKeyCsrf.headerName(),conflictKeyCsrf.token())
                        .contentType("application/json").content("{\"amount\":41.00}"),borrower.session()))
                .andExpect(status().isConflict());
        var duplicateCsrf=csrf(officer);
        mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+event.path("id").asText()+"/verify")
                        .cookie(duplicateCsrf.cookie()).header(duplicateCsrf.headerName(),duplicateCsrf.token()),officer)).andExpect(status().isConflict());
        var duplicateRecordCsrf=csrf(officer);
        mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+event.path("id").asText()+"/record")
                        .cookie(duplicateRecordCsrf.cookie()).header(duplicateRecordCsrf.headerName(),duplicateRecordCsrf.token()),officer)).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM repayments WHERE event_id=?",Integer.class,UUID.fromString(event.path("id").asText())));
        var otherBorrower=registerBorrower("Other Borrower"); var otherCsrf=csrf(otherBorrower.session());
        mockMvc.perform(withSession(post("/api/v1/loans/"+loanId+"/simulated-repayments").header("Idempotency-Key",UUID.randomUUID())
                        .cookie(otherCsrf.cookie()).header(otherCsrf.headerName(),otherCsrf.token()).contentType("application/json").content("{\"amount\":10.00}"),otherBorrower.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(withSession(get("/api/v1/loans/"+loanId+"/repayments"),otherBorrower.session())).andExpect(status().isNotFound());
    }

    @Test
    void finalRepaymentCompletesLoanAndOverpaymentIsRejected() throws Exception {
        var borrower=registerBorrower("Final Payment Borrower"); var officer=createOfficerSession();
        UUID loanId=createActiveLoan(borrower,officer,new BigDecimal("100.00"),new BigDecimal("10.00"));
        assertEquals(0,new BigDecimal("110.00").compareTo(jdbc.queryForObject("SELECT total_repayable FROM loans WHERE id=?",BigDecimal.class,loanId)));
        var stale=createSimulation(borrower,loanId,"110.00",UUID.randomUUID()); verifySimulation(officer,stale.path("id").asText());
        var first=createSimulation(borrower,loanId,"100.00",UUID.randomUUID()); verifySimulation(officer,first.path("id").asText()); recordSimulation(officer,first.path("id").asText()).getResponse().getContentAsString();
        var overpayCsrf=csrf(officer);
        mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+stale.path("id").asText()+"/record")
                        .cookie(overpayCsrf.cookie()).header(overpayCsrf.headerName(),overpayCsrf.token()),officer)).andExpect(status().isConflict());
        assertEquals(0,new BigDecimal("10.00").compareTo(jdbc.queryForObject("SELECT outstanding_balance FROM loans WHERE id=?",BigDecimal.class,loanId)));
        var finalEvent=createSimulation(borrower,loanId,"10.00",UUID.randomUUID()); verifySimulation(officer,finalEvent.path("id").asText()); recordSimulation(officer,finalEvent.path("id").asText());
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM loans WHERE id=?",String.class,loanId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE source_type='LOAN' AND source_id=? AND event_type='LOAN_COMPLETED' AND status='PENDING'",Integer.class,loanId));
        assertEquals(0,new BigDecimal("0.00").compareTo(jdbc.queryForObject("SELECT outstanding_balance FROM loans WHERE id=?",BigDecimal.class,loanId)));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM loan_status_history WHERE loan_id=? AND from_status='ACTIVE' AND to_status='COMPLETED'",Integer.class,loanId));
        var afterCompleteCsrf=csrf(borrower.session());
        mockMvc.perform(withSession(post("/api/v1/loans/"+loanId+"/simulated-repayments").header("Idempotency-Key",UUID.randomUUID())
                        .cookie(afterCompleteCsrf.cookie()).header(afterCompleteCsrf.headerName(),afterCompleteCsrf.token()).contentType("application/json").content("{\"amount\":1.00}"),borrower.session()))
                .andExpect(status().isConflict());
    }

    @Test
    void simultaneousRepaymentsCannotOverpayAndFailedRecordingRollsBackAllWrites() throws Exception {
        var borrower=registerBorrower("Concurrency Borrower"); var officer=createOfficerSession();
        UUID loanId=createActiveLoan(borrower,officer,new BigDecimal("100.00"));
        var first=createSimulation(borrower,loanId,"60.00",UUID.randomUUID()); var second=createSimulation(borrower,loanId,"60.00",UUID.randomUUID());
        verifySimulation(officer,first.path("id").asText()); verifySimulation(officer,second.path("id").asText());
        UUID officerId=jdbc.queryForObject("SELECT id FROM users WHERE role='LOAN_OFFICER' ORDER BY created_at DESC LIMIT 1",UUID.class);
        var principal=new com.pesachain.auth.UserPrincipal(officerId,"concurrency-officer@example.test","x","LOAN_OFFICER","ACTIVE","Officer","+254700000000",false);
        var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{start.await(); try { return repaymentService.record(principal,UUID.fromString(first.path("id").asText())); } catch(com.pesachain.auth.ApiException e) { return null; }});
            var two=pool.submit(()->{start.await(); try { return repaymentService.record(principal,UUID.fromString(second.path("id").asText())); } catch(com.pesachain.auth.ApiException e) { return null; }});
            start.countDown();
            int committed=(one.get()!=null?1:0)+(two.get()!=null?1:0);
            assertEquals(1,committed);
        } finally { pool.shutdownNow(); }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM repayments WHERE loan_id=?",Integer.class,loanId));
        assertEquals(0,new BigDecimal("40.00").compareTo(jdbc.queryForObject("SELECT outstanding_balance FROM loans WHERE id=?",BigDecimal.class,loanId)));

        var rollbackEvent=createSimulation(borrower,loanId,"10.00",UUID.randomUUID()); verifySimulation(officer,rollbackEvent.path("id").asText());
        UUID rollbackEventId=UUID.fromString(rollbackEvent.path("id").asText());
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_repayment_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='REPAYMENT_RECORDED' AND NEW.target_id='"+loanId+"'::uuid THEN RAISE EXCEPTION 'injected audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_repayment_audit_trigger BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION reject_repayment_audit()");
        try {
            var failure=assertThrows(Exception.class,()->repaymentService.record(principal,rollbackEventId));
            assertNotNull(failure);
        } finally {
            jdbc.execute("DROP TRIGGER reject_repayment_audit_trigger ON audit_log"); jdbc.execute("DROP FUNCTION reject_repayment_audit()");
        }
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM repayments WHERE event_id=?",Integer.class,rollbackEventId));
        assertEquals("VERIFIED",jdbc.queryForObject("SELECT status FROM simulated_repayment_events WHERE id=?",String.class,rollbackEventId));
        assertEquals(0,new BigDecimal("40.00").compareTo(jdbc.queryForObject("SELECT outstanding_balance FROM loans WHERE id=?",BigDecimal.class,loanId)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM loan_status_history WHERE loan_id=? AND to_status='COMPLETED'",Integer.class,loanId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE source_type='REPAYMENT' AND source_id=? AND event_type='REPAYMENT_RECORDED'",Integer.class,rollbackEventId));
    }

    @Test
    void outboxInsertFailureRollsBackTheOfficialRepaymentTransaction() throws Exception {
        var borrower=registerBorrower("Outbox Rollback Borrower"); var officer=createOfficerSession();
        UUID loanId=createActiveLoan(borrower,officer,new BigDecimal("100.00"));
        var event=createSimulation(borrower,loanId,"25.00",UUID.randomUUID());
        verifySimulation(officer,event.path("id").asText());
        UUID eventId=UUID.fromString(event.path("id").asText());
        UUID officerId=jdbc.queryForObject("SELECT id FROM users WHERE role='LOAN_OFFICER' ORDER BY created_at DESC LIMIT 1",UUID.class);
        var principal=new com.pesachain.auth.UserPrincipal(officerId,"outbox-officer@example.test","x","LOAN_OFFICER","ACTIVE","Officer","+254700000000",false);
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_blockchain_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='REPAYMENT_RECORDED' THEN RAISE EXCEPTION 'injected outbox failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_blockchain_outbox_trigger BEFORE INSERT ON blockchain_outbox FOR EACH ROW EXECUTE FUNCTION reject_blockchain_outbox()");
        try {
            assertThrows(Exception.class,()->repaymentService.record(principal,eventId));
        } finally {
            jdbc.execute("DROP TRIGGER reject_blockchain_outbox_trigger ON blockchain_outbox");
            jdbc.execute("DROP FUNCTION reject_blockchain_outbox()");
        }
        assertEquals("VERIFIED",jdbc.queryForObject("SELECT status FROM simulated_repayment_events WHERE id=?",String.class,eventId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM repayments WHERE event_id=?",Integer.class,eventId));
        assertEquals(0,new BigDecimal("100.00").compareTo(jdbc.queryForObject("SELECT outstanding_balance FROM loans WHERE id=?",BigDecimal.class,loanId)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM blockchain_outbox WHERE source_type='REPAYMENT' AND source_id=?",Integer.class,eventId));
    }

    @Test
    void blockchainMonitoringEndpointsRespectRoleAndBorrowerOwnership() throws Exception {
        var borrower=registerBorrower("Monitoring Borrower"); var officer=createOfficerSession();
        UUID loanId=createActiveLoan(borrower,officer,new BigDecimal("50.00"));
        var admin=createAdminSession();
        mockMvc.perform(withSession(get("/api/v1/admin/blockchain-events"),admin)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/v1/officer/blockchain-events"),officer)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/v1/admin/blockchain-events"),officer)).andExpect(status().isForbidden());
        mockMvc.perform(withSession(get("/api/v1/officer/blockchain-events"),admin)).andExpect(status().isForbidden());
        mockMvc.perform(withSession(get("/api/v1/admin/blockchain-events"),borrower.session())).andExpect(status().isForbidden());
        mockMvc.perform(withSession(get("/api/v1/loans/"+loanId+"/blockchain-events"),borrower.session())).andExpect(status().isOk());
        var other=registerBorrower("Different Monitoring Borrower");
        mockMvc.perform(withSession(get("/api/v1/loans/"+loanId+"/blockchain-events"),other.session())).andExpect(status().isNotFound());
    }

    @Test
    void administratorCanReadSafeLiveBlockchainHealthButOtherRolesCannot() throws Exception {
        var borrower = registerBorrower("Health Check Borrower");
        var officer = createOfficerSession();
        var administrator = createAdminSession();
        MvcResult result = mockMvc.perform(withSession(get("/api/v1/admin/blockchain-health"), administrator))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(response.path("connection").path("status").asText().matches("AVAILABLE|UNAVAILABLE"));
        assertEquals(31337, response.path("connection").path("expectedChainId").asLong());
        assertTrue(response.path("outboxCounts").has("FAILED"));
        mockMvc.perform(withSession(get("/api/v1/admin/blockchain-health"), officer)).andExpect(status().isForbidden());
        mockMvc.perform(withSession(get("/api/v1/admin/blockchain-health"), borrower.session())).andExpect(status().isForbidden());
        UUID eventId = UUID.randomUUID();
        Csrf officerCsrf = csrf(officer);
        mockMvc.perform(withSession(post("/api/v1/admin/blockchain-events/" + eventId + "/retry")
                        .cookie(officerCsrf.cookie()).header(officerCsrf.headerName(), officerCsrf.token()), officer))
                .andExpect(status().isForbidden());
        Csrf borrowerCsrf = csrf(borrower.session());
        mockMvc.perform(withSession(post("/api/v1/admin/blockchain-events/" + eventId + "/retry")
                        .cookie(borrowerCsrf.cookie()).header(borrowerCsrf.headerName(), borrowerCsrf.token()), borrower.session()))
                .andExpect(status().isForbidden());
        Csrf adminCsrf = csrf(administrator);
        mockMvc.perform(withSession(post("/api/v1/admin/blockchain-events/" + eventId + "/retry")
                        .cookie(adminCsrf.cookie()).header(adminCsrf.headerName(), adminCsrf.token()), administrator))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void exhaustedFailedDeliveryCanBeRequeuedWithoutChangingOperationalStateOrAttemptHistory() throws Exception {
        createAdminSession();
        jdbc.update("UPDATE blockchain_outbox SET next_attempt_at=now()+interval '1 day' WHERE status IN ('PENDING','SUBMITTED','FAILED')");
        UUID eventId = UUID.randomUUID(), sourceId = UUID.randomUUID(), aggregateId = UUID.randomUUID();
        byte[] aggregateKey = new byte[32]; aggregateKey[0] = 3;
        jdbc.update("INSERT INTO blockchain_outbox(event_id,source_type,source_id,aggregate_type,aggregate_id,aggregate_key,event_type,amount_minor_units,status,attempt_count,retryable,last_error_code) "
                        + "VALUES(?,'APPLICATION',?,'APPLICATION',?,?,'LOAN_APPROVED',0,'FAILED',8,false,'RPC_UNAVAILABLE')",
                eventId, sourceId, aggregateId, aggregateKey);
        UUID administratorId = jdbc.queryForObject("SELECT id FROM users WHERE role='ADMINISTRATOR' ORDER BY created_at DESC LIMIT 1", UUID.class);
        assertTrue(blockchainOutbox.queueManualRetry(eventId, administratorId));
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM blockchain_outbox WHERE event_id=?", String.class, eventId));
        assertTrue(jdbc.queryForObject("SELECT retryable AND manual_retry_requested FROM blockchain_outbox WHERE event_id=?", Boolean.class, eventId));
        assertEquals(8, jdbc.queryForObject("SELECT attempt_count FROM blockchain_outbox WHERE event_id=?", Integer.class, eventId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor_id=? AND action='BLOCKCHAIN_DELIVERY_RETRY_QUEUED' AND target_id=?", Integer.class, administratorId, eventId));
        var claimed = blockchainOutbox.claim(UUID.randomUUID(), 8, 60_000);
        assertTrue(claimed.isPresent());
        assertEquals(eventId, claimed.get().eventId());
        assertEquals(9, claimed.get().attemptCount());
        assertEquals("PROCESSING", jdbc.queryForObject("SELECT status FROM blockchain_outbox WHERE event_id=?", String.class, eventId));
    }

    @Test
    void concurrentOutboxWorkersCannotClaimOneEventAtTheSameTime() throws Exception {
        jdbc.update("UPDATE blockchain_outbox SET next_attempt_at=now()+interval '1 day' WHERE status IN ('PENDING','SUBMITTED','FAILED')");
        UUID eventId=UUID.randomUUID(), sourceId=UUID.randomUUID(), aggregateId=UUID.randomUUID();
        byte[] aggregateKey=new byte[32]; aggregateKey[0]=1;
        jdbc.update("INSERT INTO blockchain_outbox(event_id,source_type,source_id,aggregate_type,aggregate_id,aggregate_key,event_type,amount_minor_units) "
                        + "VALUES(?,'APPLICATION',?,'APPLICATION',? ,?,'LOAN_APPROVED',0)",
                eventId,sourceId,aggregateId,aggregateKey);
        var gate=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{gate.await(); return blockchainOutbox.claim(UUID.randomUUID(),8,60000).orElse(null);});
            var two=pool.submit(()->{gate.await(); return blockchainOutbox.claim(UUID.randomUUID(),8,60000).orElse(null);});
            gate.countDown();
            int claimed=(one.get()!=null?1:0)+(two.get()!=null?1:0);
            assertEquals(1,claimed);
            assertEquals("PROCESSING",jdbc.queryForObject("SELECT status FROM blockchain_outbox WHERE event_id=?",String.class,eventId));
        } finally { pool.shutdownNow(); }
    }

    @Autowired private com.pesachain.repayments.RepaymentService repaymentService;
    private UUID createActiveLoan(TestBorrower borrower,MockHttpSession officer,BigDecimal principal) throws Exception {
        return createActiveLoan(borrower,officer,principal,BigDecimal.ZERO);
    }
    private UUID createActiveLoan(TestBorrower borrower,MockHttpSession officer,BigDecimal principal,BigDecimal rate) throws Exception {
        UUID appId=submitApplication(borrower,principal,6,"Repayment test loan"); Csrf decisionCsrf=csrf(officer);
        mockMvc.perform(withSession(post("/api/v1/officer/applications/"+appId+"/decision").cookie(decisionCsrf.cookie()).header(decisionCsrf.headerName(),decisionCsrf.token())
                        .contentType("application/json").content(objectMapper.writeValueAsString(new Decision("APPROVE",principal,6,rate,"Approved for repayment workflow",null))),officer)).andExpect(status().isOk());
        UUID loanId=jdbc.queryForObject("SELECT id FROM loans WHERE application_id=?",UUID.class,appId);
        Csrf disburseCsrf=csrf(officer);
        mockMvc.perform(withSession(post("/api/v1/officer/loans/"+loanId+"/disbursement").cookie(disburseCsrf.cookie()).header(disburseCsrf.headerName(),disburseCsrf.token())
                        .contentType("application/json").content("{\"amount\":"+principal.toPlainString()+"}"),officer)).andExpect(status().isOk());
        return loanId;
    }
    private JsonNode createSimulation(TestBorrower borrower,UUID loanId,String amount,UUID key) throws Exception {
        Csrf csrf=csrf(borrower.session());
        MvcResult result=mockMvc.perform(withSession(post("/api/v1/loans/"+loanId+"/simulated-repayments").header("Idempotency-Key",key)
                        .cookie(csrf.cookie()).header(csrf.headerName(),csrf.token()).contentType("application/json").content("{\"amount\":"+amount+"}"),borrower.session()))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
    private void verifySimulation(MockHttpSession officer,String eventId) throws Exception {
        Csrf csrf=csrf(officer);
        mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+eventId+"/verify").cookie(csrf.cookie()).header(csrf.headerName(),csrf.token()),officer))
                .andExpect(status().isOk());
    }
    private MvcResult recordSimulation(MockHttpSession officer,String eventId) throws Exception {
        Csrf csrf=csrf(officer);
        return mockMvc.perform(withSession(post("/api/v1/officer/repayment-events/"+eventId+"/record").cookie(csrf.cookie()).header(csrf.headerName(),csrf.token()),officer))
                .andExpect(status().isOk()).andReturn();
    }
    private MockHttpSession createAdminSession() throws Exception {
        UUID id=UUID.randomUUID(); String email="admin-"+id+"@example.test";
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'ADMINISTRATOR')",id,email,passwordEncoder.encode("secure-admin-password"));
        return login(email,"secure-admin-password");
    }

    private UUID submitApplication(TestBorrower borrower,BigDecimal amount,int term,String purpose) throws Exception {
        Csrf csrf=csrf(borrower.session());
        MvcResult result=mockMvc.perform(withSession(post("/api/v1/applications").cookie(csrf.cookie()).header(csrf.headerName(),csrf.token()).header("Idempotency-Key",UUID.randomUUID().toString())
                        .contentType("application/json").content(objectMapper.writeValueAsString(new ApplicationRequest(amount,purpose,term))),borrower.session()))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private MockHttpSession createOfficerSession() throws Exception {
        UUID id=UUID.randomUUID(); String email="loan-officer-"+id+"@example.test";
        jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES(?,?,?,'LOAN_OFFICER')",id,email,passwordEncoder.encode("temporary-officer-password"));
        return login(email,"temporary-officer-password");
    }

    @Test
    void publicRegistrationCannotChoosePrivilegedRole() throws Exception {
        String email="role-attempt-"+UUID.randomUUID()+"@example.test";
        Csrf csrf=csrf(null);
        mockMvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header(csrf.headerName(),csrf.token())
                        .contentType("application/json").content("{\"fullName\":\"Role Attempt\",\"email\":\""+email+"\",\"phone\":\"+254712345678\",\"password\":\"secure-password-for-test\",\"role\":\"ADMINISTRATOR\"}"))
                .andExpect(status().isCreated());
        assertEquals("BORROWER",jdbc.queryForObject("SELECT role FROM users WHERE email=?",String.class,email));
    }

    @Test
    void administratorActivityIsReadOnlyRoleRestrictedAndPrivacyMinimized() throws Exception {
        UUID target = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_log(actor_role,action,target_type,target_id,metadata) VALUES('LOAN_OFFICER','APPLICATION_APPROVED','APPLICATION',?,?::jsonb)",
                target, "{\"internalNote\":\"must not be exposed\"}");

        var borrower = registerBorrower("Activity Reader");
        mockMvc.perform(withSession(get("/api/v1/admin/activity"), borrower.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/activity"))
                .andExpect(status().isUnauthorized());

        var administrator = createAdminSession();
        MvcResult response = mockMvc.perform(withSession(get("/api/v1/admin/activity?page=0&size=20"), administrator))
                .andExpect(status().isOk()).andReturn();
        JsonNode page = objectMapper.readTree(response.getResponse().getContentAsString());
        JsonNode item = null;
        for (JsonNode candidate : page.path("items")) {
            if (target.toString().equals(candidate.path("targetId").asText())) item = candidate;
        }
        assertNotNull(item);
        assertEquals("LOAN_OFFICER", item.path("actorRole").asText());
        assertEquals("APPLICATION_APPROVED", item.path("action").asText());
        assertEquals(false, item.has("actorId"));
        assertEquals(false, item.has("metadata"));
        assertEquals(false, response.getResponse().getContentAsString().contains("must not be exposed"));

        var officer = createOfficerSession();
        mockMvc.perform(withSession(get("/api/v1/admin/activity"), officer))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanFilterAuditActivityAndReadAggregatedReportsWithoutBorrowerDetails() throws Exception {
        UUID target = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_log(actor_role,action,target_type,target_id) VALUES('LOAN_OFFICER','REPORT_SEARCH_MARKER','LOAN',?)", target);
        TestBorrower borrower = registerBorrower("Report Privacy Borrower");
        submitApplication(borrower, new BigDecimal("2500.00"), 3, "Working capital");
        MockHttpSession administrator = createAdminSession();

        MvcResult filtered = mockMvc.perform(withSession(get("/api/v1/admin/activity?q=report&actorRole=LOAN_OFFICER&result=SUCCEEDED"), administrator))
                .andExpect(status().isOk()).andReturn();
        JsonNode filteredPage = objectMapper.readTree(filtered.getResponse().getContentAsString());
        assertEquals(1, filteredPage.path("totalItems").asInt());
        assertEquals("REPORT_SEARCH_MARKER", filteredPage.path("items").get(0).path("action").asText());

        MvcResult reports = mockMvc.perform(withSession(get("/api/v1/admin/reports/overview?months=6"), administrator))
                .andExpect(status().isOk()).andReturn();
        JsonNode report = objectMapper.readTree(reports.getResponse().getContentAsString());
        assertTrue(report.path("applications").path("pending").asLong() >= 1);
        assertEquals(6, report.path("monthlyTrend").size());
        assertFalse(reports.getResponse().getContentAsString().contains(borrower.email()));
        assertFalse(reports.getResponse().getContentAsString().contains("Report Privacy Borrower"));

        MockHttpSession officer = createOfficerSession();
        mockMvc.perform(withSession(get("/api/v1/admin/reports/overview"), officer)).andExpect(status().isForbidden());
    }

    @Test
    void officerApplicationSearchIsPrefixFilteredAndStillRoleProtected() throws Exception {
        TestBorrower matching = registerBorrower("Olivia Searchable");
        TestBorrower other = registerBorrower("Zuri Different");
        submitApplication(matching, new BigDecimal("1200.00"), 2, "Tools");
        submitApplication(other, new BigDecimal("1300.00"), 3, "Supplies");
        MockHttpSession officer = createOfficerSession();

        MvcResult response = mockMvc.perform(withSession(get("/api/v1/officer/applications?q=oli"), officer))
                .andExpect(status().isOk()).andReturn();
        JsonNode page = objectMapper.readTree(response.getResponse().getContentAsString());
        assertEquals(1, page.path("totalItems").asInt());
        assertEquals("Olivia Searchable", page.path("items").get(0).path("borrowerName").asText());
        TestBorrower borrower = registerBorrower("Unauthorized Search");
        mockMvc.perform(withSession(get("/api/v1/officer/applications?q=ol"), borrower.session())).andExpect(status().isForbidden());
    }

    private MockHttpSession login(String email,String password) throws Exception {
        Csrf csrf=csrf(null);
        MvcResult result=mockMvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header(csrf.headerName(),csrf.token()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Login(email,password)))).andExpect(status().isOk()).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }

    @Test
    void officerCanReviewFactualBorrowerRepaymentHistoryButOtherRolesCannot() throws Exception {
        TestBorrower borrower = registerBorrower("Officer History Borrower");
        MockHttpSession officer = createOfficerSession();
        UUID loanId = createActiveLoan(borrower, officer, new BigDecimal("100.00"));
        JsonNode simulated = createSimulation(borrower, loanId, "25.00", UUID.randomUUID());
        verifySimulation(officer, simulated.path("id").asText());
        recordSimulation(officer, simulated.path("id").asText());

        MvcResult history = mockMvc.perform(withSession(get("/api/v1/officer/borrowers/" + borrower.userId() + "/history"), officer))
                .andExpect(status().isOk()).andReturn();
        JsonNode result = objectMapper.readTree(history.getResponse().getContentAsString());
        assertEquals(1, result.path("loanCount").asInt());
        assertEquals(1, result.path("activeLoanCount").asInt());
        assertEquals("25.00", result.path("amountRepaid").asText());
        assertEquals("75.00", result.path("outstandingBalance").asText());
        assertEquals("ACTIVE", result.path("loans").get(0).path("status").asText());
        assertEquals(1, result.path("repayments").size());
        assertEquals("PENDING", result.path("repayments").get(0).path("blockchainStatus").asText());
        assertEquals(3, result.path("lifecycle").size());
        assertFalse(result.has("phone"));
        assertFalse(result.has("email"));

        mockMvc.perform(withSession(get("/api/v1/officer/borrowers/" + borrower.userId() + "/history"), borrower.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(withSession(get("/api/v1/officer/borrowers/" + borrower.userId() + "/history"), createAdminSession()))
                .andExpect(status().isForbidden());
    }

    private TestBorrower registerBorrower(String name) throws Exception {
        String email = "borrower-" + UUID.randomUUID() + "@example.test";
        Csrf csrf = csrf(null);
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Registration(name, email, "+254 712 345 678", "secure-password-for-test"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(registered.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        assertNotNull(session);
        return new TestBorrower(response.path("id").asText(), email, session);
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(withSession(get("/api/v1/auth/csrf"), session))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Csrf(cookie, body.path("headerName").asText(), body.path("token").asText());
    }

    private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request, MockHttpSession session) {
        return session == null ? request : request.session(session);
    }

    private record Registration(String fullName, String email, String phone, String password) {}

    private record Login(String email, String password) {}

    private record StaffCreate(String email,String password,String role) {}

    private record ApplicationRequest(BigDecimal requestedAmount,String purpose,Integer requestedTermMonths) {}

    private record Decision(String decision,BigDecimal approvedPrincipal,Integer approvedTermMonths,
                            BigDecimal flatRatePercent,String rationale,String rejectionReason) {}

    private record TestBorrower(String userId, String email, MockHttpSession session) {}

    private record Csrf(Cookie cookie, String headerName, String token) {}
}
