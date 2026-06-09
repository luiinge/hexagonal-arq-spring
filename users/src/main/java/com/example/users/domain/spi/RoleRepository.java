package com.example.users.domain.spi;

import com.example.users.domain.model.Role;
import java.util.Optional;

public interface RoleRepository {

	Optional<Role> findByName(String name);

}
