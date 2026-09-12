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
import com.api.fespot.global.push.client.dto.ExpoPushErrorDetails;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import com.api.fespot.global.push.client.dto.ExpoPushTicket;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(OutputCaptureExtension.class)
class PushDeliveryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PUSH_TOKEN_ID = 10L;
    private static final String SENT_TOKEN = "ExponentPushToken[sent]";
    private static final String RECEIPT_ID = "receipt-id";
    private static final Instant SEND_DEADLINE = Instant.parse("2026-08-07T01:00:00Z");

    private final ExpoPushClient expoPushClient = mock(ExpoPushClient.class);
    private final PushTokenRepository pushTokenRepository = mock(PushTokenRepository.class);
    private final PushDeliveryTicketRepository ticketRepository =
            mock(PushDeliveryTicketRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);
    private final Clock clock = mock(Clock.class);

    private PushDeliveryService pushDeliveryService;

    @BeforeEach
    void setUp() {
        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willAnswer(invocation -> new SimpleTransactionStatus());
        given(clock.instant()).willReturn(SEND_DEADLINE.minusSeconds(1));

        pushDeliveryService = new PushDeliveryService(
                expoPushClient,
                pushTokenRepository,
                ticketRepository,
                userRepository,
                transactionManager,
                clock
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

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void doesNotStartExpoRequestAtOrAfterDeadline(long secondsAfterDeadline) {
        User user = mock(User.class);
        PushToken sentToken = mock(PushToken.class);
        ExpoPushMessage message = message(SENT_TOKEN);

        given(sentToken.getId()).willReturn(PUSH_TOKEN_ID);
        given(sentToken.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
        given(pushTokenRepository.findByToken(SENT_TOKEN))
                .willReturn(Optional.of(sentToken));
        given(clock.instant()).willReturn(SEND_DEADLINE.plusSeconds(secondsAfterDeadline));

        pushDeliveryService.sendBefore(List.of(message), SEND_DEADLINE);

        verify(pushTokenRepository).findByToken(SENT_TOKEN);
        verifyNoInteractions(expoPushClient, ticketRepository, userRepository);
    }

    @Test
    void doesNotStartExpoRequestWhenDeadlinePassesDuringTokenLookup() {
        User user = mock(User.class);
        PushToken sentToken = mock(PushToken.class);
        ExpoPushMessage message = message(SENT_TOKEN);

        given(user.getId()).willReturn(USER_ID);
        given(sentToken.getId()).willReturn(PUSH_TOKEN_ID);
        given(sentToken.getUser()).willReturn(user);
        given(pushTokenRepository.findByToken(SENT_TOKEN)).willAnswer(invocation -> {
            given(clock.instant()).willReturn(SEND_DEADLINE);
            return Optional.of(sentToken);
        });

        pushDeliveryService.sendBefore(List.of(message), SEND_DEADLINE);

        verify(pushTokenRepository).findByToken(SENT_TOKEN);
        verifyNoInteractions(expoPushClient, ticketRepository, userRepository);
    }

    @Test
    void savesReceiptWhenExpoRequestStartedBeforeDeadlineFinishesAfterDeadline() {
        User user = mock(User.class);
        PushToken sentToken = mock(PushToken.class);
        List<ExpoPushMessage> messages = List.of(message(SENT_TOKEN));

        given(user.getId()).willReturn(USER_ID);
        given(sentToken.getId()).willReturn(PUSH_TOKEN_ID);
        given(sentToken.getUser()).willReturn(user);
        given(sentToken.getToken()).willReturn(SENT_TOKEN);
        given(pushTokenRepository.findByToken(SENT_TOKEN)).willReturn(Optional.of(sentToken));
        given(expoPushClient.send(messages)).willAnswer(invocation -> {
            given(clock.instant()).willReturn(SEND_DEADLINE.plusSeconds(1));
            return List.of(new ExpoPushTicket("ok", RECEIPT_ID, null));
        });
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(pushTokenRepository.findById(PUSH_TOKEN_ID)).willReturn(Optional.of(sentToken));

        pushDeliveryService.sendBefore(messages, SEND_DEADLINE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushDeliveryTicket>> ticketsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(expoPushClient).send(messages);
        verify(ticketRepository).saveAll(ticketsCaptor.capture());
        assertThat(ticketsCaptor.getValue())
                .singleElement()
                .satisfies(ticket -> {
                    assertThat(ticket.getReceiptId()).isEqualTo(RECEIPT_ID);
                    assertThat(ticket.getToken()).isSameAs(sentToken);
                });
    }

    @Test
    void logsRejectedTicketAndSavesAcceptedTicket(CapturedOutput output) {
        Long rejectedUserId = 2L;
        Long rejectedPushTokenId = 20L;
        String rejectedToken = "ExponentPushToken[rejected]";
        User acceptedUser = mock(User.class);
        User rejectedUser = mock(User.class);
        PushToken acceptedToken = mock(PushToken.class);
        PushToken rejectedTokenRegistration = mock(PushToken.class);
        List<ExpoPushMessage> messages = List.of(
                message(SENT_TOKEN),
                message(rejectedToken)
        );

        given(acceptedUser.getId()).willReturn(USER_ID);
        given(rejectedUser.getId()).willReturn(rejectedUserId);
        given(acceptedToken.getId()).willReturn(PUSH_TOKEN_ID);
        given(acceptedToken.getUser()).willReturn(acceptedUser);
        given(acceptedToken.getToken()).willReturn(SENT_TOKEN);
        given(rejectedTokenRegistration.getId()).willReturn(rejectedPushTokenId);
        given(rejectedTokenRegistration.getUser()).willReturn(rejectedUser);
        given(pushTokenRepository.findByToken(SENT_TOKEN))
                .willReturn(Optional.of(acceptedToken));
        given(pushTokenRepository.findByToken(rejectedToken))
                .willReturn(Optional.of(rejectedTokenRegistration));
        given(expoPushClient.send(messages)).willReturn(List.of(
                new ExpoPushTicket("ok", RECEIPT_ID, null),
                new ExpoPushTicket(
                        "error",
                        null,
                        new ExpoPushErrorDetails("DeviceNotRegistered")
                )
        ));
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(acceptedUser));
        given(userRepository.findByIdForUpdate(rejectedUserId))
                .willReturn(Optional.of(rejectedUser));
        given(pushTokenRepository.findById(PUSH_TOKEN_ID))
                .willReturn(Optional.of(acceptedToken));

        pushDeliveryService.send(messages);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushDeliveryTicket>> ticketsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(ticketsCaptor.capture());
        assertThat(ticketsCaptor.getValue()).singleElement();
        assertThat(output.getAll())
                .contains(
                        "Expo push ticket rejected. "
                                + "userId=2 pushTokenId=20 status=error "
                                + "error=DeviceNotRegistered"
                )
                .doesNotContain(rejectedToken)
                .doesNotContain("body");
    }

    @Test
    void logsUnknownWhenRejectedTicketHasNoErrorDetails(CapturedOutput output) {
        User user = mock(User.class);
        PushToken sentToken = mock(PushToken.class);
        List<ExpoPushMessage> messages = List.of(message(SENT_TOKEN));

        given(user.getId()).willReturn(USER_ID);
        given(sentToken.getId()).willReturn(PUSH_TOKEN_ID);
        given(sentToken.getUser()).willReturn(user);
        given(pushTokenRepository.findByToken(SENT_TOKEN))
                .willReturn(Optional.of(sentToken));
        given(expoPushClient.send(messages))
                .willReturn(List.of(new ExpoPushTicket("error", null, null)));

        pushDeliveryService.send(messages);

        assertThat(output.getAll())
                .contains(
                        "Expo push ticket rejected. "
                                + "userId=1 pushTokenId=10 status=error error=UNKNOWN"
                );
        verifyNoInteractions(ticketRepository);
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
