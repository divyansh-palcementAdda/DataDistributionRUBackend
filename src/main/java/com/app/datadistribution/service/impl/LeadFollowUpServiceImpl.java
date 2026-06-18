package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadFollowUpService;
import java.time.LocalDateTime;
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
public class LeadFollowUpServiceImpl implements ILeadFollowUpService {

    private final LeadFollowUpRepository leadFollowUpRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final LeadMapper leadMapper;

    @Override
    @Transactional
    public LeadFollowUpResponse createFollowUp(UUID leadId, LeadFollowUpRequest request) throws UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        User currentUser = getCurrentUserEntity();

        LeadFollowUp followUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(request.getFollowUpDate())
                .remarks(request.getRemarks())
                .status(FollowUpStatus.PENDING)
                .completed(false)
                .createdByUser(currentUser)
                .build();

        lead.setNextFollowUpDate(request.getFollowUpDate());
        leadRepository.save(lead);

        LeadFollowUp saved = leadFollowUpRepository.save(followUp);
        log.info("Follow-up scheduled for lead {} on {}", lead.getLeadCode(), saved.getFollowUpDate());
        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadFollowUpResponse> getFollowUpsByLeadId(UUID leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw new ResourcesNotFoundException("Lead not found with id: " + leadId);
        }
        return leadFollowUpRepository.findByLeadIdOrderByFollowUpDateDesc(leadId).stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LeadFollowUpResponse completeFollowUp(UUID followUpId, String remarks) {
        LeadFollowUp followUp = leadFollowUpRepository.findById(followUpId)
                .orElseThrow(() -> new ResourcesNotFoundException("Follow-up not found with id: " + followUpId));

        followUp.setCompleted(true);
        followUp.setCompletedAt(LocalDateTime.now());
        followUp.setStatus(FollowUpStatus.COMPLETED);
        if (remarks != null && !remarks.isBlank()) {
            followUp.setRemarks(remarks);
        }

        LeadFollowUp saved = leadFollowUpRepository.save(followUp);
        log.info("Follow-up {} marked completed", followUpId);
        return leadMapper.toDto(saved);
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
