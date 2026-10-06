package com.elms.employee;

import com.elms.common.CurrentUser;
import com.elms.employee.EmployeeController.EmployeeRequest;
import com.elms.employee.EmployeeController.EmployeeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.elms.leave.LeaveType;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees")
public class EmployeeController {

    private final EmployeeService service;
    private final EmployeeRepository repository;

    public EmployeeController(EmployeeService service, EmployeeRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    public record EmployeeRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            String password,
            @NotNull Role role,
            Long departmentId,
            Long managerId,
            String designation,
            LocalDate dateOfJoining,
            String phone) {
    }

    public record EmployeeResponse(
            Long id,
            String name,
            String email,
            Role role,
            Long departmentId,
            String departmentName,
            Long managerId,
            String managerName,
            String designation,
            LocalDate dateOfJoining,
            String phone) {

        static EmployeeResponse from(Employee employee) {
            return new EmployeeResponse(
                    employee.getId(),
                    employee.getName(),
                    employee.getEmail(),
                    employee.getRole(),
                    employee.getDepartment() == null ? null : employee.getDepartment().getId(),
                    employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                    employee.getManager() == null ? null : employee.getManager().getId(),
                    employee.getManager() == null ? null : employee.getManager().getName(),
                    employee.getDesignation(),
                    employee.getDateOfJoining(),
                    employee.getPhone());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN')")
    @Operation(summary = "List employees, optionally filtered by department")
    public List<EmployeeResponse> list(@RequestParam(required = false) Long departmentId) {
        List<Employee> employees = departmentId == null
                ? service.findAll()
                : service.findByDepartment(departmentId);
        return employees.stream().map(EmployeeResponse::from).toList();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated employee")
    public EmployeeResponse me() {
        return EmployeeResponse.from(CurrentUser.employee(repository));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one employee")
    public EmployeeResponse get(@PathVariable Long id) {
        return EmployeeResponse.from(service.findById(id));
    }

    @GetMapping("/{id}/reports")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN')")
    @Operation(summary = "List direct reports of an employee")
    public List<EmployeeResponse> reports(@PathVariable Long id) {
        return service.findDirectReports(id).stream().map(EmployeeResponse::from).toList();
    }

    @GetMapping("/{id}/leave-balance")
    @Operation(summary = "Remaining leave days per type for a calendar year")
    public Map<LeaveType, Integer> leaveBalance(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "2026") int year) {
        return service.leaveBalances(id, year);
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an employee")
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
        String password = request.password() == null || request.password().isBlank()
                ? "password123"
                : request.password();
        return EmployeeResponse.from(service.create(
                request.name(), request.email(), password, request.role(),
                request.departmentId(), request.managerId(), request.designation(),
                request.dateOfJoining(), request.phone()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    @Operation(summary = "Update an employee")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return EmployeeResponse.from(service.update(
                id, request.name(), request.role(), request.departmentId(), request.managerId(),
                request.designation(), request.dateOfJoining(), request.phone()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an employee")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
