package dev.akira.payflow.auth.adapter.out.persistence;

import dev.akira.payflow.account.domain.holder.AccountHolderId;
import dev.akira.payflow.auth.appuser.out.AppUserRepository;
import dev.akira.payflow.appuser.domain.AppUser;
import dev.akira.payflow.appuser.domain.AppUserId;
import dev.akira.payflow.appuser.domain.AppUserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAppUserRepository implements AppUserRepository {

    private static final String INSERT_SQL = """
        INSERT INTO app_users (
            id,
            holder_id,
            username,
            password_hash,
            pin_hash,
            status,
            failed_login_attempts,
            locked_until,
            created_at,
            updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private static final String FIND_BY_USERNAME_SQL = """
        SELECT
            id,
            holder_id,
            username,
            password_hash,
            pin_hash,
            status,
            failed_login_attempts,
            locked_until,
            created_at,
            updated_at
        FROM app_users
        WHERE username = ?
    """;

    private static final String FIND_BY_HOLDER_ID = """
        SELECT
            id,
            holder_id,
            username,
            password_hash,
            pin_hash,
            status,
            failed_login_attempts,
            locked_until,
            created_at,
            updated_at
        FROM app_users
        WHERE holder_id = ?
    """;

    private static final RowMapper<AppUser> APP_USER_ROW_MAPPER =
            JdbcAppUserRepository::mapAppUser;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public AppUser save(AppUser user) {
        Objects.requireNonNull(user, "App user cannot be null");

        jdbcTemplate.update(
                INSERT_SQL,
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
                FIND_BY_USERNAME_SQL,
                APP_USER_ROW_MAPPER,
                username.strip().toLowerCase(Locale.ROOT)
        );

        return results.stream().findFirst();
    }

    @Override
    public Optional<AppUser> findByHolderId(AccountHolderId holderId) {
        Objects.requireNonNull(holderId, "holderId cannot be null");

        List<AppUser> results = jdbcTemplate.query(
                FIND_BY_HOLDER_ID,
                APP_USER_ROW_MAPPER,
                holderId.value()
        );

        return results.stream().findFirst();
    }

    private static AppUser mapAppUser(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID holderId = rs.getObject("holder_id", UUID.class);

        OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updatedAt =  rs.getObject("updated_at", OffsetDateTime.class);

        return new AppUser(
                new AppUserId(id),
                new AccountHolderId(holderId),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("pin_hash"),
                AppUserStatus.valueOf(rs.getString("status")),
                rs.getInt("failed_login_attempts"),
                toInstant(lockedUntil),
                createdAt.toInstant(),
                updatedAt.toInstant()
        );
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
