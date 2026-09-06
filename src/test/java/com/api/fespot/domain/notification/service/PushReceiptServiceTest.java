package com.api.fespot.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.notification.entity.PushDeliveryTicket;
import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.notification.repository.PushDeliveryTicketRepository;
import com.api.fespot.domain.notification.repository.PushTokenRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.push.client.ExpoPushClient;
import com.api.fespot.global.push.client.dto.ExpoPushErrorDetails;
import com.api.fespot.global.push.client.dto.ExpoPushReceipt;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PushReceiptServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PUSH_TOKEN_ID = 10L;
    private static final Long TICKET_ID = 100L;
    private static final String TOKEN = "ExponentPushToken[token]";
    private static final String RECEIPT_ID = "receipt-id";

    private final ExpoPushClient expoPushClient = mock(ExpoPushClient.class);
    private final PushDeliveryTicketRepository ticketRepository =
            mock(PushDeliveryTicketRepository.class);
    private final PushTokenRepository pushTokenRepository =
            mock(PushTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    private PushReceiptService pushReceiptService;

    @BeforeEach
    void setUp() {
        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willAnswer(invocation -> new SimpleTransactionStatus());

        pushReceiptService = new PushReceiptService(
                expoPushClient,
                ticketRepository,
                pushTokenRepository,
                userRepository,
                transactionManager
        );
    }

    @Test
    void doesNotCallExpoWhenNoTicketIsReady() {
        given(ticketRepository.findReceiptTargets(
                eq(0L),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of());

        pushReceiptService.processPendingReceipts();

        verify(ticketRepository).deleteExpired(any(LocalDateTime.class));
        verifyNoInteractions(expoPushClient);
        verify(ticketRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void deletesTicketWhenProviderAccepted() {
        PushDeliveryTicket ticket = ticket(
                TICKET_ID,
                RECEIPT_ID,
                pushToken(PUSH_TOKEN_ID, USER_ID, TOKEN)
        );
        givenSingleBatch(ticket);
        given(expoPushClient.getReceipts(List.of(RECEIPT_ID)))
                .willReturn(Map.of(
                        RECEIPT_ID,
                        new ExpoPushReceipt("ok", null)
                ));

        pushReceiptService.processPendingReceipts();

        verify(ticketRepository)
                .deleteAllByIdInBatch(List.of(TICKET_ID));
        verifyNoInteractions(pushTokenRepository, userRepository);
    }

    @Test
    void deletesOnlyTicketForOtherReceiptError() {
        PushDeliveryTicket ticket = ticket(
                TICKET_ID,
                RECEIPT_ID,
                pushToken(PUSH_TOKEN_ID, USER_ID, TOKEN)
        );
        givenSingleBatch(ticket);
        given(expoPushClient.getReceipts(List.of(RECEIPT_ID)))
                .willReturn(Map.of(
                        RECEIPT_ID,
                        new ExpoPushReceipt(
                                "error",
                                new ExpoPushErrorDetails("MessageTooBig")
                        )
                ));

        pushReceiptService.processPendingReceipts();

        verify(ticketRepository)
                .deleteAllByIdInBatch(List.of(TICKET_ID));
        verify(ticketRepository, never())
                .deleteAllByPushTokenId(anyLong());
        verifyNoInteractions(pushTokenRepository, userRepository);
    }

    @Test
    void deletesTokenAndAllTicketsWhenDeviceNotRegistered() {
        PushToken currentToken = pushToken(PUSH_TOKEN_ID, USER_ID, TOKEN);
        User user = currentToken.getUser();
        PushDeliveryTicket ticket = ticket(
                TICKET_ID,
                RECEIPT_ID,
                currentToken
        );
        givenSingleBatch(ticket);
        given(expoPushClient.getReceipts(List.of(RECEIPT_ID)))
                .willReturn(Map.of(
                        RECEIPT_ID,
                        deviceNotRegisteredReceipt()
                ));
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(pushTokenRepository.findById(PUSH_TOKEN_ID))
                .willReturn(Optional.of(currentToken));

        pushReceiptService.processPendingReceipts();

        InOrder inOrder = inOrder(
                userRepository,
                pushTokenRepository,
                ticketRepository
        );
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(pushTokenRepository).findById(PUSH_TOKEN_ID);
        inOrder.verify(ticketRepository)
                .deleteAllByPushTokenId(PUSH_TOKEN_ID);
        inOrder.verify(pushTokenRepository).delete(currentToken);
        inOrder.verify(ticketRepository)
                .deleteAllByIdInBatch(List.of(TICKET_ID));
    }

    @Test
    void keepsNewTokenWhenTokenChangesDuringExpoCall() {
        PushToken oldToken = pushToken(PUSH_TOKEN_ID, USER_ID, TOKEN);
        PushToken newToken = pushToken(
                PUSH_TOKEN_ID,
                USER_ID,
                "ExponentPushToken[new]"
        );
        User user = newToken.getUser();
        PushDeliveryTicket ticket = ticket(
                TICKET_ID,
                RECEIPT_ID,
                oldToken
        );
        givenSingleBatch(ticket);
        given(expoPushClient.getReceipts(List.of(RECEIPT_ID)))
                .willReturn(Map.of(
                        RECEIPT_ID,
                        deviceNotRegisteredReceipt()
                ));
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(pushTokenRepository.findById(PUSH_TOKEN_ID))
                .willReturn(Optional.of(newToken));

        pushReceiptService.processPendingReceipts();

        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(pushTokenRepository).findById(PUSH_TOKEN_ID);
        verify(ticketRepository, never())
                .deleteAllByPushTokenId(anyLong());
        verify(pushTokenRepository, never()).delete(any(PushToken.class));
        verify(ticketRepository)
                .deleteAllByIdInBatch(List.of(TICKET_ID));
    }

    @Test
    void handlesDeviceNotRegisteredWhenTokenWasAlreadyDeleted() {
        PushToken deletedToken = pushToken(PUSH_TOKEN_ID, USER_ID, TOKEN);
        User user = deletedToken.getUser();
        PushDeliveryTicket ticket = ticket(
                TICKET_ID,
                RECEIPT_ID,
                deletedToken
        );
        givenSingleBatch(ticket);
        given(expoPushClient.getReceipts(List.of(RECEIPT_ID)))
                .willReturn(Map.of(
                        RECEIPT_ID,
                        deviceNotRegisteredReceipt()
                ));
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(pushTokenRepository.findById(PUSH_TOKEN_ID))
                .willReturn(Optional.empty());

        pushReceiptService.processPendingReceipts();

        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(pushTokenRepository).findById(PUSH_TOKEN_ID);
        verify(ticketRepository, never())
                .deleteAllByPushTokenId(anyLong());
        verify(pushTokenRepository, never()).delete(any(PushToken.class));
        verify(ticketRepository)
                .deleteAllByIdInBatch(List.of(TICKET_ID));
    }

    @Test
    void keepsTicketWhenReceiptIsMissingOrStatusIsUnknown() {
        PushDeliveryTicket missingTicket = ticket(
                100L,
                "missing-receipt",
                pushToken(10L, USER_ID, TOKEN)
        );
        PushDeliveryTicket unknownTicket = ticket(
                101L,
                "unknown-receipt",
                pushToken(11L, 2L, "ExponentPushToken[unknown]")
        );
        givenSingleBatch(List.of(missingTicket, unknownTicket));
        given(expoPushClient.getReceipts(List.of(
                "missing-receipt",
                "unknown-receipt"
        ))).willReturn(Map.of(
                "unknown-receipt",
                new ExpoPushReceipt("unknown", null)
        ));

        pushReceiptService.processPendingReceipts();

        verify(ticketRepository, never()).deleteAllByIdInBatch(any());
        verifyNoInteractions(pushTokenRepository, userRepository);
    }

    @Test
    void leavesReadyTicketsUnchangedWhenExpoCallFails() {
        PushDeliveryTicket ticket = ticket(
                TICKET_ID,
                RECEIPT_ID,
                pushToken(PUSH_TOKEN_ID, USER_ID, TOKEN)
        );
        givenSingleBatch(ticket);
        given(expoPushClient.getReceipts(List.of(RECEIPT_ID)))
                .willThrow(new RuntimeException("Expo API failure"));

        assertThatThrownBy(pushReceiptService::processPendingReceipts)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Expo API failure");

        verify(ticketRepository, never()).deleteAllByIdInBatch(any());
        verify(ticketRepository, never())
                .deleteAllByPushTokenId(anyLong());
        verifyNoInteractions(pushTokenRepository, userRepository);
    }

    @Test
    void locksDeviceNotRegisteredUsersOnceInAscendingOrder() {
        PushDeliveryTicket first = ticket(
                100L,
                "receipt-user-2-a",
                pushToken(10L, 2L, "ExponentPushToken[user-2-a]")
        );
        PushDeliveryTicket second = ticket(
                101L,
                "receipt-user-1",
                pushToken(11L, 1L, "ExponentPushToken[user-1]")
        );
        PushDeliveryTicket third = ticket(
                102L,
                "receipt-user-2-b",
                pushToken(12L, 2L, "ExponentPushToken[user-2-b]")
        );
        givenSingleBatch(List.of(first, second, third));
        given(expoPushClient.getReceipts(any()))
                .willReturn(Map.of(
                        "receipt-user-2-a", deviceNotRegisteredReceipt(),
                        "receipt-user-1", deviceNotRegisteredReceipt(),
                        "receipt-user-2-b", deviceNotRegisteredReceipt()
                ));

        pushReceiptService.processPendingReceipts();

        InOrder inOrder = inOrder(userRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(userRepository).findByIdForUpdate(2L);
        verify(userRepository, times(2)).findByIdForUpdate(anyLong());
    }

    @Test
    void processesMoreThanOneThousandTicketsInBatches() {
        PushToken pushToken = pushToken(PUSH_TOKEN_ID, USER_ID, TOKEN);
        List<PushDeliveryTicket> firstBatch = new ArrayList<>();

        for (long id = 1L; id <= 1_000L; id++) {
            firstBatch.add(ticket(id, "receipt-" + id, pushToken));
        }

        PushDeliveryTicket lastTicket = ticket(
                1_001L,
                "receipt-1001",
                pushToken
        );

        given(ticketRepository.findReceiptTargets(
                eq(0L),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(firstBatch);
        given(ticketRepository.findReceiptTargets(
                eq(1_000L),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of(lastTicket));
        given(ticketRepository.findReceiptTargets(
                eq(1_001L),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of());
        given(expoPushClient.getReceipts(any()))
                .willAnswer(invocation -> {
                    List<String> receiptIds = invocation.getArgument(0);
                    Map<String, ExpoPushReceipt> receipts = new HashMap<>();
                    receiptIds.forEach(receiptId -> receipts.put(
                            receiptId,
                            new ExpoPushReceipt("ok", null)
                    ));
                    return receipts;
                });

        pushReceiptService.processPendingReceipts();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> receiptIdsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(expoPushClient, times(2))
                .getReceipts(receiptIdsCaptor.capture());
        assertThat(receiptIdsCaptor.getAllValues())
                .extracting(List::size)
                .containsExactly(1_000, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> deletedIdsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(ticketRepository, times(2))
                .deleteAllByIdInBatch(deletedIdsCaptor.capture());
        assertThat(deletedIdsCaptor.getAllValues())
                .extracting(List::size)
                .containsExactly(1_000, 1);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository, times(3)).findReceiptTargets(
                anyLong(),
                any(LocalDateTime.class),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getAllValues())
                .allSatisfy(pageable -> {
                    assertThat(pageable.getPageNumber()).isZero();
                    assertThat(pageable.getPageSize()).isEqualTo(1_000);
                });
    }

    @Test
    void usesFifteenMinuteReadyAndTwentyFourHourExpiryCutoffs() {
        given(ticketRepository.findReceiptTargets(
                eq(0L),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of());

        LocalDateTime beforeExecution = LocalDateTime.now();
        pushReceiptService.processPendingReceipts();
        LocalDateTime afterExecution = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> expiredCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> readyCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(ticketRepository).deleteExpired(expiredCaptor.capture());
        verify(ticketRepository).findReceiptTargets(
                eq(0L),
                readyCaptor.capture(),
                any(Pageable.class)
        );

        assertThat(readyCaptor.getValue())
                .isAfterOrEqualTo(beforeExecution.minusMinutes(15))
                .isBeforeOrEqualTo(afterExecution.minusMinutes(15));
        assertThat(expiredCaptor.getValue())
                .isAfterOrEqualTo(beforeExecution.minusHours(24))
                .isBeforeOrEqualTo(afterExecution.minusHours(24));
    }

    private void givenSingleBatch(PushDeliveryTicket ticket) {
        givenSingleBatch(List.of(ticket));
    }

    private void givenSingleBatch(List<PushDeliveryTicket> tickets) {
        Long lastTicketId = tickets.get(tickets.size() - 1).getId();

        given(ticketRepository.findReceiptTargets(
                eq(0L),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(tickets);
        given(ticketRepository.findReceiptTargets(
                eq(lastTicketId),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of());
    }

    private PushDeliveryTicket ticket(
            Long ticketId,
            String receiptId,
            PushToken pushToken
    ) {
        PushDeliveryTicket ticket = mock(PushDeliveryTicket.class);
        given(ticket.getId()).willReturn(ticketId);
        given(ticket.getReceiptId()).willReturn(receiptId);
        given(ticket.getToken()).willReturn(pushToken);
        return ticket;
    }

    private PushToken pushToken(
            Long pushTokenId,
            Long userId,
            String tokenValue
    ) {
        User user = mock(User.class);
        given(user.getId()).willReturn(userId);

        PushToken pushToken = mock(PushToken.class);
        given(pushToken.getId()).willReturn(pushTokenId);
        given(pushToken.getUser()).willReturn(user);
        given(pushToken.getToken()).willReturn(tokenValue);
        return pushToken;
    }

    private ExpoPushReceipt deviceNotRegisteredReceipt() {
        return new ExpoPushReceipt(
                "error",
                new ExpoPushErrorDetails("DeviceNotRegistered")
        );
    }
}
