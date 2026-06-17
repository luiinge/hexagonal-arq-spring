package com.example.auth.persistence;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "roles")
@Getter
public class AuthRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;
}
