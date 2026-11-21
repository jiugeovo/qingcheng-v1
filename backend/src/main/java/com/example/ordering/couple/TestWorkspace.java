package com.example.ordering.couple;

import com.example.ordering.admin.audit.AdminAuditService;
import com.example.ordering.auth.AuthService;
import com.example.ordering.auth.LoginResponse;
import com.example.ordering.auth.TokenService;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import com.example.ordering.wallet.WalletLedgerService;
import com.example.ordering.wallet.WalletTransactionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Controls synthetic members on the isolated test instance. */
@Service
@ConditionalOnProperty(name = "app.couple.mode", havingValue = "TEST")
public class TestWorkspace {
    static final BigDecimal STARTING_BALANCE = new BigDecimal("500.00");

    private final CoupleAccess couple;
    private final UserRepository users;
    private final TokenService tokens;
    private final AuthService auth;
    private final JdbcTemplate jdbc;
    private final AdminAuditService audit;
    private final WalletLedgerService wallet;

    public TestWorkspace(CoupleAccess couple, UserRepository users, TokenService tokens, AuthService auth,
                         JdbcTemplate jdbc, AdminAuditService audit, WalletLedgerService wallet) {
        this.couple = couple;
        this.users = users;
        this.tokens = tokens;
        this.auth = auth;
        this.jdbc = jdbc;
        this.audit = audit;
        this.wallet = wallet;
    }

    @Transactional
    public LoginResponse selectActor(UserAccount developer, CoupleAccess.Member member) {
        requireDeveloper(developer);
        UserAccount actor = lockMember(couple.memberId(member));
        tokens.revokeAllForUser(actor.getId());
        audit.record(developer, "TEST_SELECT_MEMBER", "USER", actor.getId(), null, member.name(), null, null);
        return new LoginResponse(tokens.createToken(actor), auth.profile(actor));
    }

    @Transactional
    public void setBalance(UserAccount developer, CoupleAccess.Member member, BigDecimal balance) {
        requireDeveloper(developer);
        UserAccount actor = lockMember(couple.memberId(member));
        BigDecimal previous = actor.getQingchengCoins();
        BigDecimal delta = balance.subtract(previous);
        if (delta.signum() == 0) return;
        actor.setQingchengCoins(balance);
        users.save(actor);
        wallet.record(actor, WalletTransactionType.ADMIN_ADJUSTMENT, delta, "测试余额调整", null, null);
        audit.record(developer, "TEST_ADJUST_BALANCE", "USER", actor.getId(),
                previous.toPlainString(), balance.toPlainString(), delta, null);
    }

    @Transactional
    public void resetOrdersAndWallets(UserAccount developer) {
        requireDeveloper(developer);
        List<UserAccount> members = List.of(couple.memberId(CoupleAccess.Member.JIUGE),
                        couple.memberId(CoupleAccess.Member.BAOBAO)).stream().sorted()
                .map(this::lockMember).toList();
        jdbc.update("delete from order_notification_items");
        jdbc.update("delete from shop_order_items");
        jdbc.update("delete from order_notifications");
        jdbc.update("delete from shop_orders");
        jdbc.update("delete from order_idempotency_receipts");
        jdbc.update("delete from wallet_transactions");
        for (UserAccount member : members) {
            member.setQingchengCoins(STARTING_BALANCE);
            member.setLastCheckInDate(null);
            users.save(member);
            tokens.revokeAllForUser(member.getId());
        }
        audit.record(developer, "TEST_RESET_ORDERS_AND_WALLETS", "TEST_WORKSPACE",
                developer.getId(), null, null, null, null);
    }

    private UserAccount lockMember(long id) {
        return users.findLockedById(id).filter(couple::isMember)
                .orElseThrow(() -> new IllegalStateException("测试成员未初始化"));
    }

    private void requireDeveloper(UserAccount developer) {
        if (!couple.isDeveloper(developer)) throw new AccessDeniedException("需要测试开发者账号");
    }
}
