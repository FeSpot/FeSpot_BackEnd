package com.api.fespot.domain.notification.service;

import com.api.fespot.domain.notification.entity.PushDeliveryTicket;
import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.notification.repository.PushDeliveryTicketRepository;
import com.api.fespot.domain.notification.repository.PushTokenRepository;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.push.client.ExpoPushClient;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import com.api.fespot.global.push.client.dto.ExpoPushTicket;
import com.api.fespot.global.push.exception.PushErrorResponseCode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class PushDeliveryService {

    private final ExpoPushClient expoPushClient;
    private final PushTokenRepository pushTokenRepository;
    private final PushDeliveryTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public PushDeliveryService(
            ExpoPushClient expoPushClient,
            PushTokenRepository pushTokenRepository,
            PushDeliveryTicketRepository ticketRepository,
            UserRepository userRepository,
            PlatformTransactionManager transactionManager
    ) {
        this(
                expoPushClient,
                pushTokenRepository,
                ticketRepository,
                userRepository,
                transactionManager,
                Clock.systemUTC()
        );
    }

    PushDeliveryService(
            ExpoPushClient expoPushClient,
            PushTokenRepository pushTokenRepository,
            PushDeliveryTicketRepository ticketRepository,
            UserRepository userRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.expoPushClient = expoPushClient;
        this.pushTokenRepository = pushTokenRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void send(List<ExpoPushMessage> messages) {
        send(messages, null);
    }

    public void sendBefore(
            List<ExpoPushMessage> messages,
            Instant exclusiveDeadline
    ) {
        send(messages, exclusiveDeadline);
    }

    private void send(
            List<ExpoPushMessage> messages,
            Instant exclusiveDeadline
    ) {
        List<DeliveryTarget> targets = loadTargets(List.copyOf(messages));

        // 모든 토큰이 이미 삭제됐다면 Expo를 호출하지 않는다.
        if (targets.isEmpty()) {
            return;
        }

        List<ExpoPushMessage> request = targets.stream()
                .map(DeliveryTarget::message)
                .toList();

        if (exclusiveDeadline != null
                && !clock.instant().isBefore(exclusiveDeadline)) {
            return;
        }

        // request와 targets의 인덱스가 동일하다.
        List<ExpoPushTicket> tickets = expoPushClient.send(request);

        transactionTemplate.executeWithoutResult(status ->
                saveAcceptedTickets(targets, tickets)
        );
    }

    private List<DeliveryTarget> loadTargets(
            List<ExpoPushMessage> messages
    ) {
        return transactionTemplate.execute(status -> {
            List<DeliveryTarget> targets = new ArrayList<>();

            for (ExpoPushMessage message : messages) {
                PushToken pushToken = pushTokenRepository
                        .findByToken(message.to())
                        .orElse(null);

                // 로그아웃 등으로 이미 삭제된 토큰만 제외
                if (pushToken == null) {
                    continue;
                }

                targets.add(new DeliveryTarget(
                        message,
                        pushToken.getId(),
                        pushToken.getUser().getId()
                ));
            }

            return targets;
        });
    }

    private void saveAcceptedTickets(List<DeliveryTarget> targets, List<ExpoPushTicket> tickets) {
        // register/unregister/erase와 동일한 User 행을 먼저 잠근다.
        targets.stream()
                .map(DeliveryTarget::userId)
                .distinct()
                .sorted()
                .forEach(userRepository::findByIdForUpdate);

        List<PushDeliveryTicket> acceptedTickets = new ArrayList<>();

        for (int index = 0; index < tickets.size(); index++) {
            ExpoPushTicket ticket = tickets.get(index);

            if (!ticket.isAccepted()) {
                DeliveryTarget target = targets.get(index);
                String error = ticket.details() == null
                        ? null
                        : ticket.details().error();

                log.error(
                        "Expo push ticket rejected. "
                                + "userId={} pushTokenId={} status={} error={}",
                        target.userId(),
                        target.pushTokenId(),
                        ticket.status(),
                        StringUtils.hasText(error) ? error : "UNKNOWN"
                );
                continue;
            }

            if (!StringUtils.hasText(ticket.receiptId())) {
                throw new CustomException(PushErrorResponseCode.EXPO_PUSH_API_ERROR_502);
            }

            DeliveryTarget target = targets.get(index);
            PushToken currentToken = pushTokenRepository
                    .findById(target.pushTokenId())
                    .orElse(null);

            // 발송 도중 token이 삭제됐거나 변경됐다면 저장하지 않는다.
            if (currentToken == null
                    || !currentToken.getUser().getId().equals(target.userId())
                    || !currentToken.getToken().equals(target.message().to())) {
                continue;
            }

            acceptedTickets.add(PushDeliveryTicket.create(
                            ticket.receiptId(),
                            currentToken)
            );
        }

        if (!acceptedTickets.isEmpty()) {
            ticketRepository.saveAll(acceptedTickets);
        }
    }

    private record DeliveryTarget(
            ExpoPushMessage message,
            Long pushTokenId,
            Long userId
    ) {
    }
}
