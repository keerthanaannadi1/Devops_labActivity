package com.elms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elms.common.BadRequestException;
import com.elms.common.NotFoundException;
import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import com.elms.employee.Role;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository repository;

    @Mock
    private EmployeeRepository employeeRepository;

    private AttendanceService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(repository, employeeRepository);
        employee = new Employee("Priya Sharma", "priya@elms.local", "hash", Role.EMPLOYEE);
        ReflectionTestUtils.setField(employee, "id", 1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsById(1L)).thenReturn(true);
        when(repository.save(any(Attendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("mark() creates a record when the day does not exist yet")
    void createsNewRecord() {
        LocalDate date = LocalDate.now().minusDays(1);
        when(repository.findByEmployeeIdAndDate(1L, date)).thenReturn(Optional.empty());

        Attendance saved = service.mark(1L, date, LocalTime.of(9, 30), LocalTime.of(18, 0),
                AttendanceStatus.PRESENT);

        assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(saved.getDate()).isEqualTo(date);
        assertThat(saved.getEmployee()).isSameAs(employee);
    }

    @Test
    @DisplayName("mark() updates the existing record instead of inserting a duplicate")
    void updatesExistingRecord() {
        LocalDate date = LocalDate.now().minusDays(2);
        Attendance existing = new Attendance(employee, date, AttendanceStatus.ABSENT);
        when(repository.findByEmployeeIdAndDate(1L, date)).thenReturn(Optional.of(existing));

        Attendance saved = service.mark(1L, date, LocalTime.of(10, 0), null, AttendanceStatus.HALF_DAY);

        assertThat(saved).isSameAs(existing);
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.HALF_DAY);
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("mark() refuses a future date")
    void refusesFutureDate() {
        assertThatThrownBy(() -> service.mark(1L, LocalDate.now().plusDays(1), null, null,
                AttendanceStatus.PRESENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future date");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("mark() refuses a check-out earlier than the check-in")
    void refusesInvertedTimes() {
        assertThatThrownBy(() -> service.mark(1L, LocalDate.now(), LocalTime.of(18, 0),
                LocalTime.of(9, 0), AttendanceStatus.PRESENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be before");
    }

    @Test
    @DisplayName("mark() throws NotFound for an unknown employee")
    void unknownEmployee() {
        when(employeeRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mark(42L, LocalDate.now(), null, null, AttendanceStatus.PRESENT))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("presentDays() counts only PRESENT records")
    void countsPresentDaysOnly() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 7);
        when(repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(1L, from, to)).thenReturn(List.of(
                new Attendance(employee, from, AttendanceStatus.PRESENT),
                new Attendance(employee, from.plusDays(1), AttendanceStatus.PRESENT),
                new Attendance(employee, from.plusDays(2), AttendanceStatus.ABSENT),
                new Attendance(employee, from.plusDays(3), AttendanceStatus.ON_LEAVE),
                new Attendance(employee, from.plusDays(4), AttendanceStatus.HALF_DAY)));

        assertThat(service.presentDays(1L, from, to)).isEqualTo(2);
    }

    @Test
    @DisplayName("teamAttendance() returns an empty list when the manager has no reports")
    void teamAttendanceWithNoReports() {
        when(employeeRepository.findByManagerId(2L)).thenReturn(List.of());

        assertThat(service.teamAttendance(2L, LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("history() rejects an inverted range")
    void historyRejectsInvertedRange() {
        assertThatThrownBy(() -> service.history(1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be after");
    }
}
