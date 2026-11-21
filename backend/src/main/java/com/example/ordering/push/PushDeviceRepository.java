package com.example.ordering.push;

import com.example.ordering.common.JdbcEntityMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class PushDeviceRepository {

    private final JdbcTemplate jdbc;

    public PushDeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PushDeviceEntity> findByProviderAndRegistrationId(String provider, String registrationId) {
        List<PushDeviceEntity> devices = jdbc.query(
                "select * from push_devices where provider = ? and registration_id = ?",
                JdbcEntityMappers.PUSH_DEVICE,
                provider,
                registrationId);
        return devices.stream().findFirst();
    }

    public List<PushDeviceEntity> findAllByProviderAndEnabledTrueAndUserIdIn(String provider, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", userIds.stream().map(id -> "?").toList());
        Object[] args = new Object[userIds.size() + 1];
        args[0] = provider;
        int index = 1;
        for (Long userId : userIds) {
            args[index++] = userId;
        }
        return jdbc.query(
                "select * from push_devices where provider = ? and enabled = true and user_id in (" + placeholders + ")",
                JdbcEntityMappers.PUSH_DEVICE,
                args);
    }

    public List<PushDeviceEntity> findAllByUserIdAndEnabledTrue(Long userId) {
        return jdbc.query("select * from push_devices where user_id = ? and enabled = true",
                JdbcEntityMappers.PUSH_DEVICE,
                userId);
    }

    public PushDeviceEntity save(PushDeviceEntity device) {
        if (device.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into push_devices
                                (user_id, provider, registration_id, platform, enabled, created_at, updated_at, last_seen_at)
                                values (?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                bind(ps, device);
                return ps;
            }, keyHolder);
            device.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update push_devices
                            set user_id = ?, provider = ?, registration_id = ?, platform = ?, enabled = ?,
                                created_at = ?, updated_at = ?, last_seen_at = ?
                            where id = ?
                            """,
                    device.getUserId(),
                    device.getProvider(),
                    device.getRegistrationId(),
                    device.getPlatform(),
                    device.isEnabled(),
                    device.getCreatedAt(),
                    device.getUpdatedAt(),
                    device.getLastSeenAt(),
                    device.getId());
        }
        return device;
    }

    private void bind(PreparedStatement ps, PushDeviceEntity device) throws java.sql.SQLException {
        ps.setLong(1, device.getUserId());
        ps.setString(2, device.getProvider());
        ps.setString(3, device.getRegistrationId());
        ps.setString(4, device.getPlatform());
        ps.setBoolean(5, device.isEnabled());
        ps.setObject(6, device.getCreatedAt());
        ps.setObject(7, device.getUpdatedAt());
        ps.setObject(8, device.getLastSeenAt());
    }
}
