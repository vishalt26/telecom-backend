package com.telecom.service.repo;

import com.telecom.service.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);

    /** No clearAutomatically: callers still hold loaded Ticket entities whose lazy fields must stay usable. */
    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.ticketId = :ticketId and n.read = false")
    int markTicketRead(@Param("userId") Long userId, @Param("ticketId") Long ticketId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.ticketId = :ticketId")
    int deleteByTicketId(@Param("ticketId") Long ticketId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.ticketId in (select t.id from Ticket t where t.customer.id = :customerId)")
    int deleteForCustomerTickets(@Param("customerId") Long customerId);
}
