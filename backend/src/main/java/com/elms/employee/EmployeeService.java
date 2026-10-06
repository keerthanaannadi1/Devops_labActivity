package com.elms.employee;

import com.elms.common.ConflictException;
import com.elms.common.NotFoundException;
import com.elms.config.LeaveQuotaProperties;
import com.elms.department.Department;
import com.elms.department.DepartmentRepository;
import com.elms.leave.LeaveRequest;
import com.elms.leave.LeaveRequestRepository;
import com.elms.leave.LeaveStatus;
import com.elms.leave.LeaveType;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository repository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final LeaveQuotaProperties quotaProperties;

    public EmployeeService(EmployeeRepository repository,
                           DepartmentRepository departmentRepository,
                           LeaveRequestRepository leaveRequestRepository,
                           PasswordEncoder passwordEncoder,
                           LeaveQuotaProperties quotaProperties) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.quotaProperties = quotaProperties;
    }

    @Transactional(readOnly = true)
    public List<Employee> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Employee> findByDepartment(Long departmentId) {
        return repository.findByDepartmentId(departmentId);
    }

    @Transactional(readOnly = true)
    public List<Employee> findDirectReports(Long managerId) {
        return repository.findByManagerId(managerId);
    }

    @Transactional(readOnly = true)
    public Employee findById(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Employee", id));
    }

    @Transactional
    public Employee create(String name, String email, String rawPassword, Role role,
                           Long departmentId, Long managerId, String designation,
                           LocalDate dateOfJoining, String phone) {
        if (repository.existsByEmail(email)) {
            throw new ConflictException("Email already registered: " + email);
        }
        Employee employee = new Employee(name, email, passwordEncoder.encode(rawPassword), role);
        employee.setDesignation(designation);
        employee.setDateOfJoining(dateOfJoining);
        employee.setPhone(phone);
        employee.setDepartment(resolveDepartment(departmentId));
        employee.setManager(managerId == null ? null : findById(managerId));
        return repository.save(employee);
    }

    @Transactional
    public Employee update(Long id, String name, Role role, Long departmentId, Long managerId,
                           String designation, LocalDate dateOfJoining, String phone) {
        Employee employee = findById(id);
        employee.setName(name);
        employee.setRole(role);
        employee.setDesignation(designation);
        employee.setDateOfJoining(dateOfJoining);
        employee.setPhone(phone);
        employee.setDepartment(resolveDepartment(departmentId));
        employee.setManager(managerId == null ? null : findById(managerId));
        return repository.save(employee);
    }

    @Transactional
    public void delete(Long id) {
        Employee employee = findById(id);
        if (!repository.findByManagerId(id).isEmpty()) {
            throw new ConflictException("Reassign direct reports of " + employee.getName() + " before deleting");
        }
        repository.delete(employee);
    }

    @Transactional(readOnly = true)
    public Map<LeaveType, Integer> leaveBalances(Long employeeId, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<LeaveRequest> approved = leaveRequestRepository.findByEmployeeIdAndStatusAndFromDateBetween(
                employeeId, LeaveStatus.APPROVED, from, to);

        Map<LeaveType, Integer> balances = new LinkedHashMap<>();
        for (LeaveType type : LeaveType.values()) {
            int used = approved.stream()
                    .filter(request -> request.getLeaveType() == type)
                    .mapToInt(LeaveRequest::totalDays)
                    .sum();
            balances.put(type, quotaProperties.getQuota().getOrDefault(type, 0) - used);
        }
        return balances;
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> NotFoundException.of("Department", departmentId));
    }
}
