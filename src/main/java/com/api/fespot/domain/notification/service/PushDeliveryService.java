package com.api.fespot.domain.notification.service;

import com.api.fespot.domain.notification.entity.PushDeliveryTicket;
import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.notification.repository.PushDeliveryTicketRepository;
import com.api.fespot.domain.notification.repository.PushTokenRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.push.client.ExpoPushClient;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import com.api.fespot.global.push.client.dto.ExpoPushTicket;
import com.api.fespot.global.push.exception.PushErrorResponseCode;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class PushDeliveryService {

    private final ExpoPushClient expoPushClient;
    private final PushTokenRepository pushTokenRepository;
    private final PushDeliveryTicketRepository ticketRepository;
    private final TransactionTemplate transactionTemplate;

    public PushDeliveryService(
            ExpoPushClient expoPushClient,
            PushTokenRepository pushTokenRepository,
            PushDeliveryTicketRepository ticketRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.expoPushClient = expoPushClient;
        this.pushTokenRepository = pushTokenRepository;
        this.ticketRepository = ticketRepository;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public void send(List<ExpoPushMessage> messages) {
        // 1. 짧은 조회 트랜잭션
        List<PushToken> pushTokens = transactionTemplate.execute(status ->
                messages.stream()
                        .map(message ->
                                pushTokenRepository.findByToken(message.to())
                                        .orElseThrow(() -> new IllegalStateException("등록된 PushToken을 찾을 수 없습니다."))
                        ).toList()
        );

        // 2. 트랜잭션 밖에서 Expo 호출
        List<ExpoPushTicket> tickets = expoPushClient.send(messages);

        // 3. 짧은 저장 트랜잭션
        transactionTemplate.executeWithoutResult(
                status -> saveAcceptedTickets(pushTokens, tickets)
        );
    }

    private void saveAcceptedTickets(List<PushToken> pushTokens, List<ExpoPushTicket> tickets) {
        List<PushDeliveryTicket> acceptedTickets = new ArrayList<>();

        for (int index = 0; index < tickets.size(); index++) {
            ExpoPushTicket ticket = tickets.get(index);

            if (!ticket.isAccepted()) {
                continue;
            }

            if (!StringUtils.hasText(ticket.receiptId())) {
                throw new CustomException(PushErrorResponseCode.EXPO_PUSH_API_ERROR_502);
            }

            acceptedTickets.add(PushDeliveryTicket.create(
                            ticket.receiptId(),
                            pushTokens.get(index))
            );
        }

        if (!acceptedTickets.isEmpty()) {
            ticketRepository.saveAll(acceptedTickets);
        }
    }
}