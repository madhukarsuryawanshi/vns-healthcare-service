package com.vns.healthcare.repository;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @Query("select a.employee.id as employeeId, a.status as status from Attendance a where a.attendanceDate = :date")
    List<AttendanceStatusProjection> findStatusByDate(@Param("date") LocalDate date);

    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);

    List<Attendance> findByEmployeeId(Long employeeId);

    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate = :date ORDER BY a.employee.id ASC")
    Page<Attendance> findByDatePage(@Param("date") LocalDate date, Pageable pageable);

    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate = :date AND (:status IS NULL OR a.status = :status) ORDER BY a.employee.fullName ASC")
    List<Attendance> findByDateAndStatus(@Param("date") LocalDate date, @Param("status") AttendanceStatus status);

    @Query("SELECT a FROM Attendance a WHERE a.employee.id = :employeeId AND a.attendanceDate BETWEEN :fromDate AND :toDate ORDER BY a.attendanceDate DESC")
    List<Attendance> findByEmployeeAndDateRange(@Param("employeeId") Long employeeId,
                                               @Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate);

    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate = :date ORDER BY a.employee.fullName ASC")
    List<Attendance> findByDateWithEmployee(@Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate BETWEEN :fromDate AND :toDate ORDER BY a.attendanceDate DESC, a.employee.fullName ASC")
    List<Attendance> findByDateRangeWithEmployee(@Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate);

    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate BETWEEN :fromDate AND :toDate ORDER BY a.attendanceDate DESC, a.employee.fullName ASC")
    Page<Attendance> findRangePage(@Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  Pageable pageable);

    long countByAttendanceDateAndStatus(LocalDate date, AttendanceStatus status);
}
