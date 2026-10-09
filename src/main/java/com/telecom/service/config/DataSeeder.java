package com.telecom.service.config;

import com.telecom.service.domain.*;
import com.telecom.service.dto.Dtos.RegisterRequest;
import com.telecom.service.repo.TicketHistoryRepository;
import com.telecom.service.repo.TicketRepository;
import com.telecom.service.repo.UserRepository;
import com.telecom.service.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Creates the first admin from ADMIN_EMAIL / ADMIN_PASSWORD (env vars) when no admin exists.
 * Optionally loads FAKE demo data (SEED_DEMO=true) for local development only.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[][] STAFF = {
            {"Anita Rao", "anita.rao@demo.example", "+919000000001"},
            {"Karan Mehta", "karan.mehta@demo.example", "+919000000002"},
            {"Sana Iqbal", "sana.iqbal@demo.example", "+919000000003"}
    };
    private static final String[][] CUSTOMERS = {
            {"Ravi Kumar", "ravi.kumar@demo.example", "+919100000001"},
            {"Priya Nair", "priya.nair@demo.example", "+919100000002"},
            {"Arjun Singh", "arjun.singh@demo.example", "+919100000003"},
            {"Meera Joshi", "meera.joshi@demo.example", "+919100000004"},
            {"Dev Patel", "dev.patel@demo.example", "+919100000005"},
            {"Lakshmi Iyer", "lakshmi.iyer@demo.example", "+919100000006"}
    };
    private static final String[] COMPLAINTS = {
            "Frequent call drops in my area|Calls disconnect after about a minute at home. This has happened for the last three days.",
            "Mobile data very slow|Data speed is under 1 Mbps even though my plan promises 4G speeds. Restarting the phone does not help.",
            "Charged twice on last bill|My last invoice shows the monthly plan amount twice. Please check and refund the duplicate charge.",
            "Broadband keeps disconnecting|The broadband connection drops every evening between 7 and 10 PM and needs a router restart.",
            "SIM not detected after replacement|Replaced my SIM two days ago and the phone still shows no service in every location."
    };
    private static final String[] REQUESTS = {
            "New fibre connection|Please install a new fibre broadband connection at my new apartment. The building already has cabling.",
            "Upgrade to unlimited plan|I would like to move from my current prepaid plan to the unlimited postpaid plan from next month.",
            "Shift connection to new address|Moving house next week and need my broadband connection relocated to the new address."
    };

    private final UserRepository users;
    private final UserService userService;
    private final TicketRepository tickets;
    private final TicketHistoryRepository history;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean seedDemo;
    private final String demoPassword;

    public DataSeeder(UserRepository users, UserService userService, TicketRepository tickets,
                      TicketHistoryRepository history,
                      @Value("${app.admin.email}") String adminEmail,
                      @Value("${app.admin.password}") String adminPassword,
                      @Value("${app.seed-demo}") boolean seedDemo,
                      @Value("${app.demo-password}") String demoPassword) {
        this.users = users;
        this.userService = userService;
        this.tickets = tickets;
        this.history = history;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.seedDemo = seedDemo;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmin();
        if (seedDemo) {
            seedDemoData();
        }
    }

    private void seedAdmin() {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("No admin account exists. Set ADMIN_EMAIL and ADMIN_PASSWORD to create one on startup.");
            return;
        }
        userService.create(new RegisterRequest("Administrator", adminEmail, "+910000000000", adminPassword), Role.ADMIN);
        log.info("Created initial admin account");
    }

    private void seedDemoData() {
        if (demoPassword.length() < 8) {
            log.warn("SEED_DEMO is on but DEMO_PASSWORD is missing or shorter than 8 characters; skipping demo data.");
            return;
        }
        if (users.existsByEmail(STAFF[0][1])) {
            return; // already seeded
        }
        List<User> staff = new ArrayList<>();
        for (String[] s : STAFF) {
            staff.add(userService.create(new RegisterRequest(s[0], s[1], s[2], demoPassword), Role.EMPLOYEE));
        }
        List<User> customers = new ArrayList<>();
        for (String[] c : CUSTOMERS) {
            customers.add(userService.create(new RegisterRequest(c[0], c[1], c[2], demoPassword), Role.CUSTOMER));
        }

        Random rnd = new Random(42);
        Category[] complaintCats = {Category.NETWORK, Category.BILLING, Category.SIM, Category.BROADBAND, Category.OTHER};
        Category[] requestCats = {Category.NEW_CONNECTION, Category.PLAN_CHANGE, Category.BROADBAND};
        Priority[] priorities = Priority.values();
        TicketStatus[] statuses = TicketStatus.values();

        for (int i = 0; i < 28; i++) {
            boolean complaint = i % 4 != 0;
            String[] text = (complaint ? COMPLAINTS[i % COMPLAINTS.length] : REQUESTS[i % REQUESTS.length]).split("\\|");
            Ticket t = new Ticket();
            t.setType(complaint ? TicketType.COMPLAINT : TicketType.SERVICE_REQUEST);
            t.setCategory(complaint ? complaintCats[rnd.nextInt(complaintCats.length)] : requestCats[rnd.nextInt(requestCats.length)]);
            t.setSubject(text[0]);
            t.setDescription(text[1]);
            t.setPriority(priorities[rnd.nextInt(priorities.length)]);
            t.setCustomer(customers.get(rnd.nextInt(customers.size())));
            Instant created = Instant.now().minus(Duration.ofHours(rnd.nextInt(24 * 28) + 2));
            TicketStatus status = statuses[rnd.nextInt(statuses.length)];
            t.setStatus(status);
            if (status != TicketStatus.PENDING) {
                t.setAssignee(staff.get(rnd.nextInt(staff.size())));
            }
            if (status == TicketStatus.RESOLVED || status == TicketStatus.COMPLETED) {
                Instant resolved = created.plus(Duration.ofHours(rnd.nextInt(72) + 1));
                t.setResolvedAt(resolved.isAfter(Instant.now()) ? Instant.now() : resolved);
            }
            t.setCreatedAt(created); // back-dated so charts have a realistic spread
            tickets.save(t);
            TicketHistory h = new TicketHistory();
            h.setTicketId(t.getId());
            h.setToStatus(TicketStatus.PENDING);
            h.setNote("Submitted");
            h.setChangedById(t.getCustomer().getId());
            h.setChangedByName(t.getCustomer().getName());
            history.save(h);
        }
        log.info("Loaded demo data: {} staff, {} customers, 28 tickets (fake data, dev only)", staff.size(), customers.size());
    }
}
