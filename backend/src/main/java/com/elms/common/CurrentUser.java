package com.elms.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static String email() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ForbiddenException("No authenticated user");
        }
        return auth.getName();
    }

    public static Employee employee(EmployeeRepository repository) {
        return repository.findByEmail(email())
                .orElseThrow(() -> new UsernameNotFoundException("Employee not found for " + email()));
    }
}
