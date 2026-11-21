package com.example.ordering.piggy;

import com.example.ordering.couple.CoupleAccess;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PiggyProgressService {
    public record Progress(long count, long revision, long deviceTaps, boolean legacyImportClosed) { }

    private static final Progress EMPTY = new Progress(0, 0, 0, false);
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final CoupleAccess access;

    public PiggyProgressService(JdbcTemplate jdbc, UserRepository users, CoupleAccess access) {
        this.jdbc = jdbc;
        this.users = users;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Progress get(UserAccount member, UUID deviceId) {
        access.requireMember(member);
        return read(member.getId(), deviceId);
    }

    @Transactional
    public Progress sync(UserAccount member, UUID deviceId, long revision, long taps, Long legacyCount) {
        long userId = lockMember(member);
        Progress current = read(userId, deviceId);
        if (current.revision() != revision) return current;

        long submitted = Math.max(taps, current.deviceTaps());
        boolean importLegacy = legacyCount != null && !current.legacyImportClosed();
        if (submitted == current.deviceTaps() && !importLegacy) return current;

        long count = add(current.count(), submitted - current.deviceTaps());
        if (importLegacy) count = add(count, legacyCount);
        boolean importClosed = current.legacyImportClosed() || importLegacy;
        saveTotal(userId, count, revision, importClosed);
        if (submitted > current.deviceTaps()) {
            jdbc.update("""
                    INSERT INTO piggy_device_progress (user_id, device_id, submitted_taps)
                    VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE submitted_taps=?
                    """, userId, deviceId.toString(), submitted, submitted);
        }
        return new Progress(count, revision, submitted, importClosed);
    }

    @Transactional
    public Progress reset(UserAccount member, UUID deviceId, long revision) {
        long userId = lockMember(member);
        Progress current = read(userId, deviceId);
        if (current.revision() != revision) return current;

        long nextRevision = Math.incrementExact(revision);
        saveTotal(userId, 0, nextRevision, true);
        jdbc.update("DELETE FROM piggy_device_progress WHERE user_id=?", userId);
        return new Progress(0, nextRevision, 0, true);
    }

    private long lockMember(UserAccount member) {
        access.requireMember(member);
        return users.findLockedById(member.getId()).filter(access::isMember)
                .orElseThrow(() -> new AccessDeniedException("需要双人成员身份")).getId();
    }

    private Progress read(long userId, UUID deviceId) {
        return jdbc.query("""
                SELECT p.tap_count, p.revision, p.legacy_import_closed, COALESCE(d.submitted_taps, 0)
                FROM piggy_progress p LEFT JOIN piggy_device_progress d
                ON d.user_id=p.user_id AND d.device_id=?
                WHERE p.user_id=?
                """, (rs, index) -> new Progress(rs.getLong(1), rs.getLong(2), rs.getLong(4), rs.getBoolean(3)),
                deviceId.toString(), userId).stream().findFirst().orElse(EMPTY);
    }

    private void saveTotal(long userId, long count, long revision, boolean importClosed) {
        jdbc.update("""
                INSERT INTO piggy_progress (user_id, tap_count, revision, legacy_import_closed)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE tap_count=?, revision=?, legacy_import_closed=?
                """, userId, count, revision, importClosed, count, revision, importClosed);
    }

    private static long add(long initial, long delta) {
        return delta > Long.MAX_VALUE - initial ? Long.MAX_VALUE : initial + delta;
    }
}
