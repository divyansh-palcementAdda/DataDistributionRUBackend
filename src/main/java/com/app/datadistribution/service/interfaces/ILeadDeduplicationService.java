package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.lead.LeadDuplicateDiagnosticDTO;
import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public interface ILeadDeduplicationService {

    String normalizeName(String name);

    String normalizePhone(String phone);

    String normalizeEmail(String email);

    LeadMatchResult matchLead(
            String name,
            String phone,
            String email,
            LeadSource incomingSource,
            Collection<Course> incomingCourses,
            Collection<Program> incomingPrograms
    );

    Lead enrichAndSaveMatchedLead(
            Lead existingLead,
            LeadRequest request,
            LeadMatchResult matchResult,
            User actingUser,
            String sourceAuditNote
    );

    <T> T executeWithPhoneLock(String normalizedPhone, Supplier<T> action);

    List<LeadDuplicateDiagnosticDTO> generateDuplicateDiagnostics();
}

