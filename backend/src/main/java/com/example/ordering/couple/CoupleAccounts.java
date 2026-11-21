package com.example.ordering.couple;

import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class CoupleAccounts implements ApplicationRunner {
    private final CoupleAccess access;
    private final UserRepository users;

    public CoupleAccounts(CoupleAccess access, UserRepository users) {
        this.access = access;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!access.enabled()) return;
        for (var member : CoupleAccess.Member.values()) {
            UserAccount user = users.findById(access.memberId(member)).orElse(null);
            if (!access.isMember(user) || !member.loginName().equals(user.getUsername())) {
                throw new IllegalStateException("Missing or invalid 1.0 member: " + member);
            }
        }
        if (access.test() && !access.isDeveloper(users.findById(access.developerId()).orElse(null))) {
            throw new IllegalStateException("Missing or invalid test developer account");
        }
    }
}
