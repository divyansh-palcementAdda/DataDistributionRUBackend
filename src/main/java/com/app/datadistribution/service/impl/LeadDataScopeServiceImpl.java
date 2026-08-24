package com.app.datadistribution.service.impl;

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadDataScopeServiceImpl implements ILeadDataScopeService {

    private final IUserDataScopeService dataScopeService;

    @Override
    public UserDataScope getCurrentUserScope() throws UnauthorizedException, BadRequestException {
        return dataScopeService.getScopeForCurrentUser();
    }

    @Override
    public Specification<Lead> getLeadScopeSpecification(UserDataScope scope) {
        return (root, query, cb) -> {
            query.distinct(true);
            return buildScopePredicate(cb, root, scope);
        };
    }

    @Override
    public boolean isLeadAccessible(Lead lead, UserDataScope scope) {
        if (lead == null || lead.isDeleted()) {
            return false;
        }
        if (scope.getScopeType() == ScopeType.SYSTEM) {
            return true;
        }
        boolean isAssigned = lead.getAssignedTo() != null && lead.getAssignedTo().getId().equals(scope.getUserId());
        boolean isCreator = lead.getCreatedByUser() != null && lead.getCreatedByUser().getId().equals(scope.getUserId());
        boolean isOwn = isAssigned || isCreator;

        if (scope.getScopeType() == ScopeType.SELF) {
            return isOwn;
        }

        if (scope.getScopeType() == ScopeType.DEPARTMENT) {
            boolean isDeptLead = lead.getDepartment() != null
                    && scope.getDepartmentIds() != null
                    && scope.getDepartmentIds().contains(lead.getDepartment().getId());
            boolean isDeptUser = lead.getAssignedTo() != null
                    && scope.getDepartmentUserIds() != null
                    && scope.getDepartmentUserIds().contains(lead.getAssignedTo().getId());
            return isOwn || isDeptLead || isDeptUser;
        }

        return false;
    }

    @Override
    public void validateLeadReadAccess(Lead lead, UserDataScope scope) throws UnauthorizedException {
        if (!isLeadAccessible(lead, scope)) {
            throw new UnauthorizedException("You do not have access to this lead based on your assigned role and data scope.");
        }
    }

    @Override
    public void validateLeadWriteAccess(Lead lead, UserDataScope scope) throws UnauthorizedException, BadRequestException {
        validateLeadReadAccess(lead, scope);
        if (scope.isHod() && !scope.canModifyDepartmentData()) {
            boolean isOwn = (lead.getAssignedTo() != null && lead.getAssignedTo().getId().equals(scope.getUserId()))
                    || (lead.getCreatedByUser() != null && lead.getCreatedByUser().getId().equals(scope.getUserId()));
            if (!isOwn) {
                throw new UnauthorizedException("HOD with VIEW_ONLY access cannot modify department leads.");
            }
        }
    }

    @Override
    public Predicate buildScopePredicate(CriteriaBuilder cb, Root<Lead> root, UserDataScope scope) {
        Predicate notDeleted = cb.equal(root.get("isDeleted"), false);

        if (scope == null || scope.getScopeType() == ScopeType.SYSTEM) {
            return notDeleted;
        }

        Predicate ownData = cb.or(
                cb.equal(root.get("assignedTo").get("id"), scope.getUserId()),
                cb.equal(root.get("createdByUser").get("id"), scope.getUserId())
        );

        if (scope.getScopeType() == ScopeType.SELF) {
            return cb.and(notDeleted, ownData);
        }

        if (scope.getScopeType() == ScopeType.DEPARTMENT) {
            if (scope.getDepartmentIds() != null && !scope.getDepartmentIds().isEmpty()) {
                Predicate deptLead = root.get("department").get("id").in(scope.getDepartmentIds());
                Predicate deptUser = (scope.getDepartmentUserIds() != null && !scope.getDepartmentUserIds().isEmpty())
                        ? root.get("assignedTo").get("id").in(scope.getDepartmentUserIds())
                        : cb.disjunction();
                return cb.and(notDeleted, cb.or(ownData, deptLead, deptUser));
            } else {
                return cb.and(notDeleted, ownData);
            }
        }

        return notDeleted;
    }
}
