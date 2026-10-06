package com.elms.employee;

import static org.assertj.core.api.Assertions.assertThat;

import com.elms.attendance.Attendance;
import com.elms.attendance.AttendanceRepository;
import com.elms.attendance.AttendanceStatus;
import com.elms.department.Department;
import com.elms.department.DepartmentRepository;
import com.elms.leave.LeaveRequest;
import com.elms.leave.LeaveRequestRepository;
import com.elms.leave.LeaveStatus;
import com.elms.leave.LeaveType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class EmployeeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    private Department engineering;
    private Employee manager;
    private Employee report;
    private Employee unrelated;

    @BeforeEach
    void setUp() {
        engineering = entityManager.persist(new Department("Engineering"));

        manager = new Employee("Ravi Kumar", "ravi@elms.local", "hash", Role.MANAGER);
        manager.setDepartment(engineering);
        manager = entityManager.persist(manager);

        report = new Employee("Priya Sharma", "priya@elms.local", "hash", Role.EMPLOYEE);
        report.setDepartment(engineering);
        report.setManager(manager);
        report = entityManager.persist(report);

        unrelated = new Employee("Dan Other", "dan@elms.local", "hash", Role.EMPLOYEE);
        unrelated = entityManager.persist(unrelated);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findByEmail locates a single employee")
    void findByEmail() {
        assertThat(employeeRepository.findByEmail("priya@elms.local")).isPresent();
        assertThat(employeeRepository.findByEmail("nobody@elms.local")).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail distinguishes registered from free addresses")
    void existsByEmail() {
        assertThat(employeeRepository.existsByEmail("ravi@elms.local")).isTrue();
        assertThat(employeeRepository.existsByEmail("free@elms.local")).isFalse();
    }

    @Test
    @DisplayName("findByManagerId returns only direct reports")
    void findByManagerId() {
        List<Employee> reports = employeeRepository.findByManagerId(manager.getId());

        assertThat(reports).extracting(Employee::getEmail).containsExactly("priya@elms.local");
    }

    @Test
    @DisplayName("findByDepartmentId returns every employee in the department")
    void findByDepartmentId() {
        List<Employee> members = employeeRepository.findByDepartmentId(engineering.getId());

        assertThat(members).extracting(Employee::getEmail)
                .containsExactlyInAnyOrder("ravi@elms.local", "priya@elms.local");
    }

    @Test
    @DisplayName("UNIQUE(employee_id, date) makes re-marking a day an update, not a duplicate")
    void attendanceIsUniquePerEmployeePerDay() {
        LocalDate day = LocalDate.of(2026, 3, 2);

        entityManager.persist(new Attendance(report, day, AttendanceStatus.PRESENT));
        entityManager.flush();
        entityManager.clear();

        Attendance existing = attendanceRepository.findByEmployeeIdAndDate(report.getId(), day).orElseThrow();
        existing.setStatus(AttendanceStatus.ON_LEAVE);
        entityManager.persist(existing);
        entityManager.flush();
        entityManager.clear();

        List<Attendance> all =
                attendanceRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(report.getId(), day, day);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(AttendanceStatus.ON_LEAVE);
    }

    @Test
    @DisplayName("findPendingForManager returns only the manager's own reports")
    void findPendingForManager() {
        LeaveRequest request = new LeaveRequest(report, LeaveType.CASUAL,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3), "Family function");
        entityManager.persist(request);
        entityManager.flush();
        entityManager.clear();

        List<LeaveRequest> pending =
                leaveRequestRepository.findPendingForManager(manager.getId(), LeaveStatus.PENDING);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEmployee().getEmail()).isEqualTo("priya@elms.local");
        assertThat(pending.get(0).totalDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByEmployeeIdAndStatusAndFromDateBetween only counts requests inside the window")
    void filtersApprovedRequestsByYear() {
        LocalDate lastYear = LocalDate.of(2025, 6, 10);
        LocalDate thisYear = LocalDate.of(2026, 6, 10);

        LeaveRequest old = new LeaveRequest(report, LeaveType.CASUAL, lastYear, lastYear, "old");
        old.setStatus(LeaveStatus.APPROVED);
        entityManager.persist(old);

        LeaveRequest current = new LeaveRequest(report, LeaveType.CASUAL, thisYear, thisYear, "new");
        current.setStatus(LeaveStatus.APPROVED);
        entityManager.persist(current);

        LeaveRequest pending = new LeaveRequest(report, LeaveType.CASUAL,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), "pending");
        entityManager.persist(pending);

        entityManager.flush();
        entityManager.clear();

        List<LeaveRequest> inWindow = leaveRequestRepository.findByEmployeeIdAndStatusAndFromDateBetween(
                report.getId(), LeaveStatus.APPROVED,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(inWindow).hasSize(1);
        assertThat(inWindow.get(0).getReason()).isEqualTo("new");
    }
}
