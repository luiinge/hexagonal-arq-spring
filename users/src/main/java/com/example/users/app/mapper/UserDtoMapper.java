package com.example.users.app.mapper;

import com.example.users.app.dto.UserDto;
import com.example.users.domain.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserDtoMapper {

	UserDto toDto(User user);
	User toDomain(UserDto userDto);

}
