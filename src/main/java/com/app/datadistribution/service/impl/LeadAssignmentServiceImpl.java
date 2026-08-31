package com.app.datadistribution.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.lead.LeadAssignmentHistoryResponse;
import com.app.datadistribution.dto.lead.LeadAssignmentRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.ILeadAssignmentService;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.util.LeadDepartmentResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadAssignmentServiceImpl implements ILeadAssignmentService {

    private final LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final LeadMapper leadMapper;
    private final ILeadDataScopeService leadDataScopeService;

    @Override
    @Transactional
    public LeadResponse assignLead(UUID leadId, LeadAssignmentRequest request) throws UnauthorizedException, BadRequestException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

        User currentUser = getCurrentUserEntity();
        User oldAssignedUser = lead.getAssignedTo();
        User newAssignedUser = userRepository.findById(request.getAssignedToUserId())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User to assign not found with id: " + request.getAssignedToUserId()));

        if (dataScope.isSelfScope() && !newAssignedUser.getId().equals(dataScope.getUserId())) {
            throw new BadRequestException("Counselors can only assign leads to themselves.");
        }
        if (dataScope.isDepartmentScope() && dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().contains(newAssignedUser.getId())) {
            throw new BadRequestException("HOD can only assign leads to users in their mapped department(s).");
        }

        if (oldAssignedUser != null && oldAssignedUser.getId().equals(newAssignedUser.getId())) {
            return leadMapper.toDto(lead);
        }

        // Update Lead assignment & synchronize Department with new assignee
        lead.setAssignedTo(newAssignedUser);
        lead.setDepartment(LeadDepartmentResolver.resolveDepartmentForUser(newAssignedUser, lead.getDepartment()));
        Lead savedLead = leadRepository.save(lead);

        // Save Assignment History trail
        LeadAssignmentHistory history = LeadAssignmentHistory.builder()
                .lead(savedLead)
                .oldAssignedUser(oldAssignedUser)
                .newAssignedUser(newAssignedUser)
                .changedByUser(currentUser)
                .remarks(request.getRemarks())
                .build();
        leadAssignmentHistoryRepository.save(history);

        log.info("Lead {} reassigned: {} -> {}", lead.getLeadCode(),
                oldAssignedUser != null ? oldAssignedUser.getUsername() : "NONE",
                newAssignedUser.getUsername());

        return leadMapper.toDto(savedLead);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadAssignmentHistoryResponse> getAssignmentHistoryByLeadId(UUID leadId) throws UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        try {
            UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
            leadDataScopeService.validateLeadReadAccess(lead, dataScope);
        } catch (BadRequestException e) {
            throw new UnauthorizedException("Cannot resolve user data scope: " + e.getMessage());
        }

        return leadAssignmentHistoryRepository.findByLeadIdOrderByCreatedAtDesc(leadId).stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    private User getCurrentUserEntity() throws UnauthorizedException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));
    }
}
