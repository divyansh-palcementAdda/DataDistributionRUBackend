package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.entity.DashboardCard;
import com.app.datadistribution.entity.Permission;
import java.util.List;
import java.util.UUID;

public interface IDashboardCardPermissionService {
    Permission registerCardAndPermission(DashboardCard card);
    DashboardCard ensureEntityCardAndPermission(String entityType, String code, String name, String section);
    void updateCardRolePermissions(UUID cardId, List<UUID> roleIds);
    void ensureSuperAdminHasAllCardPermissions();
}
