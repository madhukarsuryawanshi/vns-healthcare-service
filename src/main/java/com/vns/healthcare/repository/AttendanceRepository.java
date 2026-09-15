package com.vns.healthcare.repository;

import com.vns.healthcare.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);

    List<Attendance> findByEmployeeId(Long employeeId);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.employee WHERE a.attendanceDate = :date ORDER BY a.employee.fullName")
    List<Attendance> findByDateWithEmployee(@Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a JOIN FETCH a.employee WHERE a.attendanceDate BETWEEN :fromDate AND :toDate ORDER BY a.attendanceDate, a.employee.fullName")
    List<Attendance> findByDateRangeWithEmployee(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    long countByAttendanceDateAndStatus(LocalDate date, com.vns.healthcare.domain.AttendanceStatus status);
}
