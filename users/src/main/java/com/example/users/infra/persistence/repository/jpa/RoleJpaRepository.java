package com.example.users.infra.persistence.repository.jpa;

import com.example.users.infra.persistence.entities.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

	 Optional<RoleEntity> findByName(String name);
}
