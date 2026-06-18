package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.LeadAssignmentRequest;
import com.app.datadistribution.dto.lead.LeadAssignmentHistoryResponse;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadAssignmentService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadAssignmentServiceImpl implements ILeadAssignmentService {

    private final LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final LeadMapper leadMapper;

    @Override
    @Transactional
    public LeadResponse assignLead(UUID leadId, LeadAssignmentRequest request) throws UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        User currentUser = getCurrentUserEntity();
        User oldAssignedUser = lead.getAssignedTo();
        User newAssignedUser = userRepository.findById(request.getAssignedToUserId())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User to assign not found with id: " + request.getAssignedToUserId()));

        if (oldAssignedUser != null && oldAssignedUser.getId().equals(newAssignedUser.getId())) {
            return leadMapper.toDto(lead);
        }

        // Update Lead assignment
        lead.setAssignedTo(newAssignedUser);
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
    public List<LeadAssignmentHistoryResponse> getAssignmentHistoryByLeadId(UUID leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw new ResourcesNotFoundException("Lead not found with id: " + leadId);
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
