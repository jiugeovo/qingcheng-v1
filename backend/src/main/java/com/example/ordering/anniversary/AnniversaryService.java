package com.example.ordering.anniversary;

import com.example.ordering.user.UserRepository;
import com.example.ordering.user.UserRole;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ordering.couple.CoupleAccess;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class AnniversaryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Comparator<AnniversaryResponse> DISPLAY_ORDER = Comparator
            .comparing(AnniversaryResponse::nextOccurrence, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AnniversaryResponse::date)
            .thenComparing(AnniversaryResponse::id);

    private final AnniversaryRepository repository;
    private final Validator validator;
    private final Clock clock;
    private CoupleAccess couple = CoupleAccess.legacy();

    @Autowired(required = false)
    void configureCouple(CoupleAccess couple) {
        this.couple = couple;
    }
    private final UserRepository users;

    public AnniversaryService(AnniversaryRepository repository, Validator validator) {
        this(repository, validator, Clock.system(ZONE));
    }

    public AnniversaryService(AnniversaryRepository repository, Validator validator, Clock clock) {
        this(repository, validator, clock, null);
    }

    @Autowired
    public AnniversaryService(AnniversaryRepository repository, Validator validator, Clock clock, UserRepository users) {
        this.users = users;
        this.repository = repository;
        this.validator = validator;
        this.clock = Objects.requireNonNull(clock, "clock").withZone(ZONE);
    }

    @Transactional(readOnly = true)
    public List<AnniversaryResponse> list(long userId) {
        requireUser(userId);
        LocalDate today = LocalDate.now(clock);
        boolean admin = isAdmin(userId);
        return repository.findAllShared().stream()
                .map(entity -> AnniversaryResponse.from(entity, today, admin || entity.userId() == userId))
                .sorted(DISPLAY_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnniversaryResponse get(long userId, long id) {
        AnniversaryEntity entity = requireVisible(userId, id);
        return AnniversaryResponse.from(entity, LocalDate.now(clock), entity.userId() == userId || isAdmin(userId));
    }

    @Transactional
    public AnniversaryResponse create(long userId, AnniversaryRequest request) {
        requireUser(userId);
        validate(request);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        AnniversaryEntity saved = repository.insert(new AnniversaryEntity(
                null, userId, request.title(), request.date(), request.note(), request.repeatYearly(),
                request.type() == null ? AnniversaryType.OTHER : request.type(), now, now
        ));
        return AnniversaryResponse.from(saved, now.toLocalDate(), true);
    }

    @Transactional
    public AnniversaryResponse update(long userId, long id, AnniversaryRequest request) {
        requireUser(userId);
        requireId(id);
        validate(request);
        // Lock before resolving omitted fields so legacy updates cannot restore a stale classification.
        AnniversaryEntity existing = (isAdmin(userId) ? repository.findByIdForUpdate(id)
                : repository.findByIdAndUserIdForUpdate(id, userId))
                .orElseThrow(AnniversaryService::notFound);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        AnniversaryEntity updated = new AnniversaryEntity(
                id, existing.userId(), request.title(), request.date(), request.note(), request.repeatYearly(),
                request.type() == null ? existing.type() : request.type(),
                existing.createdAt(), now
        );
        if (!repository.update(updated)) {
            throw notFound();
        }
        return AnniversaryResponse.from(updated, now.toLocalDate(), true);
    }

    @Transactional
    public void delete(long userId, long id) {
        requireUser(userId);
        requireId(id);
        if (!(isAdmin(userId) ? repository.deleteById(id) : repository.deleteByIdAndUserId(id, userId))) {
            throw notFound();
        }
    }

    private boolean isAdmin(long userId) {
        if (couple.enabled()) return users != null && users.findById(userId).filter(couple::isMember).isPresent();
        return users != null && users.findById(userId)
                .filter(user -> user.isEnabled() && (user.getRole() == UserRole.ADMIN
                        || user.getRole() == UserRole.SUPER_ADMIN)).isPresent();
    }

    private AnniversaryEntity requireVisible(long userId, long id) {
        requireUser(userId);
        requireId(id);
        return repository.findSharedById(id).orElseThrow(AnniversaryService::notFound);
    }

    private void validate(AnniversaryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private static void requireUser(long userId) {
        if (userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
    }

    private static void requireId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("纪念日 ID 必须为正整数");
        }
    }

    private static ResponseStatusException notFound() {
        // Deliberately do not disclose whether an ID belongs to another account.
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "纪念日不存在");
    }
}
