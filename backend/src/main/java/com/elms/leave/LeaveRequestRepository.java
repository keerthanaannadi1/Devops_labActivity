package com.elms.leave;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeIdOrderByFromDateDesc(Long employeeId);

    List<LeaveRequest> findByStatusOrderByFromDateAsc(LeaveStatus status);

    List<LeaveRequest> findByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    @Query("""
            select l from LeaveRequest l
            where l.status = :status
              and l.employee.manager.id = :managerId
            order by l.fromDate asc
            """)
    List<LeaveRequest> findPendingForManager(@Param("managerId") Long managerId, @Param("status") LeaveStatus status);

    List<LeaveRequest> findByEmployeeIdAndStatusAndFromDateBetween(
            Long employeeId, LeaveStatus status, LocalDate from, LocalDate to);
}
