package com.example.ordering.birthday;

import com.example.ordering.couple.CoupleAccess;
import com.example.ordering.push.PushNotificationService;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class BirthdayGreetingService {

    static final String SCHEDULE = "0 0 0 1 1 *";
    static final String PREPARE_SCHEDULE = "0 50 23 26 8 *";
    static final String ZONE = "Asia/Shanghai";
    static final String FALLBACK_GREETING = "生日快乐！愿新的一岁平安顺遂、天天开心，所有期待都如约而至。🎂";

    private static final Logger log = LoggerFactory.getLogger(BirthdayGreetingService.class);

    private final BirthdayGreetingProperties properties;
    private final UserRepository userRepository;
    private final BirthdayGreetingRepository greetingRepository;
    private final BirthdayGreetingGenerator greetingGenerator;
    private final BirthdayGreetingDeliveryService deliveryService;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;
    private CoupleAccess couple = CoupleAccess.legacy();

    @Autowired(required = false)
    void configureCouple(CoupleAccess couple) {
        this.couple = couple;
    }

    public BirthdayGreetingService(
            BirthdayGreetingProperties properties,
            UserRepository userRepository,
            BirthdayGreetingRepository greetingRepository,
            BirthdayGreetingGenerator greetingGenerator,
            BirthdayGreetingDeliveryService deliveryService,
            PushNotificationService pushNotificationService
    ) {
        this(properties, userRepository, greetingRepository, greetingGenerator, deliveryService,
                pushNotificationService, Clock.system(ZoneId.of(ZONE)));
    }

    @Autowired
    public BirthdayGreetingService(
            BirthdayGreetingProperties properties,
            UserRepository userRepository,
            BirthdayGreetingRepository greetingRepository,
            BirthdayGreetingGenerator greetingGenerator,
            BirthdayGreetingDeliveryService deliveryService,
            PushNotificationService pushNotificationService,
            Clock clock
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.greetingRepository = greetingRepository;
        this.greetingGenerator = greetingGenerator;
        this.deliveryService = deliveryService;
        this.pushNotificationService = pushNotificationService;
        this.clock = Objects.requireNonNull(clock, "clock").withZone(ZoneId.of(ZONE));
    }

    @Scheduled(cron = SCHEDULE, zone = ZONE)
    public void sendAnnualGreeting() {
        if (!properties.isEnabled()) {
            log.info("Annual birthday greeting is disabled");
            return;
        }
        UserAccount user = recipient();
        if (user == null) {
            log.warn("Annual birthday greeting skipped: no enabled recipient");
            return;
        }
        String username = user.getUsername();

        int greetingYear = Year.now(clock).getValue();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            BirthdayGreetingDeliveryResult delivered = deliveryService.deliver(
                            user,
                            greetingYear,
                            FALLBACK_GREETING,
                            normalizeModel(properties.getModel()),
                            now
                    )
                    .orElse(null);
            if (delivered == null) {
                log.info("Annual birthday greeting already handled for user {} in {}", username, greetingYear);
                return;
            }
            pushNotificationService.notifyAiConversation(
                    user,
                    delivered.conversationId(),
                    "布布助手祝你生日快乐",
                    delivered.content()
            );
            log.info("Annual birthday greeting delivered to user {} for year {}", username, greetingYear);
        } catch (RuntimeException error) {
            log.error("Annual birthday greeting failed for user {} in {}", username, greetingYear, error);
        }
    }

    @Scheduled(cron = PREPARE_SCHEDULE, zone = ZONE)
    public void prepareAnnualGreeting() {
        if (!properties.isEnabled()) {
            return;
        }
        UserAccount user = recipient();
        if (user == null) {
            log.warn("Birthday greeting preparation skipped: no enabled recipient");
            return;
        }
        String username = user.getUsername();
        int greetingYear = Year.now(clock).getValue();
        boolean prepared = greetingRepository.savePrepared(
                user.getId(),
                greetingYear,
                generateOrFallback(user),
                normalizeModel(properties.getModel()),
                LocalDateTime.now(clock)
        );
        if (prepared) {
            log.info("Birthday greeting prepared for user {} in {}", username, greetingYear);
        } else {
            log.info("Birthday greeting preparation skipped because delivery is already complete for user {} in {}",
                    username, greetingYear);
        }
    }

    private UserAccount recipient() {
        String username = normalizeUsername(properties.getUsername());
        if (couple.enabled()) {
            var baobao = CoupleAccess.Member.BAOBAO;
            var recipient = "superadmin".equalsIgnoreCase(username) || baobao.loginName().equals(username)
                    ? userRepository.findById(couple.memberId(baobao))
                    : userRepository.findByUsername(username);
            return recipient.filter(couple::isMember).orElse(null);
        }
        return userRepository.findByUsername(username).filter(UserAccount::isEnabled).orElse(null);
    }

    private String generateOrFallback(UserAccount user) {
        try {
            String generated = greetingGenerator.generate(displayName(user));
            return StringUtils.hasText(generated) ? generated.trim() : FALLBACK_GREETING;
        } catch (RuntimeException error) {
            log.warn("Hermes birthday greeting generation failed; using fallback: {}", error.getMessage());
            return FALLBACK_GREETING;
        }
    }

    private String displayName(UserAccount user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return user.getUsername();
    }

    private String normalizeUsername(String username) {
        return StringUtils.hasText(username) ? username.trim() : "superadmin";
    }

    private String normalizeModel(String model) {
        return StringUtils.hasText(model) ? model.trim() : "gpt-5.6-sol";
    }
}
