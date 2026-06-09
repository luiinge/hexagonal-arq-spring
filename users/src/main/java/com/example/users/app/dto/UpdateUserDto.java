package com.example.users.app.dto;

public record UpdateUserDto(
	String email,
	boolean active
) {
}
