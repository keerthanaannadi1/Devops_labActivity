package com.elms.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elms.attendance.Attendance;
import com.elms.attendance.AttendanceRepository;
import com.elms.attendance.AttendanceStatus;
import com.elms.common.BadRequestException;
import com.elms.common.ConflictException;
import com.elms.common.ForbiddenException;
import com.elms.common.NotFoundException;
import com.elms.config.LeaveQuotaProperties;
import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import com.elms.employee.Role;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    private LeaveService service;

    private Employee employee;
    private Employee manager;
    private Employee otherManager;
    private Employee hrAdmin;

    @BeforeEach
    void setUp() {
        LeaveQuotaProperties quota = new LeaveQuotaProperties();
        service = new LeaveService(leaveRepository, employeeRepository, attendanceRepository, quota);

        employee = new Employee("Priya Sharma", "priya@elms.local", "hash", Role.EMPLOYEE);
        manager = new Employee("Ravi Kumar", "ravi@elms.local", "hash", Role.MANAGER);
        otherManager = new Employee("Someone Else", "else@elms.local", "hash", Role.MANAGER);
        hrAdmin = new Employee("Asha Menon", "asha@elms.local", "hash", Role.HR_ADMIN);

        assignId(employee, 1L);
        assignId(manager, 2L);
        assignId(otherManager, 3L);
        assignId(hrAdmin, 4L);

        employee.setManager(manager);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(otherManager));
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(hrAdmin));
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());
    }

    /**
     * Entity ids are normally assigned by Hibernate on persist. Unit tests have no
     * persistence context, so the identity fields are populated reflectively.
     */
    private void assignId(Employee target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
    }

    private LeaveRequest pendingRequest(int fromDay, int toDay) {
        return new LeaveRequest(employee, LeaveType.CASUAL,
                LocalDate.of(2026, 3, fromDay), LocalDate.of(2026, 3, toDay), "Family function");
    }

    @Test
    @DisplayName("apply() rejects a range where toDate precedes fromDate")
    void rejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.apply(1L, LeaveType.CASUAL,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 5), "x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be before");
    }

    @Test
    @DisplayName("apply() rejects a request that exceeds the annual quota")
    void rejectsOverQuota() {
        assertThatThrownBy(() -> service.apply(1L, LeaveType.COMP_OFF,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30), "x"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("only 5 available");
    }

    @Test
    @DisplayName("apply() rejects an overlapping request")
    void rejectsOverlap() {
        LeaveRequest existing = new LeaveRequest(employee, LeaveType.CASUAL,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), "old");
        when(leaveRepository.findByEmployeeIdAndStatus(1L, LeaveStatus.PENDING))
                .thenReturn(List.of(existing));
        when(leaveRepository.findByEmployeeIdAndStatus(1L, LeaveStatus.APPROVED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.apply(1L, LeaveType.CASUAL,
                LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 14), "x"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    @DisplayName("apply() saves a PENDING request")
    void savesPendingRequest() {
        when(leaveRepository.findByEmployeeIdAndStatusAndFromDateBetween(
                any(), any(), any(), any())).thenReturn(List.of());
        when(leaveRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequest saved = service.apply(1L, LeaveType.CASUAL,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4), "Family function");

        assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(saved.totalDays()).isEqualTo(3);
        assertThat(saved.getReviewedBy()).isNull();
    }

    @Test
    @DisplayName("approve() marks every date in the range as ON_LEAVE")
    void approvalCreatesOnLeaveAttendanceForEachDate() {
        LeaveRequest request = pendingRequest(2, 6);
        when(leaveRepository.findById(10L)).thenReturn(Optional.of(request));
        when(leaveRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attendanceRepository.findByEmployeeIdAndDate(any(), any()))
                .thenReturn(Optional.empty());

        LeaveRequest result = service.approve(10L, 2L);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(result.getReviewedBy()).isEqualTo(manager);

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, org.mockito.Mockito.times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(record -> record.getStatus() == AttendanceStatus.ON_LEAVE);
        assertThat(captor.getAllValues()).extracting(Attendance::getDate)
                .containsExactly(
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 3),
                        LocalDate.of(2026, 3, 4),
                        LocalDate.of(2026, 3, 5),
                        LocalDate.of(2026, 3, 6));
    }

    @Test
    @DisplayName("approve() overwrites an existing PRESENT record instead of duplicating it")
    void approvalUpsertsExistingRecord() {
        LeaveRequest request = pendingRequest(2, 2);
        when(leaveRepository.findById(11L)).thenReturn(Optional.of(request));
        when(leaveRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Attendance existing = new Attendance(employee, LocalDate.of(2026, 3, 2), AttendanceStatus.PRESENT);
        existing.setCheckInTime(java.time.LocalTime.of(9, 30));
        when(attendanceRepository.findByEmployeeIdAndDate(1L, LocalDate.of(2026, 3, 2)))
                .thenReturn(Optional.of(existing));

        service.approve(11L, 2L);

        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.ON_LEAVE);
        assertThat(existing.getCheckInTime()).isNull();
        verify(attendanceRepository, org.mockito.Mockito.times(1)).save(existing);
    }

    @Test
    @DisplayName("approve() refuses a reviewer who is not the requester's manager")
    void rejectsUnauthorizedReviewer() {
        LeaveRequest request = pendingRequest(2, 3);
        when(leaveRepository.findById(12L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(12L, 3L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cannot review");

        verify(leaveRepository, never()).save(any());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve() allows HR_ADMIN to review any employee's request")
    void allowsHrAdminOverride() {
        LeaveRequest request = pendingRequest(2, 2);
        when(leaveRepository.findById(13L)).thenReturn(Optional.of(request));
        when(leaveRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attendanceRepository.findByEmployeeIdAndDate(any(), any())).thenReturn(Optional.empty());

        LeaveRequest result = service.approve(13L, 4L);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(result.getReviewedBy()).isEqualTo(hrAdmin);
    }

    @Test
    @DisplayName("approve() refuses a request that is no longer PENDING")
    void rejectsNonPendingRequest() {
        LeaveRequest request = pendingRequest(2, 3);
        request.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(14L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(14L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    @DisplayName("cancel() only works for the requester and only while PENDING")
    void cancelIsRestricted() {
        LeaveRequest request = pendingRequest(2, 3);
        when(leaveRepository.findById(15L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(15L, 3L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the requester");

        request.setStatus(LeaveStatus.APPROVED);
        assertThatThrownBy(() -> service.cancel(15L, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    @DisplayName("cancel() succeeds for the requester on a PENDING request")
    void cancelSucceeds() {
        LeaveRequest request = pendingRequest(2, 3);
        when(leaveRepository.findById(16L)).thenReturn(Optional.of(request));
        when(leaveRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.cancel(16L, 1L).getStatus()).isEqualTo(LeaveStatus.CANCELLED);
    }

    @Test
    @DisplayName("approve() throws NotFound for an unknown reviewer")
    void unknownReviewer() {
        LeaveRequest request = pendingRequest(2, 3);
        when(leaveRepository.findById(17L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(17L, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("reject() records the reviewer but creates no attendance")
    void rejectCreatesNoAttendance() {
        List<Attendance> saved = new ArrayList<>();
        LeaveRequest request = pendingRequest(2, 5);
        when(leaveRepository.findById(18L)).thenReturn(Optional.of(request));
        when(leaveRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> {
                    LeaveRequest value = invocation.getArgument(0);
                    saved.clear();
                    return value;
                });

        LeaveRequest result = service.reject(18L, 2L);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(result.getReviewedBy()).isEqualTo(manager);
        assertThat(result.getReviewedAt()).isNotNull();
        verify(attendanceRepository, never()).save(any());
    }
}
