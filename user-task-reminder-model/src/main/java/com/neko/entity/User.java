package com.neko.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@Data
@Entity
@Table(name = "user_entity")
public class User {
    @Id
    private UUID id;
    private String userName;
    private String email;
    private String firstName;
    private String lastName;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private List<String> roles;

    @OneToMany(mappedBy = "createdBy")
    private List<Task> tasks;
}
