package com.example.users.infra.persistence.mapper;

import com.example.users.domain.model.Role;
import com.example.users.infra.persistence.entities.RoleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleEntityMapper {

    Role toDomain(RoleEntity entity);
    RoleEntity toEntity(Role role);

}