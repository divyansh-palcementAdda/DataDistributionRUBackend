package com.app.datadistribution.service.dto;

import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.HodAccessType;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDataScope {

    public enum ScopeType {
        SYSTEM,
        DEPARTMENT,
        SELF
    }

    private ScopeType scopeType;
    private UUID userId;
    private User currentUser;

    @Builder.Default
    private Set<UUID> departmentIds = new HashSet<>();

    @Builder.Default
    private Set<UUID> departmentUserIds = new HashSet<>();

    private HodAccessType hodAccessType;
    private boolean isAdmin;
    private boolean isHod;
    private boolean isCounsellor;

    private ScopeType requestedScopeType;
    private boolean isSelfScopeOnly;

    public boolean isSystemScope() {
        return scopeType == ScopeType.SYSTEM;
    }

    public boolean isDepartmentScope() {
        return scopeType == ScopeType.DEPARTMENT;
    }

    public boolean isSelfScope() {
        return scopeType == ScopeType.SELF;
    }

    public boolean canModifyDepartmentData() {
        if (isAdmin) return true;
        if (isHod) return hodAccessType != HodAccessType.VIEW_ONLY;
        return false;
    }
}
