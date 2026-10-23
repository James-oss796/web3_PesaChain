package com.pesachain.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, privacy-minimized view of persisted audit records for system monitoring. */
@RestController
@RequestMapping("/api/v1/admin/activity")
public class AdminActivityController {
    private final JdbcTemplate jdbc;

    public AdminActivityController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    ActivityPage list(Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String actorRole,
            @RequestParam(required = false) String result) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (!"ADMINISTRATOR".equals(principal.role()) || !principal.isEnabled() || principal.passwordChangeRequired()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Administrator access is required.");
        }
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw invalid("Page must be nonnegative and size must be between 1 and 100.");
        }
        String query = normalizeQuery(q);
        if (actorRole != null && !List.of("BORROWER", "LOAN_OFFICER", "ADMINISTRATOR").contains(actorRole))
            throw invalid("Actor role is invalid.");
        if (result != null && !List.of("SUCCEEDED", "FAILED").contains(result))
            throw invalid("Result must be SUCCEEDED or FAILED.");

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (query != null) {
            try {
                UUID targetId = UUID.fromString(query);
                where.append(" AND target_id=?"); parameters.add(targetId);
            } catch (IllegalArgumentException notUuid) {
                where.append(" AND (lower(action) LIKE ? OR lower(target_type) LIKE ?)");
                parameters.add(query + "%"); parameters.add(query + "%");
            }
        }
        if (actorRole != null) { where.append(" AND actor_role=?"); parameters.add(actorRole); }
        if (result != null) { where.append(" AND result=?"); parameters.add(result); }

        String predicate = where.toString();
        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_log" + predicate, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size); pageParameters.add((long) page * size);
        List<Activity> items = jdbc.query("""
                SELECT id, actor_role, action, target_type, target_id, result, occurred_at
                FROM audit_log
                """ + predicate + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new Activity(rs.getObject("id", UUID.class), rs.getString("actor_role"),
                        rs.getString("action"), rs.getString("target_type"), rs.getObject("target_id", UUID.class),
                        rs.getString("result"), rs.getTimestamp("occurred_at").toInstant()), pageParameters.toArray());
        return new ActivityPage(items, page, size, total == null ? 0 : total);
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String value = query.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[%_\\\\]", "");
        if (value.length() < 2 || value.length() > 80) throw invalid("Search must contain between 2 and 80 characters.");
        return value;
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    record Activity(UUID id, String actorRole, String action, String targetType, UUID targetId, String result, Instant occurredAt) {}
    record ActivityPage(List<Activity> items, int page, int size, long totalItems) {}
}
