package com.elms.reports;

import com.elms.attendance.AttendanceRepository;
import com.elms.attendance.AttendanceStatus;
import com.elms.common.CurrentUser;
import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import com.elms.leave.LeaveRequestRepository;
import com.elms.leave.LeaveStatus;
import com.elms.leave.LeaveType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public ReportController(EmployeeRepository employeeRepository,
                            AttendanceRepository attendanceRepository,
                            LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public record AttendanceSummary(Long employeeId, String employeeName, long present, long absent,
                                    long halfDay, long onLeave) {
    }

    public record LeaveSummary(Long employeeId, String employeeName, long pending, long approved,
                               long rejected, long cancelled) {
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN')")
    @Operation(summary = "Attendance totals per employee for a period")
    public List<AttendanceSummary> attendanceSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return employeeRepository.findAll().stream()
                .map(employee -> {
                    var records = attendanceRepository
                            .findByEmployeeIdAndDateBetweenOrderByDateAsc(employee.getId(), from, to);
                    return new AttendanceSummary(
                            employee.getId(),
                            employee.getName(),
                            count(records, AttendanceStatus.PRESENT),
                            count(records, AttendanceStatus.ABSENT),
                            count(records, AttendanceStatus.HALF_DAY),
                            count(records, AttendanceStatus.ON_LEAVE));
                })
                .toList();
    }

    @GetMapping("/leaves")
    @Operation(summary = "Leave request counts per employee by status")
    public List<LeaveSummary> leaveSummary() {
        return employeeRepository.findAll().stream()
                .map(employee -> {
                    List<com.elms.leave.LeaveRequest> requests =
                            leaveRequestRepository.findByEmployeeIdOrderByFromDateDesc(employee.getId());
                    return new LeaveSummary(
                            employee.getId(),
                            employee.getName(),
                            requests.stream().filter(r -> r.getStatus() == LeaveStatus.PENDING).count(),
                            requests.stream().filter(r -> r.getStatus() == LeaveStatus.APPROVED).count(),
                            requests.stream().filter(r -> r.getStatus() == LeaveStatus.REJECTED).count(),
                            requests.stream().filter(r -> r.getStatus() == LeaveStatus.CANCELLED).count());
                })
                .toList();
    }

    @GetMapping("/department")
    @Operation(summary = "Head count per department")
    public Map<String, Long> departmentSummary() {
        return employeeRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        employee -> employee.getDepartment() == null ? "Unassigned" : employee.getDepartment().getName(),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    @GetMapping("/me")
    @Operation(summary = "Leave days used per type for the authenticated employee")
    public Map<LeaveType, Long> myLeaveUsage(
            @RequestParam(defaultValue = "2026") int year) {
        Employee caller = CurrentUser.employee(employeeRepository);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        Map<LeaveType, Long> usage = new LinkedHashMap<>();
        for (LeaveType type : LeaveType.values()) {
            long days = leaveRequestRepository
                    .findByEmployeeIdAndStatusAndFromDateBetween(caller.getId(), LeaveStatus.APPROVED, from, to)
                    .stream()
                    .filter(request -> request.getLeaveType() == type)
                    .mapToLong(com.elms.leave.LeaveRequest::totalDays)
                    .sum();
            usage.put(type, days);
        }
        return usage;
    }

    private long count(List<com.elms.attendance.Attendance> records, AttendanceStatus status) {
        return records.stream().filter(record -> record.getStatus() == status).count();
    }
}
