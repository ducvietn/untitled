package com.teamup.teamup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Adds created_at / updated_at auditing columns to every entity that extends it.
 * Combine with {@link BaseEntity} for the full audit mixin:
 * <pre>
 * &#64;Entity
 * public class MyEntity extends BaseEntity implements AuditableEntity { … }
 * </pre>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public interface AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime getCreatedAt();

    @LastModifiedDate
    @Column(name = "updated_at")
    LocalDateTime getUpdatedAt();
}
