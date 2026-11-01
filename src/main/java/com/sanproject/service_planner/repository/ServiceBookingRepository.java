package com.sanproject.service_planner.repository;

import com.sanproject.service_planner.domain.BookingStatus;
import com.sanproject.service_planner.domain.ServiceBooking;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Booking persistence. spring.jpa.open-in-view=false closes the session before JSON serialization,
 * so *WithDetails queries JOIN FETCH the lazy associations (vehicle, customer, worker, serviceTypes).
 *
 * findByIdForUpdate uses SELECT … FOR UPDATE so concurrent claim/reject cannot both succeed:
 * the second transaction waits, then sees the updated row and fails the status check.
 */
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {

    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ServiceBooking b WHERE b.id = :id")
    Optional<ServiceBooking> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findAllWithDetails();

    /**
     * Ids only, so the page limit runs in SQL; fetching serviceTypes in the same query would make
     * Hibernate paginate in memory. Load the rows afterwards with findAllWithDetailsByIdIn.
     */
    @Query(value = """
            SELECT b.id FROM ServiceBooking b
            LEFT JOIN b.vehicle v
            LEFT JOIN v.customer c
            LEFT JOIN b.assignedWorker w
            WHERE CAST(b.id AS String) LIKE :pattern
               OR LOWER(b.customerName) LIKE :pattern
               OR LOWER(c.email) LIKE :pattern
               OR LOWER(b.carModel) LIKE :pattern
               OR LOWER(v.modelLine) LIKE :pattern
               OR LOWER(v.vin) LIKE :pattern
               OR LOWER(CONCAT(w.firstName, ' ', w.lastName)) LIKE :pattern
               OR LOWER(b.customerDescription) LIKE :pattern
               OR LOWER(b.cancellationReason) LIKE :pattern
               OR (:filterByStatus = true AND b.status IN :statuses)
            ORDER BY b.id DESC
            """,
            countQuery = """
            SELECT COUNT(b) FROM ServiceBooking b
            LEFT JOIN b.vehicle v
            LEFT JOIN v.customer c
            LEFT JOIN b.assignedWorker w
            WHERE CAST(b.id AS String) LIKE :pattern
               OR LOWER(b.customerName) LIKE :pattern
               OR LOWER(c.email) LIKE :pattern
               OR LOWER(b.carModel) LIKE :pattern
               OR LOWER(v.modelLine) LIKE :pattern
               OR LOWER(v.vin) LIKE :pattern
               OR LOWER(CONCAT(w.firstName, ' ', w.lastName)) LIKE :pattern
               OR LOWER(b.customerDescription) LIKE :pattern
               OR LOWER(b.cancellationReason) LIKE :pattern
               OR (:filterByStatus = true AND b.status IN :statuses)
            """)
    Page<Long> findAdminBookingIds(@Param("pattern") String pattern,
                                   @Param("filterByStatus") boolean filterByStatus,
                                   @Param("statuses") Collection<BookingStatus> statuses,
                                   Pageable pageable);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.id IN :ids
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findAllWithDetailsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.id = :id
            """)
    Optional<ServiceBooking> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE v.id = :vehicleId
            """)
    List<ServiceBooking> findByVehicleIdWithDetails(@Param("vehicleId") Long vehicleId);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE v.customer.id = :customerId
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findByCustomerIdWithDetails(@Param("customerId") Long customerId);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.assignedWorker IS NULL AND b.status = :status
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findAvailableWithDetails(@Param("status") BookingStatus status);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.status IN :statuses
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findByStatusesWithDetails(
            @Param("statuses") java.util.Collection<BookingStatus> statuses);

    @Query("""
            SELECT COUNT(b) FROM ServiceBooking b
            WHERE b.status IN :statuses
              AND (
                    b.scheduledDateTime IS NULL
                    OR b.scheduledDateTime <= :at
                  )
            """)
    long countBusyAt(
            @Param("at") java.time.LocalDateTime at,
            @Param("statuses") java.util.Collection<BookingStatus> statuses);

    @Query("""
            SELECT DISTINCT b FROM ServiceBooking b
            LEFT JOIN FETCH b.vehicle v
            LEFT JOIN FETCH v.customer
            LEFT JOIN FETCH b.assignedWorker
            LEFT JOIN FETCH b.serviceTypes
            WHERE b.assignedWorker.id = :workerId
            ORDER BY b.id DESC
            """)
    List<ServiceBooking> findByWorkerIdWithDetails(@Param("workerId") Long workerId);

    List<ServiceBooking> findByAssignedWorkerId(Long workerId);

    boolean existsByAssignedWorkerIdAndStatus(Long workerId, BookingStatus status);

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, List<BookingStatus> statuses);

    boolean existsByVehicleCustomerIdAndStatusIn(Long customerId, List<BookingStatus> statuses);
}
