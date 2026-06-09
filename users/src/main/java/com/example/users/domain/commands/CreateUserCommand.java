package com.example.users.domain.commands;

public record CreateUserCommand (
	String username,
	String password,
	String email
){
}
