package com.elms.config;

import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import com.elms.employee.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(EmployeeRepository repository) {
        return username -> {
            Employee employee = repository.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
            return org.springframework.security.core.userdetails.User
                    .withUsername(employee.getEmail())
                    .password(employee.getPasswordHash())
                    .roles(toRolePrefix(employee.getRole()))
                    .build();
        };
    }

    private String toRolePrefix(Role role) {
        return "ROLE_" + role.name();
    }
}
