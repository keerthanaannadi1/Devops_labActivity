package com.elms.employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elms.config.SecurityConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
class EmployeeRoleGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService service;

    @MockBean
    private EmployeeRepository repository;

    private Employee plainEmployee;

    @BeforeEach
    void setUp() {
        plainEmployee = new Employee("Priya Sharma", "priya@elms.local", "hash", Role.EMPLOYEE);
        ReflectionTestUtils.setField(plainEmployee, "id", 1L);
        when(repository();
    }

    private java.util.Optional<Employee> repository() {
        return Optional.of(plainEmployee);
    }

    private void when(java.util.Optional<Employee> value) {
        org.mockito.Mockito.when(repository.findByEmail("priya@elms.local")).thenReturn(value);
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("EMPLOYEE cannot list all employees")
    void employeeCannotListAll() throws Exception {
        mockMvc.perform(get("/api/employees")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ravi@elms.local", roles = "MANAGER")
    @DisplayName("MANAGER can list all employees")
    void managerCanListAll() throws Exception {
        org.mockito.Mockito.when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/employees")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("EMPLOYEE cannot create an employee")
    void employeeCannotCreate() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"x@elms.local","role":"EMPLOYEE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "asha@elms.local", roles = "HR_ADMIN")
    @DisplayName("HR_ADMIN can create an employee")
    void hrAdminCanCreate() throws Exception {
        Employee created = new Employee("X", "x@elms.local", "hash", Role.EMPLOYEE);
        ReflectionTestUtils.setField(created, "id", 9L);
        org.mockito.Mockito.when(service.create(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(created);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"x@elms.local","role":"EMPLOYEE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("GET /api/employees/me returns the caller's own profile")
    void meReturnsOwnProfile() throws Exception {
        mockMvc.perform(get("/api/employees/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("priya@elms.local"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
}
