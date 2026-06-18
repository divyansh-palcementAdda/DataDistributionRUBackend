package com.app.datadistribution.config;

import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.repository.PermissionRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

	private final PermissionRepository permissionRepository;
	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(String... args) {
		log.info("Database seeding started...");
		seedPermissions();
		seedRoles();
		seedUsers();
		log.info("Database seeding completed successfully!");
	}

	private void seedPermissions() {
		for (PermissionType type : PermissionType.values()) {
			String name = type.name();
			if (!permissionRepository.findByName(name).isPresent()) {
				Permission permission = Permission.builder().name(name).description("Dynamic permission for " + name)
						.build();
				permissionRepository.save(permission);
				log.info("Seeded permission: {}", name);
			}
		}
	}

	private void seedRoles() {
		Set<Permission> allPermissions = new HashSet<>(permissionRepository.findAll());

		// 1. SUPER_ADMIN Role
		createRoleIfNotExist(RoleType.SUPER_ADMIN.name(), "Super Administrator Role", allPermissions);

		// 2. ADMIN Role
		Set<Permission> adminPermissions = allPermissions.stream()
				.filter(p -> p.getName().equals(PermissionType.USER_CREATE.name())
						|| p.getName().equals(PermissionType.USER_READ.name())
						|| p.getName().equals(PermissionType.USER_UPDATE.name())
						|| p.getName().equals(PermissionType.AUTH_READ.name()))
				.collect(Collectors.toSet());
		createRoleIfNotExist(RoleType.ADMIN.name(), "Administrator Role", adminPermissions);

		// 3. USER Role
		Set<Permission> userPermissions = allPermissions.stream()
				.filter(p -> p.getName().equals(PermissionType.USER_READ.name())).collect(Collectors.toSet());
		createRoleIfNotExist(RoleType.USER.name(), "Standard User Role", userPermissions);
	}

	private void createRoleIfNotExist(String name, String description, Set<Permission> permissions) {
		if (!roleRepository.findByName(name).isPresent()) {
			Role role = Role.builder().name(name).description(description).active(true).permissions(permissions)
					.build();
			roleRepository.save(role);
			log.info("Seeded role: {}", name);
		} else {
			// Update permissions to match code config
			Role role = roleRepository.findByName(name).get();
			role.setPermissions(permissions);
			roleRepository.save(role);
		}
	}

	private void seedUsers() {
		// Seed default Super Admin
		if (!userRepository.findByUsername("superadmin").isPresent()) {
			Role superAdminRole = roleRepository.findByName(RoleType.SUPER_ADMIN.name())
					.orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not seeded"));

			User superAdmin = User.builder().firstName("Super").lastName("Admin")
					.email("superadmin@datadistribution.com").phone("+919999999999").username("superadmin")
					.password(passwordEncoder.encode("Admin@123")).active(true).locked(false).emailVerified(true)

					.roles(new HashSet<>(Arrays.asList(superAdminRole))).build();
			userRepository.save(superAdmin);
			log.info("Seeded default superadmin user");
		}

		// Seed default Admin
		if (!userRepository.findByUsername("admin").isPresent()) {
			Role adminRole = roleRepository.findByName(RoleType.ADMIN.name())
					.orElseThrow(() -> new RuntimeException("ADMIN role not seeded"));

			User admin = User.builder().firstName("Standard").lastName("Admin").email("admin@datadistribution.com")
					.phone("+918888888888").username("admin").password(passwordEncoder.encode("Admin@123")).active(true)
					.locked(false).emailVerified(true)

					.roles(new HashSet<>(Arrays.asList(adminRole))).build();
			userRepository.save(admin);
			log.info("Seeded default admin user");
		}

		// Seed default User
		if (!userRepository.findByUsername("user").isPresent()) {
			Role userRole = roleRepository.findByName(RoleType.USER.name())
					.orElseThrow(() -> new RuntimeException("USER role not seeded"));

			User user = User.builder().firstName("Standard").lastName("User").email("user@datadistribution.com")
					.phone("+917777777777").username("user").password(passwordEncoder.encode("User@123")).active(true)
					.locked(false).emailVerified(true)

					.roles(new HashSet<>(Arrays.asList(userRole))).build();
			userRepository.save(user);
			log.info("Seeded default standard user");
		}
	}
}
