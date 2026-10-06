package com.elms.leave;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elms.common.ForbiddenException;
import com.elms.common.NotFoundException;
import com.elms.config.LeaveQuotaProperties;
import com.elms.config.SecurityConfig;
import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import com.elms.leave.LeaveController.LeaveResponse;
import java.time.LocalDate;
import java.util.List;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveController.class)
@Import(SecurityConfig.class)
class LeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveService service;

    @MockBean
    private EmployeeRepository employeeRepository;

    private Employee caller;

    @BeforeEach
    void setUp() {
        caller = new Employee("Priya Sharma", "priya@elms.local", "hash",
                com.elms.employee.Role.EMPLOYEE);
        ReflectionTestUtils.setField(caller, "id", 1L);
        when(employeeRepository.findByEmail("priya@elms.local"))
                .thenReturn(java.util.Optional.of(caller));
        when(employeeRepository.findById(1L)).thenReturn(java.util.Optional.of(caller));
    }

    private LeaveRequest sampleRequest(LeaveStatus status) {
        LeaveRequest request = new LeaveRequest(caller, LeaveType.CASUAL,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4), "Family function");
        ReflectionTestUtils.setField(request, "id", 10L);
        request.setStatus(status);
        return request;
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("POST /api/leaves/apply returns 201 with the created request")
    void applyReturnsCreated() throws Exception {
        when(service.apply(eq(1L), eq(LeaveType.CASUAL), any(), any(), any()))
                .thenReturn(sampleRequest(LeaveStatus.PENDING));

        mockMvc.perform(post("/api/leaves/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leaveType": "CASUAL",
                                  "fromDate": "2026-03-02",
                                  "toDate": "2026-03-04",
                                  "reason": "Family function"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalDays").value(3));
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("POST /api/leaves/apply returns 400 when leaveType is missing")
    void applyValidatesBody() throws Exception {
        mockMvc.perform(post("/api/leaves/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDate": "2026-03-02", "toDate": "2026-03-04"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("POST /api/leaves/apply returns 409 when the quota is exceeded")
    void applySurfacesConflict() throws Exception {
        when(service.apply(anyLong(), any(), any(), any(), any()))
                .thenThrow(new com.elms.common.ConflictException("Requested 30 CASUAL day(s) but only 12 available"));

        mockMvc.perform(post("/api/leaves/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaveType": "CASUAL", "fromDate": "2026-03-01", "toDate": "2026-03-30"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only 12 available")));
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("PUT /api/leaves/{id}/approve returns 403 for an unauthorized reviewer")
    void approveSurfacesForbidden() throws Exception {
        when(service.approve(eq(10L), eq(1L)))
                .thenThrow(new ForbiddenException("Priya Sharma cannot review leave for Ravi Kumar"));

        mockMvc.perform(put("/api/leaves/10/approve"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot review")));
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("PUT /api/leaves/{id}/approve returns 404 for an unknown request")
    void approveSurfacesNotFound() throws Exception {
        when(service.approve(eq(999L), eq(1L)))
                .thenThrow(NotFoundException.of("LeaveRequest", 999L));

        mockMvc.perform(put("/api/leaves/999/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("GET /api/leaves/mine returns the caller's requests")
    void mineReturnsList() throws Exception {
        when(service.mine(1L)).thenReturn(List.of(sampleRequest(LeaveStatus.APPROVED)));

        mockMvc.perform(get("/api/leaves/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    @WithMockUser(username = "priya@elms.local", roles = "EMPLOYEE")
    @DisplayName("GET /api/leaves/pending returns an empty list for a plain employee")
    void pendingIsEmptyForEmployee() throws Exception {
        mockMvc.perform(get("/api/leaves/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/leaves/mine returns 401 without authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/leaves/mine"))
                .andExpect(status().isUnauthorized());
    }
}
