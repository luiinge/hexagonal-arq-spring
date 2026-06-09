package com.example.users.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class User {

    private Long id;
    private String username;
    private String password;
    private boolean active;
    private String email; 
    private List<Role> roles;

}
