package com.example.users.app.dto;

public record CreateUserDto(
	String username,
	String password,
	String email
) {
}
