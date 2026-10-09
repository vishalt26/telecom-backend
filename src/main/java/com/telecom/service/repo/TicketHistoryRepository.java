package com.telecom.service.repo;

import com.telecom.service.domain.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    List<TicketHistory> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TicketHistory h where h.ticketId = :ticketId")
    int deleteByTicketId(@Param("ticketId") Long ticketId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TicketHistory h where h.ticketId in (select t.id from Ticket t where t.customer.id = :customerId)")
    int deleteForCustomer(@Param("customerId") Long customerId);
}
