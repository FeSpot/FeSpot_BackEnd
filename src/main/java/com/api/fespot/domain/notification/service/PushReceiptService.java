package com.api.fespot.domain.notification.service;

import com.api.fespot.domain.notification.entity.PushDeliveryTicket;
import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.notification.repository.PushDeliveryTicketRepository;
import com.api.fespot.domain.notification.repository.PushTokenRepository;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.push.client.ExpoPushClient;
import com.api.fespot.global.push.client.ExpoPushErrorCode;
import com.api.fespot.global.push.client.dto.ExpoPushReceipt;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PushReceiptService {

    private static final int RECEIPT_BATCH_SIZE = 1_000;
    private static final Duration RECEIPT_READY_DELAY =
            Duration.ofMinutes(15);
    private static final Duration RECEIPT_RETENTION =
            Duration.ofHours(24);

    private final ExpoPushClient expoPushClient;
    private final PushDeliveryTicketRepository ticketRepository;
    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public PushReceiptService(
            ExpoPushClient expoPushClient,
            PushDeliveryTicketRepository ticketRepository,
            PushTokenRepository pushTokenRepository,
            UserRepository userRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.expoPushClient = expoPushClient;
        this.ticketRepository = ticketRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.userRepository = userRepository;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public void processPendingReceipts() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime readyBefore =
                now.minus(RECEIPT_READY_DELAY);
        LocalDateTime expiredBefore =
                now.minus(RECEIPT_RETENTION);

        // Expo에서 더 이상 조회할 수 없는 ticket 정리
        transactionTemplate.executeWithoutResult(status ->
                ticketRepository.deleteExpired(expiredBefore)
        );

        long lastSeenId = 0L;

        while (true) {
            List<ReceiptTarget> targets =
                    loadTargets(lastSeenId, readyBefore);

            if (targets.isEmpty()) {
                return;
            }

            List<String> receiptIds = targets.stream()
                    .map(ReceiptTarget::receiptId)
                    .toList();

            // 외부 API 호출 중에는 DB 트랜잭션을 유지하지 않는다.
            Map<String, ExpoPushReceipt> receipts =
                    expoPushClient.getReceipts(receiptIds);

            transactionTemplate.executeWithoutResult(status ->
                    applyReceipts(targets, receipts)
            );

            // 응답이 누락된 ticket이 있어도 다음 묶음으로 이동한다.
            lastSeenId = targets.get(targets.size() - 1).ticketId();
        }
    }

    private List<ReceiptTarget> loadTargets(
            long lastSeenId,
            LocalDateTime readyBefore
    ) {
        return transactionTemplate.execute(status ->
                ticketRepository.findReceiptTargets(
                                lastSeenId,
                                readyBefore,
                                PageRequest.of(0, RECEIPT_BATCH_SIZE)
                        )
                        .stream()
                        .map(ticket -> {
                            PushToken pushToken = ticket.getToken();

                            return new ReceiptTarget(
                                    ticket.getId(),
                                    ticket.getReceiptId(),
                                    pushToken.getId(),
                                    pushToken.getUser().getId(),
                                    pushToken.getToken()
                            );
                        })
                        .toList()
        );
    }

    private void applyReceipts(
            List<ReceiptTarget> targets,
            Map<String, ExpoPushReceipt> receipts
    ) {
        // DeviceNotRegistered인 사용자만 User 행을 잠근다.
        targets.stream()
                .filter(target -> {
                    ExpoPushReceipt receipt =
                            receipts.get(target.receiptId());

                    return receipt != null
                            && "error".equals(receipt.status())
                            && receipt.errorCode()
                            == ExpoPushErrorCode.DEVICE_NOT_REGISTERED;
                })
                .map(ReceiptTarget::userId)
                .distinct()
                .sorted()
                .forEach(userRepository::findByIdForUpdate);

        List<Long> resolvedTicketIds = new ArrayList<>();

        for (ReceiptTarget target : targets) {
            ExpoPushReceipt receipt =
                    receipts.get(target.receiptId());

            // 응답에 포함되지 않은 receipt는 다음 실행에서 재조회
            if (receipt == null) {
                continue;
            }

            // FCM/APNs가 인수한 발송 건
            if (receipt.isAcceptedByProvider()) {
                resolvedTicketIds.add(target.ticketId());
                continue;
            }

            // 알 수 없는 status는 잘못 삭제하지 않고 유지
            if (!"error".equals(receipt.status())) {
                continue;
            }

            if (receipt.errorCode()
                    == ExpoPushErrorCode.DEVICE_NOT_REGISTERED) {
                deleteTokenIfStillCurrent(target);
            }

            /*
             * error도 확인이 끝난 최종 결과다.
             * 원본 메시지를 보관하지 않으므로 receipt를 다시 조회해도
             * 재발송할 수 없다.
             */
            resolvedTicketIds.add(target.ticketId());
        }

        if (!resolvedTicketIds.isEmpty()) {
            ticketRepository.deleteAllByIdInBatch(
                    resolvedTicketIds
            );
        }
    }

    private void deleteTokenIfStillCurrent(
            ReceiptTarget target
    ) {
        PushToken currentToken = pushTokenRepository
                .findById(target.pushTokenId())
                .orElse(null);

        if (currentToken == null
                || !currentToken.getUser().getId()
                .equals(target.userId())
                || !currentToken.getToken()
                .equals(target.tokenValue())) {
            return;
        }

        ticketRepository.deleteAllByPushTokenId(currentToken.getId());
        pushTokenRepository.delete(currentToken);
    }

    private record ReceiptTarget(
            Long ticketId,
            String receiptId,
            Long pushTokenId,
            Long userId,
            String tokenValue
    ) {
    }
}