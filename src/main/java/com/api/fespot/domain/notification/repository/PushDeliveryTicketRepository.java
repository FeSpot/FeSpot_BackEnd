package com.api.fespot.domain.notification.repository;

import com.api.fespot.domain.notification.entity.PushDeliveryTicket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PushDeliveryTicketRepository extends JpaRepository<PushDeliveryTicket, Long> {

    @Modifying
    @Query("""
            delete from PushDeliveryTicket ticket
            where ticket.token.id = :pushTokenId
            """)
    void deleteAllByPushTokenId(@Param("pushTokenId") Long pushTokenId); //특정 기기 1개의 ticket 삭제


    @Modifying
    @Query("""
            delete from PushDeliveryTicket ticket
            where ticket.token.user.id = :userId
            """)
    void deleteAllByUserId(@Param("userId") Long userId);

    @Query("""
            select ticket
            from PushDeliveryTicket ticket
            join fetch ticket.token pushToken
            join fetch pushToken.user
            where ticket.id > :lastSeenId
              and ticket.createdAt <= :readyBefore
            order by ticket.id asc
            """)
    List<PushDeliveryTicket> findReceiptTargets(
            @Param("lastSeenId") Long lastSeenId,
            @Param("readyBefore") LocalDateTime readyBefore,
            Pageable pageable
    );

    @Modifying
    @Query("""
            delete from PushDeliveryTicket ticket
            where ticket.createdAt < :expiredBefore
            """)
    int deleteExpired(
            @Param("expiredBefore") LocalDateTime expiredBefore
    );
}
