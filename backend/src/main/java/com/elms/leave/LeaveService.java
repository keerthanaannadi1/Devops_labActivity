package com.elms.leave;

import com.elms.attendance.Attendance;
import com.elms.attendance.AttendanceRepository;
import com.elms.attendance.AttendanceStatus;
import com.elms.config.LeaveQuotaProperties;
import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.elms.common.BadRequestException;
import com.elms.common.ConflictException;
import com.elms.common.ForbiddenException;
import com.elms.common.NotFoundException;

@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveQuotaProperties quotaProperties;

    public LeaveService(LeaveRequestRepository leaveRepository,
                        EmployeeRepository employeeRepository,
                        AttendanceRepository attendanceRepository,
                        LeaveQuotaProperties quotaProperties) {
        this.leaveRepository = leaveRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.quotaProperties = quotaProperties;
    }

    @Transactional
    public LeaveRequest apply(Long employeeId, LeaveType leaveType, LocalDate fromDate, LocalDate toDate,
                              String reason) {
        if (leaveType == null) {
            throw new BadRequestException("leaveType is required");
        }
        if (fromDate == null || toDate == null) {
            throw new BadRequestException("fromDate and toDate are required");
        }
        if (toDate.isBefore(fromDate)) {
            throw new BadRequestException("toDate must not be before fromDate");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> NotFoundException.of("Employee", employeeId));

        long requestedDays = (toDate.toEpochDay() - fromDate.toEpochDay()) + 1;
        int available = quotaProperties.getQuota().getOrDefault(leaveType, 0)
                - usedDays(employeeId, leaveType, fromDate.getYear());
        if (requestedDays > available) {
            throw new ConflictException("Requested " + requestedDays + " " + leaveType
                    + " day(s) but only " + available + " available for " + fromDate.getYear());
        }
        if (hasOverlap(employeeId, fromDate, toDate)) {
            throw new ConflictException("Leave request overlaps an existing pending or approved request");
        }

        return leaveRepository.save(new LeaveRequest(employee, leaveType, fromDate, toDate, reason));
    }

    @Transactional
    public LeaveRequest approve(Long requestId, Long reviewerId) {
        LeaveRequest request = findPending(requestId);
        Employee reviewer = authorizeReviewer(request, reviewerId);

        request.setStatus(LeaveStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        LeaveRequest saved = leaveRepository.save(request);

        markAttendanceOnLeave(saved);
        return saved;
    }

    @Transactional
    public LeaveRequest reject(Long requestId, Long reviewerId) {
        LeaveRequest request = findPending(requestId);
        Employee reviewer = authorizeReviewer(request, reviewerId);

        request.setStatus(LeaveStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        return leaveRepository.save(request);
    }

    @Transactional
    public LeaveRequest cancel(Long requestId, Long employeeId) {
        LeaveRequest request = leaveRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("LeaveRequest", requestId));
        boolean owner = request.getEmployee().getId().equals(employeeId);
        if (!owner) {
            throw new ForbiddenException("Only the requester can cancel a leave request");
        }
        if (!request.isPending()) {
            throw new ConflictException("Only PENDING requests can be cancelled, this one is " + request.getStatus());
        }
        request.setStatus(LeaveStatus.CANCELLED);
        request.setReviewedAt(LocalDateTime.now());
        return leaveRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> mine(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw NotFoundException.of("Employee", employeeId);
        }
        return leaveRepository.findByEmployeeIdOrderByFromDateDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> pendingApprovals(Long managerId) {
        return leaveRepository.findPendingForManager(managerId, LeaveStatus.PENDING);
    }

    /**
     * Upserts one ON_LEAVE attendance row per calendar date in the approved range.
     * Safe to call twice: UNIQUE (employee_id, date) makes the second pass a no-op update.
     */
    private void markAttendanceOnLeave(LeaveRequest request) {
        LocalDate cursor = request.getFromDate();
        while (!cursor.isAfter(request.getToDate())) {
            final LocalDate day = cursor;
            Attendance record = attendanceRepository
                    .findByEmployeeIdAndDate(request.getEmployee().getId(), day)
                    .orElseGet(() -> new Attendance(request.getEmployee(), day, AttendanceStatus.ON_LEAVE));
            if (record.getStatus() != AttendanceStatus.ON_LEAVE) {
                record.setStatus(AttendanceStatus.ON_LEAVE);
                record.setCheckInTime(null);
                record.setCheckOutTime(null);
            }
            attendanceRepository.save(record);
            cursor = cursor.plusDays(1);
        }
    }

    private Employee authorizeReviewer(LeaveRequest request, Long reviewerId) {
        Employee reviewer = employeeRepository.findById(reviewerId)
                .orElseThrow(() -> NotFoundException.of("Employee", reviewerId));

        boolean isHrAdmin = reviewer.isHrAdmin();
        boolean isOwnersManager = request.getEmployee().isDirectReportOf(reviewer);
        if (!isHrAdmin && !isOwnersManager) {
            throw new ForbiddenException(reviewer.getName() + " cannot review leave for "
                    + request.getEmployee().getName());
        }
        if (request.getEmployee().getId().equals(reviewerId)) {
            throw new ForbiddenException("An employee cannot review their own leave request");
        }
        return reviewer;
    }

    private LeaveRequest findPending(Long requestId) {
        LeaveRequest request = leaveRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("LeaveRequest", requestId));
        if (!request.isPending()) {
            throw new ConflictException("Only PENDING requests can be reviewed, this one is " + request.getStatus());
        }
        return request;
    }

    private boolean hasOverlap(Long employeeId, LocalDate from, LocalDate to) {
        return leaveRepository.findByEmployeeIdAndStatus(employeeId, LeaveStatus.PENDING).stream()
                .anyMatch(existing -> !existing.getToDate().isBefore(from) && !existing.getFromDate().isAfter(to))
                || leaveRepository.findByEmployeeIdAndStatus(employeeId, LeaveStatus.APPROVED).stream()
                        .anyMatch(existing -> !existing.getToDate().isBefore(from) && !existing.getFromDate().isAfter(to));
    }

    private int usedDays(Long employeeId, LeaveType type, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        return leaveRepository.findByEmployeeIdAndStatusAndFromDateBetween(employeeId, LeaveStatus.APPROVED, from, to)
                .stream()
                .filter(request -> request.getLeaveType() == type)
                .mapToInt(LeaveRequest::totalDays)
                .sum();
    }
}
