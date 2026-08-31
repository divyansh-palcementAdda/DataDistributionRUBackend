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
        if (scope == null || scope.getScopeType() == ScopeType.SYSTEM) {
            return true;
        }

        if (scope.getScopeType() == ScopeType.SELF) {
            // COUNSELOR -> STRICTLY lead.assignedTo == currentUser
            return lead.getAssignedTo() != null && lead.getAssignedTo().getId().equals(scope.getUserId());
        }

        if (scope.getScopeType() == ScopeType.DEPARTMENT) {
            boolean isAssignedToHod = lead.getAssignedTo() != null && lead.getAssignedTo().getId().equals(scope.getUserId());
            boolean isDeptLead = lead.getAssignedTo() != null     // HODs must NEVER see unallocated leads
                    && lead.getDepartment() != null
                    && scope.getDepartmentIds() != null
                    && scope.getDepartmentIds().contains(lead.getDepartment().getId());
            boolean isDeptUser = lead.getAssignedTo() != null
                    && scope.getDepartmentUserIds() != null
                    && scope.getDepartmentUserIds().contains(lead.getAssignedTo().getId());
            return isAssignedToHod || isDeptLead || isDeptUser;
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
            boolean isSelfAssigned = lead.getAssignedTo() != null && lead.getAssignedTo().getId().equals(scope.getUserId());
            if (!isSelfAssigned) {
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

        if (scope.getScopeType() == ScopeType.SELF) {
            // COUNSELOR -> STRICTLY assignedTo.id == currentUserId
            // Must NOT see unassigned/not-allotted leads or other counselors' leads
            return cb.and(
                    notDeleted,
                    cb.isNotNull(root.get("assignedTo")),
                    cb.equal(root.get("assignedTo").get("id"), scope.getUserId())
            );
        }

        if (scope.getScopeType() == ScopeType.DEPARTMENT) {
            Predicate selfAssigned = cb.and(
                    cb.isNotNull(root.get("assignedTo")),
                    cb.equal(root.get("assignedTo").get("id"), scope.getUserId())
            );
            if (scope.getDepartmentIds() != null && !scope.getDepartmentIds().isEmpty()) {
                // deptLead: must be assigned (not null) AND belong to HOD's department
                Predicate deptLead = cb.and(
                        cb.isNotNull(root.get("assignedTo")),
                        root.get("department").get("id").in(scope.getDepartmentIds())
                );
                Predicate deptUser = (scope.getDepartmentUserIds() != null && !scope.getDepartmentUserIds().isEmpty())
                        ? root.get("assignedTo").get("id").in(scope.getDepartmentUserIds())
                        : cb.disjunction();
                return cb.and(notDeleted, cb.or(selfAssigned, deptLead, deptUser));
            } else {
                return cb.and(notDeleted, selfAssigned);
            }
        }

        return notDeleted;
    }
}
