package com.api.fespot.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.notification.entity.PushDeliveryTicket;
import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.notification.repository.PushDeliveryTicketRepository;
import com.api.fespot.domain.notification.repository.PushTokenRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.push.client.ExpoPushClient;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import com.api.fespot.global.push.client.dto.ExpoPushTicket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PushDeliveryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PUSH_TOKEN_ID = 10L;
    private static final String SENT_TOKEN = "ExponentPushToken[sent]";
    private static final String RECEIPT_ID = "receipt-id";

    private final ExpoPushClient expoPushClient = mock(ExpoPushClient.class);
    private final PushTokenRepository pushTokenRepository = mock(PushTokenRepository.class);
    private final PushDeliveryTicketRepository ticketRepository =
            mock(PushDeliveryTicketRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    private PushDeliveryService pushDeliveryService;

    @BeforeEach
    void setUp() {
        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willAnswer(invocation -> new SimpleTransactionStatus());

        pushDeliveryService = new PushDeliveryService(
                expoPushClient,
                pushTokenRepository,
                ticketRepository,
                userRepository,
                transactionManager
        );
    }

    @Test
    void savesReceiptWhenPushTokenRegistrationIsUnchanged() {
        User user = mock(User.class);
        PushToken sentToken = mock(PushToken.class);
        PushToken currentToken = mock(PushToken.class);
        ExpoPushMessage message = message(SENT_TOKEN);
        List<ExpoPushMessage> messages = List.of(message);

        given(user.getId()).willReturn(USER_ID);
        given(sentToken.getId()).willReturn(PUSH_TOKEN_ID);
        given(sentToken.getUser()).willReturn(user);
        given(currentToken.getUser()).willReturn(user);
        given(currentToken.getToken()).willReturn(SENT_TOKEN);
        given(pushTokenRepository.findByToken(SENT_TOKEN)).willReturn(Optional.of(sentToken));
        given(expoPushClient.send(messages))
                .willReturn(List.of(new ExpoPushTicket("ok", RECEIPT_ID, null)));
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(pushTokenRepository.findById(PUSH_TOKEN_ID))
                .willReturn(Optional.of(currentToken));

        pushDeliveryService.send(messages);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushDeliveryTicket>> ticketsCaptor =
                ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(
                pushTokenRepository,
                expoPushClient,
                userRepository,
                ticketRepository
        );
        inOrder.verify(pushTokenRepository).findByToken(SENT_TOKEN);
        inOrder.verify(expoPushClient).send(messages);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(pushTokenRepository).findById(PUSH_TOKEN_ID);
        inOrder.verify(ticketRepository).saveAll(ticketsCaptor.capture());

        assertThat(ticketsCaptor.getValue())
                .singleElement()
                .satisfies(ticket -> {
                    assertThat(ticket.getReceiptId()).isEqualTo(RECEIPT_ID);
                    assertThat(ticket.getToken()).isSameAs(currentToken);
                });
    }

    @Test
    void skipsReceiptWhenPushTokenChangesDuringExpoCall() {
        User user = mock(User.class);
        PushToken sentToken = mock(PushToken.class);
        PushToken changedToken = mock(PushToken.class);
        ExpoPushMessage message = message(SENT_TOKEN);
        List<ExpoPushMessage> messages = List.of(message);

        given(user.getId()).willReturn(USER_ID);
        given(sentToken.getId()).willReturn(PUSH_TOKEN_ID);
        given(sentToken.getUser()).willReturn(user);
        given(changedToken.getUser()).willReturn(user);
        given(changedToken.getToken()).willReturn("ExponentPushToken[changed]");
        given(pushTokenRepository.findByToken(SENT_TOKEN)).willReturn(Optional.of(sentToken));
        given(expoPushClient.send(messages))
                .willReturn(List.of(new ExpoPushTicket("ok", RECEIPT_ID, null)));
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(pushTokenRepository.findById(PUSH_TOKEN_ID))
                .willReturn(Optional.of(changedToken));

        pushDeliveryService.send(messages);

        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(pushTokenRepository).findById(PUSH_TOKEN_ID);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void skipsReceiptWhenPushTokenIsDeletedDuringExpoCall() {
        User user = mock(User.class);
        PushToken sentToken = mock(PushToken.class);
        ExpoPushMessage message = message(SENT_TOKEN);
        List<ExpoPushMessage> messages = List.of(message);

        given(user.getId()).willReturn(USER_ID);
        given(sentToken.getId()).willReturn(PUSH_TOKEN_ID);
        given(sentToken.getUser()).willReturn(user);
        given(pushTokenRepository.findByToken(SENT_TOKEN)).willReturn(Optional.of(sentToken));
        given(expoPushClient.send(messages))
                .willReturn(List.of(new ExpoPushTicket("ok", RECEIPT_ID, null)));
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(pushTokenRepository.findById(PUSH_TOKEN_ID))
                .willReturn(Optional.empty());

        pushDeliveryService.send(messages);

        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(pushTokenRepository).findById(PUSH_TOKEN_ID);
        verifyNoInteractions(ticketRepository);
    }

    private ExpoPushMessage message(String token) {
        return new ExpoPushMessage(
                token,
                "title",
                "body",
                Map.of(),
                "default",
                "default",
                null
        );
    }
}
