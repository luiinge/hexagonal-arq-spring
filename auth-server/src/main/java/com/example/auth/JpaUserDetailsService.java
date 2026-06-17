package com.example.auth;

import com.example.auth.persistence.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class JpaUserDetailsService implements UserDetailsService {

    private final AuthUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var entity = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(username));

        if (!entity.isActive()) {
            throw new UsernameNotFoundException("User is inactive: " + username);
        }

        var authorities = entity.getRoles().stream()
            .map(r -> new SimpleGrantedAuthority(r.getName()))
            .collect(Collectors.toList());

        return new DomainUser(entity.getUsername(), entity.getPassword(), entity.getEmail(), authorities);
    }
}
