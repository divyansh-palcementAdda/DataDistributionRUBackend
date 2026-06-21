package com.app.datadistribution.security;

import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final IRoleService roleService;

    @Transactional(readOnly = true)
    public UserDetails loadUserById(UUID id) throws UsernameNotFoundException {
        User user = userRepository.findById(id)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> {
                    log.error("User not found with id: {}", id);
                    return new UsernameNotFoundException("User not found with id: " + id);
                });

        log.debug("User found with id: {}", id);
        return buildUserDetails(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> {
                    log.error("User not found with username/email: {}", usernameOrEmail);
                    return new UsernameNotFoundException("User not found with identifier: " + usernameOrEmail);
                });

        log.debug("User found with username/email: {}", usernameOrEmail);
        return buildUserDetails(user);
    }

    private UserDetails buildUserDetails(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> loadedPermissions = new ArrayList<>();
        List<String> loadedRoles = new ArrayList<>();

        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.isActive() && !role.isDeleted()) {
                    String roleName = role.getName().toUpperCase();
                    if (!roleName.startsWith("ROLE_")) {
                        roleName = "ROLE_" + roleName;
                    }
                    authorities.add(new SimpleGrantedAuthority(roleName));
                    loadedRoles.add(roleName);

                    if (role.getPermissions() != null) {
                        for (Permission permission : role.getPermissions()) {
                            if (permission.isActive() && !permission.isDeleted()) {
                                String permissionName = permission.getName().toUpperCase();
                                authorities.add(new SimpleGrantedAuthority(permissionName));
                                loadedPermissions.add(permissionName);
                            }
                        }
                    }
                }
            }
        }

        log.info("SECURITY DEBUG | User: {} | Roles Loaded: {} | Permissions Loaded: {} | Total Authorities: {}",
                user.getUsername(), loadedRoles, loadedPermissions, authorities);

        return new UserDetailsImpl(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                authorities,
                user.isActive(),
                user.isLocked(),
                user.isEmailVerified(),
                user.getTokenVersion()
        );
    }
}
