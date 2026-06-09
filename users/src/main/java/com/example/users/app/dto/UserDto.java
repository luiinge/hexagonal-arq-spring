package com.example.users.app.dto;

public record UserDto(
	Long id,
	String username,
	boolean active,
	String email
) {
}
