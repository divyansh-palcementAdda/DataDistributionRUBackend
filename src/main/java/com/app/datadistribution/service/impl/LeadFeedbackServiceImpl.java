package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.LeadFeedbackRequest;
import com.app.datadistribution.dto.lead.LeadFeedbackResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadFeedbackService;
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
public class LeadFeedbackServiceImpl implements ILeadFeedbackService {

    private final LeadFeedbackRepository leadFeedbackRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final LeadMapper leadMapper;

    @Override
    @Transactional
    public LeadFeedbackResponse addFeedback(UUID leadId, LeadFeedbackRequest request) throws UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        User currentUser = getCurrentUserEntity();

        LeadFeedback feedback = LeadFeedback.builder()
                .lead(lead)
                .feedback(request.getFeedback())
                .statusAtTime(lead.getCurrentStatus())
                .createdByUser(currentUser)
                .build();

        LeadFeedback saved = leadFeedbackRepository.save(feedback);
        log.info("Feedback added to lead {} by {}", lead.getLeadCode(), currentUser.getUsername());
        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadFeedbackResponse> getFeedbacksByLeadId(UUID leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw new ResourcesNotFoundException("Lead not found with id: " + leadId);
        }
        return leadFeedbackRepository.findByLeadIdOrderByCreatedAtDesc(leadId).stream()
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
