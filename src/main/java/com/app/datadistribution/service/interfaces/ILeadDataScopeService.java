package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.dto.UserDataScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

public interface ILeadDataScopeService {

    UserDataScope getCurrentUserScope() throws UnauthorizedException, BadRequestException;

    Specification<Lead> getLeadScopeSpecification(UserDataScope scope);

    boolean isLeadAccessible(Lead lead, UserDataScope scope);

    void validateLeadReadAccess(Lead lead, UserDataScope scope) throws UnauthorizedException;

    void validateLeadWriteAccess(Lead lead, UserDataScope scope) throws UnauthorizedException, BadRequestException;

    Predicate buildScopePredicate(CriteriaBuilder cb, Root<Lead> root, UserDataScope scope);
}
