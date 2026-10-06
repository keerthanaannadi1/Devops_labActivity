package com.elms.leave;

import com.elms.common.CurrentUser;
import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import com.elms.leave.LeaveController.LeaveRequestBody;
import com.elms.leave.LeaveController.LeaveResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaves")
@Tag(name = "Leave")
public class LeaveController {

    private final LeaveService service;
    private final EmployeeRepository employeeRepository;

    public LeaveController(LeaveService service, EmployeeRepository employeeRepository) {
        this.service = service;
        this.employeeRepository = employeeRepository;
    }

    public record LeaveRequestBody(
            @NotNull LeaveType leaveType,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            String reason) {
    }

    public record LeaveResponse(
            Long id,
            Long employeeId,
            String employeeName,
            LeaveType leaveType,
            LocalDate fromDate,
            LocalDate toDate,
            int totalDays,
            String reason,
            LeaveStatus status,
            Long reviewedById,
            String reviewedByName,
            LocalDateTime reviewedAt) {

        static LeaveResponse from(LeaveRequest request) {
            return new LeaveResponse(
                    request.getId(),
                    request.getEmployee().getId(),
                    request.getEmployee().getName(),
                    request.getLeaveType(),
                    request.getFromDate(),
                    request.getToDate(),
                    request.totalDays(),
                    request.getReason(),
                    request.getStatus(),
                    request.getReviewedBy() == null ? null : request.getReviewedBy().getId(),
                    request.getReviewedBy() == null ? null : request.getReviewedBy().getName(),
                    request.getReviewedAt());
        }
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply for leave")
    public LeaveResponse apply(@Valid @RequestBody LeaveRequestBody request) {
        Employee caller = CurrentUser.employee(employeeRepository);
        return LeaveResponse.from(service.apply(
                caller.getId(), request.leaveType(), request.fromDate(), request.toDate(), request.reason()));
    }

    @GetMapping("/mine")
    @Operation(summary = "Leave requests raised by the authenticated employee")
    public List<LeaveResponse> mine() {
        Employee caller = CurrentUser.employee(employeeRepository);
        return service.mine(caller.getId()).stream().map(LeaveResponse::from).toList();
    }

    @GetMapping("/pending")
    @Operation(summary = "Pending requests awaiting the caller's approval")
    public List<LeaveResponse> pending() {
        Employee caller = CurrentUser.employee(employeeRepository);
        if (caller.getRole() == com.elms.employee.Role.EMPLOYEE) {
            return List.of();
        }
        return service.pendingApprovals(caller.getId()).stream().map(LeaveResponse::from).toList();
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve a pending request; creates ON_LEAVE attendance for each date")
    public LeaveResponse approve(@PathVariable Long id) {
        Employee caller = CurrentUser.employee(employeeRepository);
        return LeaveResponse.from(service.approve(id, caller.getId()));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject a pending request")
    public LeaveResponse reject(@PathVariable Long id) {
        Employee caller = CurrentUser.employee(employeeRepository);
        return LeaveResponse.from(service.reject(id, caller.getId()));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel own pending request")
    public LeaveResponse cancel(@PathVariable Long id) {
        Employee caller = CurrentUser.employee(employeeRepository);
        return LeaveResponse.from(service.cancel(id, caller.getId()));
    }
}
