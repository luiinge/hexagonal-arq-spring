package com.example.users.infra.persistence.mapper;

import com.example.users.domain.model.User;
import com.example.users.infra.persistence.entities.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = RoleEntityMapper.class)
public interface UserEntityMapper {

	User toDomain(UserEntity entity);
	UserEntity toEntity(User user);

}
