package com.telecom.service.web;

import com.telecom.service.domain.Role;
import com.telecom.service.service.AdminUserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/employees")
public class AdminEmployeeController extends AbstractAdminUserController {

    public AdminEmployeeController(AdminUserService admin) {
        super(admin);
    }

    @Override
    protected Role role() {
        return Role.EMPLOYEE;
    }
}
