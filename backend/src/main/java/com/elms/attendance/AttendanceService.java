package com.elms.attendance;

import com.elms.employee.Employee;
import com.elms.employee.EmployeeRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.elms.common.BadRequestException;
import com.elms.common.NotFoundException;

@Service
public class AttendanceService {

    private final AttendanceRepository repository;
    private final EmployeeRepository employeeRepository;

    public AttendanceService(AttendanceRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public Attendance mark(Long employeeId, LocalDate date, LocalTime checkIn, LocalTime checkOut,
                           AttendanceStatus status) {
        if (date == null) {
            throw new BadRequestException("date is required");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestException("Cannot mark attendance for a future date: " + date);
        }
        if (status == null) {
            throw new BadRequestException("status is required");
        }
        if (checkIn != null && checkOut != null && checkOut.isBefore(checkIn)) {
            throw new BadRequestException("checkOutTime cannot be before checkInTime");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> NotFoundException.of("Employee", employeeId));

        Attendance attendance = repository.findByEmployeeIdAndDate(employeeId, date)
                .orElseGet(() -> new Attendance(employee, date, status));
        attendance.setEmployee(employee);
        attendance.setStatus(status);
        attendance.setCheckInTime(checkIn);
        attendance.setCheckOutTime(checkOut);
        return repository.save(attendance);
    }

    @Transactional(readOnly = true)
    public List<Attendance> history(Long employeeId, LocalDate from, LocalDate to) {
        if (!employeeRepository.existsById(employeeId)) {
            throw NotFoundException.of("Employee", employeeId);
        }
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (start.isAfter(end)) {
            throw new BadRequestException("from must not be after to");
        }
        return repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId, start, end);
    }

    @Transactional(readOnly = true)
    public List<Attendance> teamAttendance(Long managerId, LocalDate date) {
        List<Long> reportIds = employeeRepository.findByManagerId(managerId).stream()
                .map(Employee::getId)
                .toList();
        if (reportIds.isEmpty()) {
            return List.of();
        }
        return repository.findByEmployeeIdInAndDateBetweenOrderByDateAsc(reportIds, date, date);
    }

    @Transactional(readOnly = true)
    public long presentDays(Long employeeId, LocalDate from, LocalDate to) {
        return repository.findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId, from, to).stream()
                .filter(record -> record.getStatus() == AttendanceStatus.PRESENT)
                .count();
    }
}
