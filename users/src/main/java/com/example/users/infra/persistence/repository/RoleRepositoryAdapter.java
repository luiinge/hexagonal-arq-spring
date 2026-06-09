package com.example.users.infra.persistence.repository;

import com.example.users.domain.model.Role;
import com.example.users.domain.spi.RoleRepository;
import com.example.users.infra.persistence.mapper.RoleEntityMapper;
import com.example.users.infra.persistence.repository.jpa.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {

	private final RoleJpaRepository roleJpaRepository;
	private final RoleEntityMapper roleEntityMapper;


	@Override
	public Optional<Role> findByName(String name) {
		return roleJpaRepository.findByName(name).map(roleEntityMapper::toDomain);
	}

}
