package com.neko.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
public class Notification {
    @Id
    private UUID id;
    private UUID userId;
    private UUID taskId;
    private String message;
    private Boolean seen;
    private LocalDateTime createdDate;
}
