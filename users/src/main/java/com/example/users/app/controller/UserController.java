package com.example.users.app.controller;

import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.users.app.dto.CreateUserDto;
import com.example.users.app.dto.UpdateUserDto;
import com.example.users.app.dto.UserDto;
import com.example.users.app.mapper.UserDtoMapper;
import com.example.users.domain.commands.CreateUserCommand;
import com.example.users.domain.commands.UpdateUserCommand;
import com.example.users.domain.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

	private final UserService userService;
	private final UserDtoMapper userDtoMapper;

	@PostMapping
	@ResponseStatus(code = org.springframework.http.HttpStatus.CREATED)
	public UserDto createUser(@RequestBody CreateUserDto createUserDto) {
		var command = new CreateUserCommand(
			createUserDto.username(),
			createUserDto.password(),
			createUserDto.email()
		);
		return userDtoMapper.toDto(userService.createUser(command));
	}

	@GetMapping("/{id}")
	public UserDto getUserById(@PathVariable Long id) {
		return userService.getUserById(id)
			.map(userDtoMapper::toDto)
			.orElseThrow(() -> new EntityNotFoundException("User not found", id));
	}

	@GetMapping
	public List<UserDto> getAllUsers() {
		var users = userService.getAllUsers();
		return users.stream().map(userDtoMapper::toDto).toList();
	}

	@PutMapping("/{id}")
	public UserDto updateUser(@PathVariable Long id, @RequestBody UpdateUserDto updateUserDto) {
		var command = new UpdateUserCommand(id, updateUserDto.email(), updateUserDto.active());
		return userDtoMapper.toDto(userService.updateUser(command));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(code = org.springframework.http.HttpStatus.NO_CONTENT)
	public void deleteUser(@PathVariable Long id) {
		userService.deleteUser(id);
	}

}