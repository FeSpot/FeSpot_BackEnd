package com.api.fespot.domain.notification.entity;

import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "push_delivery_tickets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_delivery_ticket_receipt_id",
                columnNames = "receipt_id"
        )
)
public class PushDeliveryTicket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_id", nullable = false)
    private String receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "push_token_id", nullable = false)
    private PushToken token;

    private PushDeliveryTicket(String receiptId, PushToken token) {
        this.receiptId = receiptId;
        this.token = token;
    }

    public static PushDeliveryTicket create(String receiptId, PushToken token) {
        return new PushDeliveryTicket(receiptId, token);
    }
}
