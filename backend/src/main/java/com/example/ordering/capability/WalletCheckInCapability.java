package com.example.ordering.capability;
import org.springframework.stereotype.Component;
@Component public final class WalletCheckInCapability implements Capability {
    public String id() { return "wallet.check-in"; }
    public String name() { return "每日签到"; }
    public String description() { return "领取每日签到奖励并查看钱包"; }
    public String category() { return CapabilityCategory.LIFESTYLE.id(); }
    public String route() { return "/api/wallet/check-in"; }
}
