package com.example.ordering.birthday;

import com.example.ordering.ai.AiConversationService;
import com.example.ordering.user.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class BirthdayGreetingDeliveryService {

    private final BirthdayGreetingRepository greetingRepository;
    private final AiConversationService conversationService;

    public BirthdayGreetingDeliveryService(
            BirthdayGreetingRepository greetingRepository,
            AiConversationService conversationService
    ) {
        this.greetingRepository = greetingRepository;
        this.conversationService = conversationService;
    }

    @Transactional
    public Optional<BirthdayGreetingDeliveryResult> deliver(
            UserAccount user,
            int greetingYear,
            String fallbackContent,
            String fallbackModel,
            LocalDateTime createdAt
    ) {
        if (!greetingRepository.claim(
                user.getId(), greetingYear, fallbackContent, fallbackModel, createdAt)) {
            return Optional.empty();
        }
        BirthdayGreetingRepository.PreparedGreeting prepared = greetingRepository
                .findClaimed(user.getId(), greetingYear)
                .orElseThrow(() -> new IllegalStateException("Claimed birthday greeting was not found"));
        String content = StringUtils.hasText(prepared.content()) ? prepared.content().trim() : fallbackContent;
        String model = StringUtils.hasText(prepared.model()) ? prepared.model().trim() : fallbackModel;
        Long conversationId = conversationService.saveProactiveAssistantMessage(
                user,
                "生日祝福 " + greetingYear,
                model,
                content,
                createdAt
        );
        greetingRepository.markSent(
                user.getId(),
                greetingYear,
                conversationId,
                model,
                createdAt
        );
        return Optional.of(new BirthdayGreetingDeliveryResult(conversationId, content));
    }
}
