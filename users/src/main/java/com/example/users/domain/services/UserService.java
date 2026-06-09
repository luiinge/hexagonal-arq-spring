package com.example.users.domain.services;

import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.users.domain.commands.CreateUserCommand;
import com.example.users.domain.commands.UpdateUserCommand;
import com.example.users.domain.model.Role;
import com.example.users.domain.model.RoleNames;
import com.example.users.domain.model.User;
import com.example.users.domain.spi.RoleRepository;
import com.example.users.domain.spi.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final RoleService roleService;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public User createUser(CreateUserCommand command) {
		if (userRepository.existsByUsername(command.username())) {
			throw new IllegalArgumentException("Username already exists");
		}
		User user = User.builder()
			.username(command.username())
			.password(passwordEncoder.encode(command.password()))
			.email(command.email())
			.active(true)
			.roles(roleService.getDefaultRoles())
			.build();
		return userRepository.save(user);
	}


	@Transactional(readOnly = true)
	public Optional<User> getUserById(Long id) {
		return userRepository.findById(id);
	}


	@Transactional(readOnly = true)
	public List<User> getAllUsers() {
		return userRepository.getAllUsers();
	}

	@Transactional
	public User updateUser(UpdateUserCommand command) {
		checkUserExists(command.id());
		User user = userRepository.findById(command.id()).orElseThrow();
		user.setEmail(command.email());
		user.setActive(command.active());
		return userRepository.save(user);
	}


	@Transactional
	public void deleteUser(Long id) {
		checkUserExists(id);
		userRepository.deleteById(id);
	}


	private void checkUserExists(Long id) {
		if (!userRepository.existsById(id)) {
			throw new EntityNotFoundException("User not found with id: %s",id);
		}
	}



}
