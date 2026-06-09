package com.example.users.infra.persistence.repository;

import com.example.users.domain.model.User;
import com.example.users.domain.spi.UserRepository;
import com.example.users.infra.persistence.mapper.UserEntityMapper;
import com.example.users.infra.persistence.repository.jpa.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

	private final UserJpaRepository userJpaRepository;
	private final UserEntityMapper userEntityMapper;


	@Override
	public Optional<User> findByUsername(String username) {
		return userJpaRepository.findByUsername(username).map(userEntityMapper::toDomain);
	}

	@Override
	public boolean existsById(Long id) {
		return userJpaRepository.existsById(id);
	}

	@Override
	public void deleteById(Long id) {
		userJpaRepository.deleteById(id);
	}

	@Override
	public Optional<User> findById(Long id) {
		return userJpaRepository.findById(id).map(userEntityMapper::toDomain);
	}

	@Override
	public List<User> getAllUsers() {
		return userJpaRepository.findAll().stream().map(userEntityMapper::toDomain).toList();
	}

	@Override
	public User save(User user) {
		return userEntityMapper.toDomain(userJpaRepository.save(userEntityMapper.toEntity(user)));
	}

	@Override
	public boolean existsByUsername(String username) {
		return userJpaRepository.existsByUsername(username);
	}

}
