package com.telecom.service.service;

import com.telecom.service.domain.Category;
import com.telecom.service.domain.TicketStatus;
import com.telecom.service.domain.TicketType;
import com.telecom.service.dto.Dtos.SummaryReport;
import com.telecom.service.dto.Dtos.TrendPoint;
import com.telecom.service.repo.TicketRepository;
import com.telecom.service.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final int MAX_RANGE_DAYS = 366;

    private final TicketRepository tickets;

    public ReportService(TicketRepository tickets) {
        this.tickets = tickets;
    }

    public SummaryReport summary() {
        Map<String, Long> byStatus = zero(TicketStatus.values());
        Map<String, Long> byCategory = zero(Category.values());
        Map<String, Long> byType = zero(TicketType.values());
        fill(byStatus, tickets.countByStatus());
        fill(byCategory, tickets.countByCategory());
        fill(byType, tickets.countByType());

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long open = byStatus.get("PENDING") + byStatus.get("IN_PROGRESS");
        long resolved = byStatus.get("RESOLVED") + byStatus.get("COMPLETED");

        List<Object[]> times = tickets.resolutionTimes();
        Double avgHours = times.isEmpty() ? null : round1(times.stream()
                .mapToLong(r -> Duration.between((Instant) r[0], (Instant) r[1]).toMinutes())
                .average().orElse(0) / 60.0);

        long last30 = tickets.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(30)));
        return new SummaryReport(total, open, resolved, last30, avgHours, byStatus, byCategory, byType);
    }

    public List<TrendPoint> trends(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("'from' must not be after 'to'");
        }
        if (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("Date range is limited to " + MAX_RANGE_DAYS + " days");
        }
        Instant since = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            buckets.put(d, new long[2]);
        }
        tickets.createdSince(since).forEach(i -> bump(buckets, i, 0));
        tickets.resolvedSince(since).forEach(i -> bump(buckets, i, 1));

        List<TrendPoint> out = new ArrayList<>();
        buckets.forEach((d, c) -> out.add(new TrendPoint(d, c[0], c[1])));
        return out;
    }

    private static void bump(Map<LocalDate, long[]> buckets, Instant when, int idx) {
        long[] slot = buckets.get(when.atZone(ZoneOffset.UTC).toLocalDate());
        if (slot != null) {
            slot[idx]++;
        }
    }

    private static Map<String, Long> zero(Enum<?>[] values) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Enum<?> e : values) {
            m.put(e.name(), 0L);
        }
        return m;
    }

    private static void fill(Map<String, Long> target, List<Object[]> rows) {
        for (Object[] row : rows) {
            target.put(((Enum<?>) row[0]).name(), (Long) row[1]);
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
