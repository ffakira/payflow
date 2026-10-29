package dev.akira.payflow.appuser.adapter.out.persistence;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.appuser.application.port.out.AppUserRepository;
import dev.akira.payflow.appuser.domain.AppUser;
import dev.akira.payflow.appuser.domain.AppUserId;
import dev.akira.payflow.appuser.domain.AppUserStatus;
import dev.akira.payflow.common.adapter.out.persistence.SqlLoader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAppUserRepository implements AppUserRepository {

    private static final Map<String, String> SQL =
            SqlLoader.loadQueries(JdbcAppUserRepository.class);

    private static final RowMapper<AppUser> APP_USER_ROW_MAPPER =
            JdbcAppUserRepository::mapAppUser;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "JdbcTemplate cannot be null"
        );
    }

    @Override
    public AppUser save(AppUser user) {
        Objects.requireNonNull(user, "App user cannot be null");

        jdbcTemplate.update(
                SQL.get("insert"),
                user.id().value(),
                user.holderId().value(),
                user.username(),
                user.passwordHash(),
                user.pinHash(),
                user.status().name(),
                user.failedLoginAttempts(),
                toOffsetDateTime(user.lockedUntil()),
                toOffsetDateTime(user.createdAt()),
                toOffsetDateTime(user.updatedAt())
        );

        return user;
    }

    @Override
    public Optional<AppUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        List<AppUser> results = jdbcTemplate.query(
                SQL.get("findByUsername"),
                APP_USER_ROW_MAPPER,
                normalizeUsername(username)
        );

        return results.stream().findFirst();
    }

    @Override
    public Optional<AppUser> findByHolderId(AccountHolderId holderId) {
        Objects.requireNonNull(holderId, "holderId cannot be null");

        List<AppUser> results = jdbcTemplate.query(
                SQL.get("findByHolderId"),
                APP_USER_ROW_MAPPER,
                holderId.value()
        );

        return results.stream().findFirst();
    }

    @Override
    public int recordFailedLoginAttempts(
            String username,
            int maxFailedAttempts,
            Instant now,
            Instant lockedUntil
    ) {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(now, "Current time cannot be null");
        Objects.requireNonNull(lockedUntil, "Lock expiry cannot be null");

        if (username.isBlank()) {
            return 0;
        }

        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum failed attempts must be positive"
            );
        }

        if (!lockedUntil.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Lock expiry must be after the current time"
            );
        }

        return jdbcTemplate.update(
                SQL.get("recordFailedLoginAttempts"),
                Timestamp.from(now),         // 1: expired-lock check
                Timestamp.from(now),         // 2: expired-lock check
                maxFailedAttempts,           // 3: status threshold
                Timestamp.from(now),         // 4: expired-lock check
                Timestamp.from(now),         // 5: expired-lock check
                maxFailedAttempts,           // 6: lock threshold
                Timestamp.from(lockedUntil), // 7: lock expiry
                Timestamp.from(now),         // 8: expired-lock check
                Timestamp.from(now),         // 9: updated_at
                normalizeUsername(username), // 10: username
                Timestamp.from(now)          // 11: WHERE lock expiry
        );
    }

    @Override
    public int resetFailedLoginAttempts(String username, Instant now) {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(now, "Current time cannot be null");

        if (username.isBlank()) {
            return 0;
        }

        return jdbcTemplate.update(
                SQL.get("resetFailedLoginAttempts"),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                normalizeUsername(username)
        );
    }

    @Override
    public boolean isPinVerificationLocked(String username, Instant now) {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(now, "Current time cannot be null");

        if (username.isBlank()) {
            return false;
        }

        Boolean locked = jdbcTemplate.queryForObject(
                SQL.get("isPinVerificationLocked"),
                Boolean.class,
                normalizeUsername(username),
                Timestamp.from(now)
        );

        return Boolean.TRUE.equals(locked);
    }

    @Override
    public int recordFailedPinAttempts(String username, int maxFailedAttempts, Instant now, Instant lockedUntil) {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(now, "Current time cannot be null");
        Objects.requireNonNull(lockedUntil, "Lock expiry cannot be null");

        if (username.isBlank()) {
            return 0;
        }

        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("Maximum failed attempts must be positive");
        }

        if (!lockedUntil.isAfter(now)) {
            throw new IllegalArgumentException("Lock expiry must be after the current time");
        }

        return jdbcTemplate.update(
                SQL.get("recordFailedPinAttempts"),
                Timestamp.from(now),
                Timestamp.from(now),
                maxFailedAttempts,
                Timestamp.from(lockedUntil),
                Timestamp.from(now),
                Timestamp.from(now),
                normalizeUsername(username),
                Timestamp.from(now)
        );
    }

    @Override
    public int resetFailedPinAttempts(String username, Instant now) {
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(now, "Current time cannot be null");

        if (username.isBlank()) {
            return 0;
        }

        return jdbcTemplate.update(
                SQL.get("resetFailedPinAttempts"),
                Timestamp.from(now),
                normalizeUsername(username),
                Timestamp.from(now)
        );
    }

    private static AppUser mapAppUser(ResultSet rs, int rowNum)
            throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID holderId = rs.getObject("holder_id", UUID.class);

        OffsetDateTime lockedUntil =
                rs.getObject("locked_until", OffsetDateTime.class);
        OffsetDateTime createdAt =
                rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updatedAt =
                rs.getObject("updated_at", OffsetDateTime.class);

        return new AppUser(
                new AppUserId(id),
                new AccountHolderId(holderId),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("pin_hash"),
                AppUserStatus.valueOf(rs.getString("status")),
                rs.getInt("failed_login_attempts"),
                toInstant(lockedUntil),
                Objects.requireNonNull(createdAt, "created_at cannot be null")
                        .toInstant(),
                Objects.requireNonNull(updatedAt, "updated_at cannot be null")
                        .toInstant()
        );
    }

    private static String normalizeUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null
                ? null
                : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
