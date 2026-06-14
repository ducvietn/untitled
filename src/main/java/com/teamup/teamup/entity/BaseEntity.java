package com.teamup.teamup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base entity carrying the primary key and optimistic-locking version field.
 * All domain entities extend this class.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-locking version. Hibernate increments this on every dirty write,
     * and throws {@link org.hibernate.StaleObjectStateException} on concurrent
     * modification — far cheaper than pessimistic row-level locks.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
