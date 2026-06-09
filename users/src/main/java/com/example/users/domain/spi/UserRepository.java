package com.example.users.domain.spi;

import java.util.List;
import java.util.Optional;

import com.example.users.domain.model.User;

public interface UserRepository {

    Optional<User> findByUsername(String username);

	boolean existsById(Long id);

	void deleteById(Long id);

	Optional<User> findById(Long id);

	List<User> getAllUsers();

	User save(User user);

	boolean existsByUsername(String username);
}