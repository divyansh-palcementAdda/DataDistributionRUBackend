package com.app.datadistribution.service;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadPageResponse;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadAvailedRepository;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.impl.LeadStatusTransitionServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadFieldSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LatestStatusRemarkTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;

    @Mock
    private LeadAvailedRepository leadAvailedRepository;

    @Mock
    private LeadFeedbackRepository leadFeedbackRepository;

    @Mock
    private LeadMapper leadMapper;

    @Mock
    private ILeadDataScopeService leadDataScopeService;

    @Mock
    private ILeadFieldSecurityService leadFieldSecurityService;

    @InjectMocks
    private LeadServiceImpl leadService;

    private User currentUser;
    private LeadStatus rawStatus;
    private LeadStatus badStatus;
    private Lead lead;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().username("testuser").firstName("Neha").lastName("Saini").build();
        currentUser.setId(UUID.randomUUID());

        rawStatus = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
        rawStatus.setId(UUID.randomUUID());

        badStatus = LeadStatus.builder().name("Bad").code("BAD").active(true).build();
        badStatus.setId(UUID.randomUUID());

        lead = Lead.builder()
                .leadCode("LEAD-TEST-001")
                .fullName("Dipika Sardar")
                .currentStatus(badStatus)
                .remarks("[Meta Lead Ads Integration] Lead ID: 123456789")
                .build();
        lead.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("getAllLeads should replace initial ingestion remark with latest status history remark (e.g. 'not enquiry')")
    void shouldReturnLatestStatusRemarkInGetAllLeads() throws Exception {
        UserDataScope scope = UserDataScope.builder().scopeType(UserDataScope.ScopeType.SYSTEM).build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadDataScopeService.getLeadScopeSpecification(any())).thenReturn((root, q, cb) -> null);

        when(leadRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lead)));

        when(leadAvailedRepository.findByLeadIdInAndIsDeletedFalse(anyList()))
                .thenReturn(Collections.emptyList());

        LeadStatusHistory autoHistory = LeadStatusHistory.builder()
                .lead(lead)
                .newStatus(LeadStatus.builder().name("Connected").build())
                .transitionType("AUTO_PARENT")
                .feedback("Automatically progressed through hierarchy to Connected")
                .build();
        autoHistory.setId(UUID.randomUUID());
        autoHistory.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        LeadStatusHistory targetHistory = LeadStatusHistory.builder()
                .lead(lead)
                .newStatus(badStatus)
                .transitionType("USER")
                .feedback("not enquiry")
                .build();
        targetHistory.setId(UUID.randomUUID());
        targetHistory.setCreatedAt(LocalDateTime.now());

        when(leadStatusHistoryRepository.findByLeadIdInOrderByCreatedAtDescIdDesc(anyCollection()))
                .thenReturn(List.of(targetHistory, autoHistory));

        LeadResponse dto = LeadResponse.builder()
                .id(lead.getId())
                .leadCode(lead.getLeadCode())
                .fullName(lead.getFullName())
                .remarks(lead.getRemarks())
                .build();

        when(leadMapper.toDto(lead)).thenReturn(dto);

        PageRequestDTO pageReq = PageRequestDTO.builder().page(0).size(10).build();
        LeadPageResponse response = leadService.getAllLeads(pageReq, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        LeadResponse resDto = response.getContent().get(0);

        // Verify that the remarks field and latestStatusRemark both show 'not enquiry' instead of Meta Ads dump
        assertEquals("not enquiry", resDto.getLatestStatusRemark());
        assertEquals("not enquiry", resDto.getRemarks());
    }

    @Test
    @DisplayName("getById should resolve latest status history remark")
    void shouldResolveLatestStatusRemarkInGetById() throws Exception {
        UserDataScope scope = UserDataScope.builder().scopeType(UserDataScope.ScopeType.SYSTEM).build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));

        LeadStatusHistory targetHistory = LeadStatusHistory.builder()
                .lead(lead)
                .newStatus(badStatus)
                .transitionType("USER")
                .feedback("not enquiry")
                .build();
        targetHistory.setId(UUID.randomUUID());
        targetHistory.setCreatedAt(LocalDateTime.now());

        when(leadStatusHistoryRepository.findByLeadIdOrderByCreatedAtDescIdDesc(lead.getId()))
                .thenReturn(List.of(targetHistory));

        LeadResponse dto = LeadResponse.builder()
                .id(lead.getId())
                .leadCode(lead.getLeadCode())
                .fullName(lead.getFullName())
                .remarks(lead.getRemarks())
                .build();

        when(leadMapper.toDto(lead)).thenReturn(dto);

        LeadResponse result = leadService.getById(lead.getId());

        assertNotNull(result);
        assertEquals("not enquiry", result.getLatestStatusRemark());
        assertEquals("not enquiry", result.getRemarks());
    }

    @Test
    @DisplayName("Lead with no status histories should gracefully fall back to lead.remarks")
    void shouldFallbackToLeadRemarksWhenNoStatusHistoriesExist() throws Exception {
        UserDataScope scope = UserDataScope.builder().scopeType(UserDataScope.ScopeType.SYSTEM).build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadDataScopeService.getLeadScopeSpecification(any())).thenReturn((root, q, cb) -> null);

        lead.setRemarks("General inquiry note");
        when(leadRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lead)));
        when(leadAvailedRepository.findByLeadIdInAndIsDeletedFalse(anyList()))
                .thenReturn(Collections.emptyList());
        when(leadStatusHistoryRepository.findByLeadIdInOrderByCreatedAtDescIdDesc(anyCollection()))
                .thenReturn(Collections.emptyList());

        LeadResponse dto = LeadResponse.builder()
                .id(lead.getId())
                .leadCode(lead.getLeadCode())
                .fullName(lead.getFullName())
                .remarks(lead.getRemarks())
                .build();

        when(leadMapper.toDto(lead)).thenReturn(dto);

        PageRequestDTO pageReq = PageRequestDTO.builder().page(0).size(10).build();
        LeadPageResponse response = leadService.getAllLeads(pageReq, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertNotNull(response);
        LeadResponse resDto = response.getContent().get(0);
        assertEquals("General inquiry note", resDto.getLatestStatusRemark());
        assertEquals("General inquiry note", resDto.getRemarks());
    }
}
