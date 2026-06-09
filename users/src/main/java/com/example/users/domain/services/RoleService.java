package com.example.users.domain.services;

import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.users.domain.model.Role;
import com.example.users.domain.model.RoleNames;
import com.example.users.domain.spi.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

	private final RoleRepository roleRepository;

	@Cacheable("defaultRoles")
	public List<Role> getDefaultRoles() {
		Role userRole = roleRepository.findByName(RoleNames.ROLE_USER.name())
				.orElseThrow(() -> new EntityNotFoundException("Default role not found: %s", RoleNames.ROLE_USER));
		return List.of(userRole);
	}
}
