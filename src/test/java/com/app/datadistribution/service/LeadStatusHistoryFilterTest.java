package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadPageResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.LeadAvailedRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadStatusHistoryFilterTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private LeadAvailedRepository leadAvailedRepository;
    @Mock
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private LeadServiceImpl leadService;

    private LeadStatus statusRaw;
    private LeadStatus statusConnected;
    private LeadStatus statusInterested;
    private LeadStatus statusRegistered;
    private LeadStatus statusNotInterested;
    private LeadStatus statusSpecialReview;

    private User counselorA;
    private Department deptA;

    @BeforeEach
    void setUp() throws Exception {
        deptA = Department.builder().name("Admissions").code("ADM").active(true).build();
        deptA.setId(UUID.randomUUID());

        counselorA = User.builder().username("counselorA").departments(new HashSet<>(Set.of(deptA))).active(true).build();
        counselorA.setId(UUID.randomUUID());

        statusRaw = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
        statusRaw.setId(UUID.randomUUID());

        statusConnected = LeadStatus.builder().name("Connected").code("CONNECTED").active(true).build();
        statusConnected.setId(UUID.randomUUID());

        statusInterested = LeadStatus.builder().name("Interested").code("INTERESTED").active(true).build();
        statusInterested.setId(UUID.randomUUID());

        statusRegistered = LeadStatus.builder().name("Registered").code("REGISTERED").active(true).build();
        statusRegistered.setId(UUID.randomUUID());

        statusNotInterested = LeadStatus.builder().name("Not Interested").code("NOT_INTERESTED").active(true).build();
        statusNotInterested.setId(UUID.randomUUID());

        statusSpecialReview = LeadStatus.builder().name("Special Review").code("SPECIAL_REVIEW").active(true).build();
        statusSpecialReview.setId(UUID.randomUUID());

        when(leadStatusRepository.findByCodeIgnoreCase("RAW")).thenReturn(Optional.of(statusRaw));
        when(leadStatusRepository.findByCodeIgnoreCase("CONNECTED")).thenReturn(Optional.of(statusConnected));
        when(leadStatusRepository.findByCodeIgnoreCase("INTERESTED")).thenReturn(Optional.of(statusInterested));
        when(leadStatusRepository.findByCodeIgnoreCase("REGISTERED")).thenReturn(Optional.of(statusRegistered));
        when(leadStatusRepository.findByCodeIgnoreCase("NOT_INTERESTED")).thenReturn(Optional.of(statusNotInterested));
        when(leadStatusRepository.findByCodeIgnoreCase("SPECIAL_REVIEW")).thenReturn(Optional.of(statusSpecialReview));

        UserDataScope scope = UserDataScope.builder()
                .scopeType(ScopeType.SYSTEM)
                .isAdmin(true)
                .userId(counselorA.getId())
                .build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
        when(leadDataScopeService.getLeadScopeSpecification(any(UserDataScope.class)))
                .thenReturn((root, query, cb) -> cb.conjunction());

        when(leadAvailedRepository.findByLeadIdInAndIsDeletedFalse(anyList())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Test 1: Status History filter resolves string code to status ID and builds specification")
    void test1_StatusHistoryFilter_ResolvesCodeAndBuildsSpec() throws Exception {
        Lead lead1 = Lead.builder().leadCode("LEAD-001").currentStatus(statusRegistered).build();
        lead1.setId(UUID.randomUUID());

        Page<Lead> page = new PageImpl<>(List.of(lead1));
        when(leadRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();

        LeadPageResponse response = leadService.getAllLeads(
                pageRequest,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, "INTERESTED"
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        verify(leadStatusRepository).findByCodeIgnoreCase("INTERESTED");
        verify(leadRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Test 2: Multi-status history filter (INTERESTED,REGISTERED) resolves both status IDs")
    void test2_MultiStatusHistoryFilter_ResolvesBoth() throws Exception {
        Lead lead1 = Lead.builder().leadCode("LEAD-001").currentStatus(statusRegistered).build();
        lead1.setId(UUID.randomUUID());

        Page<Lead> page = new PageImpl<>(List.of(lead1));
        when(leadRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();

        LeadPageResponse response = leadService.getAllLeads(
                pageRequest,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, "INTERESTED,REGISTERED"
        );

        assertNotNull(response);
        verify(leadStatusRepository).findByCodeIgnoreCase("INTERESTED");
        verify(leadStatusRepository).findByCodeIgnoreCase("REGISTERED");
    }

    @Test
    @DisplayName("Test 3: Dynamic status filter (SPECIAL_REVIEW) works dynamically without hardcoding")
    void test3_DynamicStatusFilter_WorksWithoutHardcoding() throws Exception {
        Lead lead3 = Lead.builder().leadCode("LEAD-003").currentStatus(statusSpecialReview).build();
        lead3.setId(UUID.randomUUID());

        Page<Lead> page = new PageImpl<>(List.of(lead3));
        when(leadRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();

        LeadPageResponse response = leadService.getAllLeads(
                pageRequest,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, "SPECIAL_REVIEW"
        );

        assertNotNull(response);
        verify(leadStatusRepository).findByCodeIgnoreCase("SPECIAL_REVIEW");
    }

    @Test
    @DisplayName("Test 4: Status History filter with UUID parameters directly applies to specification")
    void test4_StatusHistoryFilter_WithDirectUUIDs() throws Exception {
        Lead lead1 = Lead.builder().leadCode("LEAD-001").currentStatus(statusRegistered).build();
        lead1.setId(UUID.randomUUID());

        Page<Lead> page = new PageImpl<>(List.of(lead1));
        when(leadRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageRequestDTO pageRequest = PageRequestDTO.builder().page(0).size(10).build();

        LeadPageResponse response = leadService.getAllLeads(
                pageRequest,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                statusInterested.getId(), null, null
        );

        assertNotNull(response);
        verify(leadRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
