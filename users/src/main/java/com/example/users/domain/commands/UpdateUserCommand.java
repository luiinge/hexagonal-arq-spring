package com.example.users.domain.commands;

public record UpdateUserCommand(
	Long id,
	String email,
	boolean active
){
}
