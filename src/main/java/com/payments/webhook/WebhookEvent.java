package com.payments.webhook;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events")
@Data
@NoArgsConstructor
public class WebhookEvent {
    @Id
    @Column(name = "event_id")
    private String eventId; 
    
    @Column(nullable = false)
    private String provider;
    
    @Column(name = "raw_payload", columnDefinition = "TEXT", nullable = false)
    private String rawPayload;
    
    @Column(nullable = false)
    private String status; 
    
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();
}
