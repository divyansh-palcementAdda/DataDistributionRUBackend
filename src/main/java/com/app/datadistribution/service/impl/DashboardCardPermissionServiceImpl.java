package com.app.datadistribution.service.impl;

import com.app.datadistribution.entity.DashboardCard;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.DashboardCardRepository;
import com.app.datadistribution.repository.PermissionRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.service.interfaces.IDashboardCardPermissionService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardCardPermissionServiceImpl implements IDashboardCardPermissionService {

    private final DashboardCardRepository dashboardCardRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public Permission registerCardAndPermission(DashboardCard card) {
        String permCode = generatePermissionCode(card.getCode());

        Permission permission = permissionRepository.findByName(permCode)
                .orElseGet(() -> {
                    Permission newPerm = Permission.builder()
                            .name(permCode)
                            .description("Card level RBAC permission for dashboard card: " + card.getName())
                            .active(true)
                            .build();
                    Permission saved = permissionRepository.save(newPerm);
                    log.info("Created dynamic card permission: {}", permCode);
                    return saved;
                });

        card.setPermission(permission);

        List<Role> activeRoles = roleRepository.findAllByIsDeletedFalse();
        Set<Role> assignedRoles = new HashSet<>();

        for (Role role : activeRoles) {
            if (role.getPermissions() == null) {
                role.setPermissions(new HashSet<>());
            }
            if (!role.getPermissions().contains(permission)) {
                role.getPermissions().add(permission);
                roleRepository.save(role);
                log.info("Assigned card permission {} to role {}", permCode, role.getName());
            }
            assignedRoles.add(role);
        }

        if (card.getAllowedRoles() == null || card.getAllowedRoles().isEmpty()) {
            card.setAllowedRoles(assignedRoles);
        }

        DashboardCard savedCard = dashboardCardRepository.save(card);
        log.info("Registered dashboard card: {} with permission {}", savedCard.getCode(), permCode);
        return permission;
    }

    @Override
    @Transactional
    public DashboardCard ensureEntityCardAndPermission(String entityType, String code, String name, String section) {
        String normalizedCode = (entityType + "_" + code).toUpperCase().replaceAll("[^A-Z0-9_]", "_");

        return dashboardCardRepository.findByCode(normalizedCode)
                .orElseGet(() -> {
                    DashboardCard card = DashboardCard.builder()
                            .code(normalizedCode)
                            .name(name)
                            .description("Dynamic dashboard card for " + entityType + " " + name)
                            .section(section)
                            .cardType("GROUP_CHART")
                            .icon("analytics")
                            .displayOrder(50)
                            .active(true)
                            .build();
                    registerCardAndPermission(card);
                    return card;
                });
    }

    @Override
    @Transactional
    public void updateCardRolePermissions(UUID cardId, List<UUID> roleIds) {
        DashboardCard card = dashboardCardRepository.findById(cardId)
                .filter(DashboardCard::isActive)
                .orElseThrow(() -> new ResourcesNotFoundException("Dashboard card not found with id: " + cardId));

        Permission permission = card.getPermission();
        if (permission == null) {
            permission = registerCardAndPermission(card);
        }

        List<Role> targetRoles = roleRepository.findAllById(roleIds);
        Set<Role> newAllowedRoles = new HashSet<>(targetRoles);

        roleRepository.findByNameAndIsDeletedFalse(RoleType.SUPER_ADMIN.name())
                .ifPresent(newAllowedRoles::add);

        List<Role> allRoles = roleRepository.findAllByIsDeletedFalse();
        for (Role role : allRoles) {
            if (newAllowedRoles.contains(role)) {
                if (!role.getPermissions().contains(permission)) {
                    role.getPermissions().add(permission);
                    roleRepository.save(role);
                }
            } else {
                if (!RoleType.SUPER_ADMIN.name().equalsIgnoreCase(role.getName())) {
                    if (role.getPermissions().contains(permission)) {
                        role.getPermissions().remove(permission);
                        roleRepository.save(role);
                    }
                }
            }
        }

        card.setAllowedRoles(newAllowedRoles);
        dashboardCardRepository.save(card);
        log.info("Updated card role permissions for card {} to {} roles", card.getCode(), newAllowedRoles.size());
    }

    @Override
    @Transactional
    public void ensureSuperAdminHasAllCardPermissions() {
        roleRepository.findByNameAndIsDeletedFalse(RoleType.SUPER_ADMIN.name()).ifPresent(superAdminRole -> {
            List<Permission> cardPermissions = permissionRepository.findAllByIsDeletedFalse().stream()
                    .filter(p -> p.getName().startsWith("DASHBOARD_CARD_"))
                    .toList();

            if (superAdminRole.getPermissions() == null) {
                superAdminRole.setPermissions(new HashSet<>());
            }

            boolean updated = false;
            for (Permission p : cardPermissions) {
                if (!superAdminRole.getPermissions().contains(p)) {
                    superAdminRole.getPermissions().add(p);
                    updated = true;
                }
            }

            if (updated) {
                roleRepository.save(superAdminRole);
                log.info("Assigned all active DASHBOARD_CARD_* permissions to SUPER_ADMIN role");
            }
        });
    }

    private String generatePermissionCode(String cardCode) {
        if (cardCode == null || cardCode.isBlank()) {
            return "DASHBOARD_CARD_UNKNOWN";
        }
        String normalized = cardCode.toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (normalized.startsWith("DASHBOARD_CARD_")) {
            return normalized;
        }
        return "DASHBOARD_CARD_" + normalized;
    }
}
