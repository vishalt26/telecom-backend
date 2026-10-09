package com.telecom.service.repo;

import com.telecom.service.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    @Query("select t.status, count(t) from Ticket t group by t.status")
    List<Object[]> countByStatus();

    @Query("select t.category, count(t) from Ticket t group by t.category")
    List<Object[]> countByCategory();

    @Query("select t.type, count(t) from Ticket t group by t.type")
    List<Object[]> countByType();

    @Query("select t.createdAt, t.resolvedAt from Ticket t where t.resolvedAt is not null")
    List<Object[]> resolutionTimes();

    @Query("select t.createdAt from Ticket t where t.createdAt >= :from")
    List<Instant> createdSince(@Param("from") Instant from);

    @Query("select t.resolvedAt from Ticket t where t.resolvedAt >= :from")
    List<Instant> resolvedSince(@Param("from") Instant from);

    long countByCreatedAtAfter(Instant from);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Ticket t set t.assignee = null where t.assignee.id = :employeeId")
    int unassignEmployee(@Param("employeeId") Long employeeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Ticket t where t.customer.id = :customerId")
    int deleteByCustomerId(@Param("customerId") Long customerId);
}
