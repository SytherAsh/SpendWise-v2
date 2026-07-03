package com.spendwise.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so that the RLS
 * session variable set by {@link RlsSession} and the query that depends on it share the same
 * connection. Uses {@link ConnectionCallback} directly (rather than plain {@link JdbcTemplate}
 * argument binding) because {@code selected_apps}/{@code selected_banks} are native Postgres
 * {@code TEXT[]} columns, which need {@link Connection#createArrayOf(String, Object[])}.
 */
@Repository
public class UserPreferencesRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final ObjectMapper objectMapper;

    public UserPreferencesRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
        this.objectMapper = objectMapper;
    }

    public Optional<UserPreferences> find(UUID userId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.execute((ConnectionCallback<Optional<UserPreferences>>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM user_preferences WHERE user_id = ?")) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs, userId)) : Optional.empty();
                }
            }
        });
    }

    public UserPreferences upsert(
            UUID userId,
            Map<String, Boolean> alertChannels,
            List<String> selectedApps,
            List<String> selectedBanks,
            BigDecimal monthlySpendEstimate) {
        rlsSession.setCurrentUser(userId);
        String fcmToken = jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user_preferences (user_id, alert_channels, selected_apps, selected_banks, monthly_spend_estimate) "
                            + "VALUES (?, ?::jsonb, ?, ?, ?) "
                            + "ON CONFLICT (user_id) DO UPDATE SET "
                            + "alert_channels = EXCLUDED.alert_channels, selected_apps = EXCLUDED.selected_apps, "
                            + "selected_banks = EXCLUDED.selected_banks, monthly_spend_estimate = EXCLUDED.monthly_spend_estimate "
                            + "RETURNING fcm_token")) {
                ps.setObject(1, userId);
                ps.setString(2, toJson(alertChannels));
                ps.setArray(3, toSqlArray(connection, selectedApps));
                ps.setArray(4, toSqlArray(connection, selectedBanks));
                if (monthlySpendEstimate == null) {
                    ps.setNull(5, Types.NUMERIC);
                } else {
                    ps.setBigDecimal(5, monthlySpendEstimate);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getString("fcm_token");
                }
            }
        });
        return new UserPreferences(userId, alertChannels, selectedApps, selectedBanks, monthlySpendEstimate, fcmToken);
    }

    /**
     * Separate from {@link #upsert} (E5, `PUT /users/me/fcm-token`) so registering/rotating a
     * push token never touches alert_channels/selected_apps/selected_banks/monthly_spend_estimate,
     * and vice versa — the general preferences upsert's SET clause deliberately excludes
     * fcm_token for the same reason.
     */
    public void updateFcmToken(UUID userId, String fcmToken) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "INSERT INTO user_preferences (user_id, fcm_token) VALUES (?, ?) "
                        + "ON CONFLICT (user_id) DO UPDATE SET fcm_token = EXCLUDED.fcm_token",
                userId,
                fcmToken);
    }

    private Array toSqlArray(Connection connection, List<String> values) throws SQLException {
        return values == null ? null : connection.createArrayOf("text", values.toArray());
    }

    private String toJson(Map<String, Boolean> alertChannels) {
        try {
            return objectMapper.writeValueAsString(alertChannels);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize alert_channels", e);
        }
    }

    @SuppressWarnings("unchecked")
    private UserPreferences mapRow(ResultSet rs, UUID userId) throws SQLException {
        Map<String, Boolean> alertChannels;
        try {
            alertChannels = objectMapper.readValue(rs.getString("alert_channels"), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize alert_channels", e);
        }
        List<String> selectedApps = toStringList(rs.getArray("selected_apps"));
        List<String> selectedBanks = toStringList(rs.getArray("selected_banks"));
        BigDecimal monthlySpendEstimate = rs.getBigDecimal("monthly_spend_estimate");
        String fcmToken = rs.getString("fcm_token");
        return new UserPreferences(userId, alertChannels, selectedApps, selectedBanks, monthlySpendEstimate, fcmToken);
    }

    private List<String> toStringList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        return List.of((String[]) sqlArray.getArray());
    }
}
