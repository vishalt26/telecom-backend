package com.telecom.service.web;

import com.telecom.service.domain.TicketType;
import com.telecom.service.security.CurrentUser;
import com.telecom.service.service.TicketService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController extends AbstractTicketController {

    public ComplaintController(TicketService tickets, CurrentUser currentUser) {
        super(tickets, currentUser);
    }

    @Override
    protected TicketType type() {
        return TicketType.COMPLAINT;
    }
}
