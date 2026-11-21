package com.example.ordering.couple;

import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRole;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public final class CoupleAccess {
    public enum Mode { LEGACY, FORMAL, TEST }
    public enum Member {
        JIUGE("member_a"), BAOBAO("member_b");

        private final String loginName;

        Member(String loginName) {
            this.loginName = loginName;
        }

        public String loginName() {
            return loginName;
        }
    }

    public record Profile(Mode mode, String identity, boolean canBuyMenu, boolean canBuyShop) { }

    private final Mode mode;
    private final long jiugeId;
    private final long baobaoId;
    private final long developerId;

    public CoupleAccess(@Value("${app.couple.mode:LEGACY}") Mode mode,
                        @Value("${app.couple.jiuge-id:101}") long jiugeId,
                        @Value("${app.couple.baobao-id:102}") long baobaoId,
                        @Value("${app.couple.developer-id:0}") long developerId) {
        if (mode != Mode.LEGACY && (jiugeId <= 0 || baobaoId <= 0 || jiugeId == baobaoId)) {
            throw new IllegalStateException("Two distinct couple member IDs are required");
        }
        if (mode == Mode.TEST && (developerId <= 0 || developerId == jiugeId || developerId == baobaoId)) {
            throw new IllegalStateException("A separate test developer ID is required");
        }
        this.mode = mode;
        this.jiugeId = jiugeId;
        this.baobaoId = baobaoId;
        this.developerId = developerId;
    }

    public static CoupleAccess legacy() {
        return new CoupleAccess(Mode.LEGACY, 0, 0, 0);
    }

    public boolean enabled() {
        return mode != Mode.LEGACY;
    }

    public boolean test() {
        return mode == Mode.TEST;
    }

    public long memberId(Member member) {
        return switch (member) {
            case JIUGE -> jiugeId;
            case BAOBAO -> baobaoId;
        };
    }

    public long developerId() {
        return developerId;
    }

    public boolean isMember(UserAccount user) {
        return ordinary(user) && (user.getId() == jiugeId || user.getId() == baobaoId);
    }

    public boolean isDeveloper(UserAccount user) {
        return test() && ordinary(user) && user.getId() == developerId;
    }

    public boolean canLogIn(UserAccount user) {
        return switch (mode) {
            case LEGACY -> true;
            case FORMAL -> isMember(user);
            case TEST -> isDeveloper(user);
        };
    }

    public Long handler(boolean menuOrder) {
        return enabled() ? memberId(menuOrder ? Member.BAOBAO : Member.JIUGE) : null;
    }

    public boolean orderAssignment(Long buyerId, Long handlerId, boolean menuOrder) {
        return enabled() && Objects.equals(handler(menuOrder), handlerId)
                && Objects.equals(memberId(menuOrder ? Member.JIUGE : Member.BAOBAO), buyerId);
    }

    public void requireBuyer(UserAccount user, boolean menuOrder) {
        if (enabled() && (!isMember(user) || user.getId() != memberId(menuOrder ? Member.JIUGE : Member.BAOBAO))) {
            throw new AccessDeniedException(menuOrder ? "点菜和猪猪商店由九歌下单" : "宝宝商店由宝宝购买");
        }
    }

    public void requireMember(UserAccount user) {
        if (!enabled() || !isMember(user)) {
            throw new AccessDeniedException("需要双人成员身份");
        }
    }

    public Profile profile(UserAccount user) {
        if (!enabled()) return null;
        if (isDeveloper(user)) return new Profile(mode, "DEVELOPER", false, false);
        if (!isMember(user)) return null;
        boolean jiuge = user.getId() == jiugeId;
        return new Profile(mode, (jiuge ? Member.JIUGE : Member.BAOBAO).name(), jiuge, !jiuge);
    }

    private static boolean ordinary(UserAccount user) {
        return user != null && user.getId() != null && user.isEnabled() && user.getRole() == UserRole.USER;
    }
}
