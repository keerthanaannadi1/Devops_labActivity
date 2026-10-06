package com.elms.attendance;

import com.elms.attendance.AttendanceController.AttendanceRequest;
import com.elms.attendance.AttendanceController.AttendanceResponse;
import com.elms.common.CurrentUser;
import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance")
public class AttendanceController {

    private final AttendanceService service;
    private final EmployeeRepository employeeRepository;

    public AttendanceController(AttendanceService service, EmployeeRepository employeeRepository) {
        this.service = service;
        this.employeeRepository = employeeRepository;
    }

    public record AttendanceRequest(
            Long employeeId,
            @NotNull LocalDate date,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            @NotNull AttendanceStatus status) {
    }

    public record AttendanceResponse(
            Long id,
            Long employeeId,
            String employeeName,
            LocalDate date,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            AttendanceStatus status) {

        static AttendanceResponse from(Attendance attendance) {
            return new AttendanceResponse(
                    attendance.getId(),
                    attendance.getEmployee().getId(),
                    attendance.getEmployee().getName(),
                    attendance.getDate(),
                    attendance.getCheckInTime(),
                    attendance.getCheckOutTime(),
                    attendance.getStatus());
        }
    }

    @PostMapping("/mark")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Mark attendance for a date (upserts if the day already exists)")
    public AttendanceResponse mark(@Valid @RequestBody AttendanceRequest request) {
        Employee caller = CurrentUser.employee(employeeRepository);
        Long targetId = caller.isHrAdmin() && request.employeeId() != null
                ? request.employeeId()
                : caller.getId();
        return AttendanceResponse.from(service.mark(
                targetId, request.date(), request.checkInTime(), request.checkOutTime(), request.status()));
    }

    @GetMapping("/history")
    @Operation(summary = "Attendance history for the authenticated employee")
    public List<AttendanceResponse> history(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        Long employeeId = CurrentUser.employee(employeeRepository).getId();
        return service.history(employeeId, from, to).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN')")
    @Operation(summary = "Team attendance for a single date")
    public List<AttendanceResponse> team(@RequestParam(required = false) LocalDate date,
                                        @RequestParam(required = false) Long managerId) {
        Employee caller = CurrentUser.employee(employeeRepository);
        Long targetManagerId = caller.isHrAdmin() && managerId != null ? managerId : caller.getId();
        LocalDate day = date == null ? LocalDate.now() : date;
        return service.teamAttendance(targetManagerId, day).stream()
                .map(AttendanceResponse::from)
                .toList();
    }
}
