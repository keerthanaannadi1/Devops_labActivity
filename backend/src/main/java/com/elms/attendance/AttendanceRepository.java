package com.elms.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByEmployeeIdAndDate(Long employeeId, LocalDate date);

    List<Attendance> findByEmployeeIdAndDateBetweenOrderByDateAsc(Long employeeId, LocalDate from, LocalDate to);

    List<Attendance> findByEmployeeIdAndStatus(Long employeeId, AttendanceStatus status);

    long countByEmployeeIdAndStatus(Long employeeId, AttendanceStatus status);

    List<Attendance> findByDate(LocalDate date);

    List<Attendance> findByEmployeeIdInAndDateBetweenOrderByDateAsc(List<Long> employeeIds, LocalDate from, LocalDate to);

    void deleteByEmployeeId(Long employeeId);
}
