package com.example.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

class DomainUser extends User {

    private final String email;

    DomainUser(String username, String password, String email,
               Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.email = email;
    }

    String getEmail() {
        return email;
    }
}
