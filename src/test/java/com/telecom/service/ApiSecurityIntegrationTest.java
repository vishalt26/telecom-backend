package com.telecom.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the whole app against in-memory H2 (MySQL mode); never touches the real database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-test-secret-test-secret-123456",
        "app.admin.email=admin@test.example",
        "app.admin.password=AdminPass123",
        "app.seed-demo=false",
        "app.rate-limit.auth-per-minute=100000"
})
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final String TICKET = """
            {"category":"NETWORK","subject":"No signal at home","description":"There is no signal at my home since morning.","priority":"HIGH"}""";

    @Autowired
    MockMvc mvc;

    // ---- helpers ----

    private String body(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(req).andExpect(status().is(expectedStatus)).andReturn();
        return r.getResponse().getContentAsString();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String json) {
        return b.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"?([^\",}]+)").matcher(json);
        assertTrue(m.find(), "field " + name + " not found in " + json);
        return m.group(1);
    }

    private record Account(String email, String phone, String password, String token, String id) {}

    private Account newCustomer() throws Exception {
        int n = SEQ.getAndIncrement();
        String email = "user" + n + "@test.example";
        String phone = "+9199000" + String.format("%05d", n);
        String pw = "Passw0rd" + n;
        String reg = body(json(post("/api/v1/auth/register"),
                "{\"name\":\"User " + n + "\",\"email\":\"" + email + "\",\"phone\":\"" + phone + "\",\"password\":\"" + pw + "\"}"), 201);
        return new Account(email, phone, pw, login(email, pw), field(reg, "id"));
    }

    private String login(String emailOrPhone, String password) throws Exception {
        String res = body(json(post("/api/v1/auth/login"),
                "{\"emailOrPhone\":\"" + emailOrPhone + "\",\"password\":\"" + password + "\"}"), 200);
        return field(res, "accessToken");
    }

    private String adminToken() throws Exception {
        return login("admin@test.example", "AdminPass123");
    }

    private Account newEmployee(String adminToken) throws Exception {
        int n = SEQ.getAndIncrement();
        String email = "staff" + n + "@test.example";
        String phone = "+9188000" + String.format("%05d", n);
        String pw = "Staff" + n + "Pass1";
        String res = body(auth(json(post("/api/v1/admin/employees"),
                "{\"name\":\"Staff " + n + "\",\"email\":\"" + email + "\",\"phone\":\"" + phone + "\",\"password\":\"" + pw + "\"}"),
                adminToken), 201);
        return new Account(email, phone, pw, login(email, pw), field(res, "id"));
    }

    // ---- tests ----

    @Test
    void protectedEndpointsRejectMissingOrBadTokens() throws Exception {
        mvc.perform(get("/api/v1/complaints")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/v1/complaints"), "not.a.token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void registrationIsValidatedAndAlwaysCreatesCustomers() throws Exception {
        mvc.perform(json(post("/api/v1/auth/register"),
                "{\"name\":\"Weak Pw\",\"email\":\"weak@test.example\",\"phone\":\"+919876500001\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        String res = body(json(post("/api/v1/auth/register"),
                "{\"name\":\"Sneaky\",\"email\":\"sneaky@test.example\",\"phone\":\"+919876500002\",\"password\":\"Passw0rd99\",\"role\":\"ADMIN\"}"), 201);
        assertEquals("CUSTOMER", field(res, "role"));

        mvc.perform(json(post("/api/v1/auth/register"),
                "{\"name\":\"Sneaky Two\",\"email\":\"sneaky@test.example\",\"phone\":\"+919876500003\",\"password\":\"Passw0rd99\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void customersCannotSeeOrChangeEachOthersTickets() throws Exception {
        Account alice = newCustomer();
        Account bob = newCustomer();
        String id = field(body(auth(json(post("/api/v1/complaints"), TICKET), alice.token()), 201), "id");

        mvc.perform(auth(get("/api/v1/complaints/" + id), alice.token())).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/complaints/" + id), bob.token())).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/complaints/" + id), bob.token())).andExpect(status().isNotFound());
        mvc.perform(auth(json(put("/api/v1/complaints/" + id), TICKET), bob.token())).andExpect(status().isNotFound());
        assertEquals("0", field(body(auth(get("/api/v1/complaints"), bob.token()), 200), "totalElements"));
        // a complaint id is not reachable through the service-requests path
        mvc.perform(auth(get("/api/v1/service-requests/" + id), alice.token())).andExpect(status().isNotFound());
    }

    @Test
    void roleRestrictionsAreEnforcedOnTheServer() throws Exception {
        Account customer = newCustomer();
        String admin = adminToken();
        Account staff = newEmployee(admin);
        String id = field(body(auth(json(post("/api/v1/complaints"), TICKET), customer.token()), 201), "id");

        mvc.perform(auth(get("/api/v1/admin/customers"), customer.token())).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/admin/reports/summary"), staff.token())).andExpect(status().isForbidden());
        mvc.perform(auth(json(patch("/api/v1/complaints/" + id + "/assign"), "{\"employeeId\":" + staff.id() + "}"), customer.token()))
                .andExpect(status().isForbidden());
        mvc.perform(auth(json(patch("/api/v1/complaints/" + id + "/status"), "{\"status\":\"IN_PROGRESS\"}"), customer.token()))
                .andExpect(status().isForbidden());
        mvc.perform(auth(json(post("/api/v1/complaints"), TICKET), admin)).andExpect(status().isForbidden());
        // unassigned tickets are invisible to employees
        mvc.perform(auth(get("/api/v1/complaints/" + id), staff.token())).andExpect(status().isNotFound());
    }

    @Test
    void ticketLifecycleFromSubmissionToCompletion() throws Exception {
        Account customer = newCustomer();
        String admin = adminToken();
        Account staff = newEmployee(admin);
        String id = field(body(auth(json(post("/api/v1/complaints"), TICKET), customer.token()), 201), "id");
        String base = "/api/v1/complaints/" + id;

        mvc.perform(auth(json(patch(base + "/assign"), "{\"employeeId\":" + staff.id() + "}"), admin)).andExpect(status().isOk());
        // cannot skip a step
        mvc.perform(auth(json(patch(base + "/status"), "{\"status\":\"RESOLVED\"}"), staff.token())).andExpect(status().isConflict());
        mvc.perform(auth(json(patch(base + "/status"), "{\"status\":\"IN_PROGRESS\",\"note\":\"On it\"}"), staff.token())).andExpect(status().isOk());
        // customer can no longer edit once work started
        mvc.perform(auth(json(put(base), TICKET), customer.token())).andExpect(status().isConflict());
        mvc.perform(auth(json(patch(base + "/status"), "{\"status\":\"RESOLVED\"}"), staff.token())).andExpect(status().isOk());

        String done = body(auth(json(patch(base + "/status"), "{\"status\":\"COMPLETED\"}"), customer.token()), 200);
        assertEquals("COMPLETED", field(done, "status"));
        // completed is final
        mvc.perform(auth(json(patch(base + "/status"), "{\"status\":\"IN_PROGRESS\"}"), admin)).andExpect(status().isConflict());
        // customer is notified
        String notes = body(auth(get("/api/v1/notifications"), customer.token()), 200);
        assertTrue(notes.contains("is now resolved"), notes);
    }

    @Test
    void openingATicketMarksItsNotificationsAsRead() throws Exception {
        Account customer = newCustomer();
        String admin = adminToken();
        Account staff = newEmployee(admin);
        String id = field(body(auth(json(post("/api/v1/complaints"), TICKET), customer.token()), 201), "id");
        mvc.perform(auth(json(patch("/api/v1/complaints/" + id + "/assign"), "{\"employeeId\":" + staff.id() + "}"), admin))
                .andExpect(status().isOk());

        assertEquals("1", field(body(auth(get("/api/v1/notifications/unread-count"), staff.token()), 200), "unread"));
        mvc.perform(auth(get("/api/v1/complaints/" + id), staff.token())).andExpect(status().isOk());
        assertEquals("0", field(body(auth(get("/api/v1/notifications/unread-count"), staff.token()), 200), "unread"));
        // the customer's own notification about the assignment is untouched until they open it
        assertEquals("1", field(body(auth(get("/api/v1/notifications/unread-count"), customer.token()), 200), "unread"));
    }

    @Test
    void accountLocksAfterRepeatedFailedLogins() throws Exception {
        Account user = newCustomer();
        for (int i = 0; i < 5; i++) {
            mvc.perform(json(post("/api/v1/auth/login"),
                    "{\"emailOrPhone\":\"" + user.email() + "\",\"password\":\"WrongPass1\"}")).andExpect(status().isUnauthorized());
        }
        // even the right password is rejected while locked, with the same generic message
        mvc.perform(json(post("/api/v1/auth/login"),
                "{\"emailOrPhone\":\"" + user.email() + "\",\"password\":\"" + user.password() + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountsLoseAccessImmediately() throws Exception {
        Account customer = newCustomer();
        String admin = adminToken();
        mvc.perform(auth(get("/api/v1/profile"), customer.token())).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/admin/customers/" + customer.id()), admin)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/profile"), customer.token())).andExpect(status().isUnauthorized());
    }
}
