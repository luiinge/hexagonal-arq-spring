package com.example.users.infra.persistence.repository.jpa;

import com.example.users.infra.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByUsername(String username);

	boolean existsByUsernameAndIdNot(String username, Long id);

	boolean existsByUsername(String username);
}
