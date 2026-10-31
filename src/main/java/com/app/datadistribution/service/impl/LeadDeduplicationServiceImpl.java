package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.LeadCandidateSummaryDTO;
import com.app.datadistribution.dto.lead.LeadDuplicateDiagnosticDTO;
import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.service.interfaces.ILeadDeduplicationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadDeduplicationServiceImpl implements ILeadDeduplicationService {

    private final LeadRepository leadRepository;
    private final LeadStatusHistoryRepository leadStatusHistoryRepository;

    private final ConcurrentHashMap<String, Object> phoneLocks = new ConcurrentHashMap<>();

    @Override
    public String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Override
    public String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return digits.substring(1);
        }
        if (digits.length() > 10) {
            return digits.substring(digits.length() - 10);
        }
        return digits;
    }

    @Override
    public String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadMatchResult matchLead(
            String name,
            String phone,
            String email,
            LeadSource incomingSource,
            Collection<Course> incomingCourses,
            Collection<Program> incomingPrograms
    ) {
        String normName = normalizeName(name);
        String normPhone = normalizePhone(phone);
        String normEmail = normalizeEmail(email);

        if (normPhone.isBlank() || normName.isBlank()) {
            return LeadMatchResult.noMatch("Name and mobile number are required for lead identity matching");
        }

        String phoneSuffix = normPhone.length() >= 7 ? normPhone : "";
        List<Lead> candidates = leadRepository.findCandidateLeadsByPhone(
                phone != null ? phone.trim() : "",
                phoneSuffix
        );

        if (candidates == null || candidates.isEmpty()) {
            return LeadMatchResult.noMatch("No active leads found for normalized phone: " + normPhone);
        }

        // 1. Filter candidates by normalized phone match (including alternatePhoneNumber)
        List<Lead> phoneMatches = candidates.stream()
                .filter(c -> !c.isDeleted())
                .filter(c -> {
                    String p1 = normalizePhone(c.getPhoneNumber());
                    String p2 = normalizePhone(c.getAlternatePhoneNumber());
                    return normPhone.equals(p1) || (!normPhone.isBlank() && normPhone.equals(p2));
                })
                .toList();

        if (phoneMatches.isEmpty()) {
            return LeadMatchResult.noMatch("No active leads matched phone digits: " + normPhone);
        }

        // 2. Rule D: Check normalized name matching
        List<Lead> nameMatches = phoneMatches.stream()
                .filter(c -> normalizeName(c.getFullName()).equals(normName))
                .toList();

        if (nameMatches.isEmpty()) {
            // Rule D: Same mobile but different names -> Separate leads, DO NOT MERGE
            log.info("Rule D applied: Mobile {} matched, but name '{}' differs from existing lead '{}'. Separate lead required.",
                    normPhone, name, phoneMatches.get(0).getFullName());
            return LeadMatchResult.noMatch("Rule D: Matching mobile found but names differ");
        }

        // 3. Rule B & Rule C: Evaluate Email compatibility
        List<Lead> compatibleMatches = nameMatches.stream()
                .filter(c -> {
                    String candEmail = normalizeEmail(c.getEmail());
                    if (!normEmail.isEmpty() && !candEmail.isEmpty()) {
                        // Both non-empty: must match exactly (Rule A/B)
                        return normEmail.equals(candEmail);
                    }
                    // One or both missing: compatible (Rule C)
                    return true;
                })
                .toList();

        if (compatibleMatches.isEmpty()) {
            // Rule B: Same name, mobile and source, but different non-empty emails -> Separate leads
            log.info("Rule B applied: Name '{}' and mobile {} matched, but non-empty email '{}' differs from existing record(s). Separate lead required.",
                    name, normPhone, email);
            return LeadMatchResult.noMatch("Rule B: Matching name and mobile found, but non-empty emails differ");
        }

        // 4. Handle multiple historical matches (Ambiguity detection)
        boolean isAmbiguous = compatibleMatches.size() > 1;
        Lead selectedLead;
        if (isAmbiguous) {
            // Select most recently updated/created active lead safely without merging records arbitrarily
            selectedLead = compatibleMatches.stream()
                    .max(Comparator.comparing(
                            (Lead l) -> l.getUpdatedAt() != null ? l.getUpdatedAt() : l.getCreatedAt(),
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .orElse(compatibleMatches.get(0));

            List<String> matchedCodes = compatibleMatches.stream().map(Lead::getLeadCode).toList();
            log.warn("AMBIGUOUS_LEAD_MATCH: Detected {} historical matching leads {} for phone={}, name={}. Reusing most recent lead {} ({}) without arbitrary consolidation.",
                    compatibleMatches.size(), matchedCodes, normPhone, normName, selectedLead.getId(), selectedLead.getLeadCode());
        } else {
            selectedLead = compatibleMatches.get(0);
        }

        // 5. Evaluate Safe Enrichment: Email (Rule C)
        boolean emailEnriched = false;
        String emailToEnrich = null;
        if (normalizeEmail(selectedLead.getEmail()).isEmpty() && !normEmail.isEmpty()) {
            emailEnriched = true;
            emailToEnrich = email.trim();
        }

        // 6. Evaluate Course Preservation & Association (Rule F)
        Set<Course> coursesToAdd = new HashSet<>();
        if (incomingCourses != null) {
            Set<UUID> existingCourseIds = selectedLead.getInterestedCourses() != null
                    ? selectedLead.getInterestedCourses().stream()
                            .filter(Objects::nonNull)
                            .map(Course::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet())
                    : new HashSet<>();

            if (selectedLead.getCourse() != null && selectedLead.getCourse().getId() != null) {
                existingCourseIds.add(selectedLead.getCourse().getId());
            }

            for (Course incCourse : incomingCourses) {
                if (incCourse != null && incCourse.getId() != null && !existingCourseIds.contains(incCourse.getId())) {
                    coursesToAdd.add(incCourse);
                }
            }
        }

        // 7. Evaluate Lead Source Preservation & Association (Rule E)
        Set<LeadSource> sourcesToAdd = new HashSet<>();
        if (incomingSource != null && incomingSource.getId() != null) {
            Set<UUID> existingSourceIds = selectedLead.getLeadSources() != null
                    ? selectedLead.getLeadSources().stream()
                            .filter(Objects::nonNull)
                            .map(LeadSource::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet())
                    : new HashSet<>();

            if (!existingSourceIds.contains(incomingSource.getId())) {
                sourcesToAdd.add(incomingSource);
            }
        }

        // 8. Evaluate Program Preservation & Association
        Set<Program> programsToAdd = new HashSet<>();
        if (incomingPrograms != null) {
            Set<UUID> existingProgramIds = selectedLead.getPrograms() != null
                    ? selectedLead.getPrograms().stream()
                            .filter(Objects::nonNull)
                            .map(Program::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet())
                    : new HashSet<>();

            if (selectedLead.getProgram() != null && selectedLead.getProgram().getId() != null) {
                existingProgramIds.add(selectedLead.getProgram().getId());
            }

            for (Program incProg : incomingPrograms) {
                if (incProg != null && incProg.getId() != null && !existingProgramIds.contains(incProg.getId())) {
                    programsToAdd.add(incProg);
                }
            }
        }

        LeadMatchResult.MatchStatus matchStatus;
        if (isAmbiguous) {
            matchStatus = LeadMatchResult.MatchStatus.AMBIGUOUS_MATCH;
        } else if (emailEnriched || !coursesToAdd.isEmpty() || !sourcesToAdd.isEmpty() || !programsToAdd.isEmpty()) {
            matchStatus = LeadMatchResult.MatchStatus.ENRICHED_MATCH;
        } else {
            matchStatus = LeadMatchResult.MatchStatus.EXACT_MATCH;
        }

        String reason = String.format("Matched existing lead %s (%s). Email enriched: %b, New courses: %d, New sources: %d",
                selectedLead.getLeadCode(), matchStatus, emailEnriched, coursesToAdd.size(), sourcesToAdd.size());

        return LeadMatchResult.builder()
                .status(matchStatus)
                .matchedLead(selectedLead)
                .emailEnriched(emailEnriched)
                .emailToEnrich(emailToEnrich)
                .ambiguous(isAmbiguous)
                .matchReason(reason)
                .newCoursesToAdd(coursesToAdd)
                .newSourcesToAdd(sourcesToAdd)
                .newProgramsToAdd(programsToAdd)
                .build();
    }

    @Override
    @Transactional
    public Lead enrichAndSaveMatchedLead(
            Lead lead,
            LeadRequest request,
            LeadMatchResult matchResult,
            User actingUser,
            String sourceAuditNote
    ) {
        if (lead == null || matchResult == null) {
            return lead;
        }

        // Safe email enrichment: never overwrite existing non-empty email with empty
        if (matchResult.isEmailEnriched() && matchResult.getEmailToEnrich() != null) {
            if (lead.getEmail() == null || lead.getEmail().isBlank()) {
                lead.setEmail(matchResult.getEmailToEnrich());
                log.info("Enriched missing email for lead {}: {}", lead.getLeadCode(), matchResult.getEmailToEnrich());
            }
        }

        // Add additional sources idempotently
        if (matchResult.getNewSourcesToAdd() != null && !matchResult.getNewSourcesToAdd().isEmpty()) {
            if (lead.getLeadSources() == null) {
                lead.setLeadSources(new HashSet<>());
            }
            for (LeadSource src : matchResult.getNewSourcesToAdd()) {
                boolean exists = lead.getLeadSources().stream()
                        .anyMatch(s -> s.getId() != null && s.getId().equals(src.getId()));
                if (!exists) {
                    lead.getLeadSources().add(src);
                    log.info("Associated additional source '{}' with lead {}", src.getName(), lead.getLeadCode());
                }
            }
        }

        // Add additional interested courses idempotently
        if (matchResult.getNewCoursesToAdd() != null && !matchResult.getNewCoursesToAdd().isEmpty()) {
            if (lead.getInterestedCourses() == null) {
                lead.setInterestedCourses(new HashSet<>());
            }
            for (Course course : matchResult.getNewCoursesToAdd()) {
                boolean exists = lead.getInterestedCourses().stream()
                        .anyMatch(c -> c.getId() != null && c.getId().equals(course.getId()));
                if (!exists) {
                    lead.getInterestedCourses().add(course);
                    log.info("Associated additional course '{}' with lead {}", course.getCourseName(), lead.getLeadCode());
                }
            }
            if (lead.getCourse() == null && !lead.getInterestedCourses().isEmpty()) {
                lead.setCourse(lead.getInterestedCourses().iterator().next());
            }
        }

        // Add additional programs idempotently
        if (matchResult.getNewProgramsToAdd() != null && !matchResult.getNewProgramsToAdd().isEmpty()) {
            if (lead.getPrograms() == null) {
                lead.setPrograms(new HashSet<>());
            }
            for (Program prog : matchResult.getNewProgramsToAdd()) {
                boolean exists = lead.getPrograms().stream()
                        .anyMatch(p -> p.getId() != null && p.getId().equals(prog.getId()));
                if (!exists) {
                    lead.getPrograms().add(prog);
                    log.info("Associated additional program '{}' with lead {}", prog.getName(), lead.getLeadCode());
                }
            }
            if (lead.getProgram() == null && !lead.getPrograms().isEmpty()) {
                lead.setProgram(lead.getPrograms().iterator().next());
            }
        }

        // Safe location enrichment
        if ((lead.getCity() == null || lead.getCity().isBlank()) && request.getCity() != null && !request.getCity().isBlank()) {
            lead.setCity(request.getCity().trim());
        }
        if ((lead.getState() == null || lead.getState().isBlank()) && request.getState() != null && !request.getState().isBlank()) {
            lead.setState(request.getState().trim());
        }
        if ((lead.getCountry() == null || lead.getCountry().isBlank()) && request.getCountry() != null && !request.getCountry().isBlank()) {
            lead.setCountry(request.getCountry().trim());
        }
        if ((lead.getPreferredStudyState() == null || lead.getPreferredStudyState().isBlank())
                && request.getPreferredStudyState() != null && !request.getPreferredStudyState().isBlank()) {
            lead.setPreferredStudyState(request.getPreferredStudyState().trim());
        }
        if ((lead.getPreferredStudyCity() == null || lead.getPreferredStudyCity().isBlank())
                && request.getPreferredStudyCity() != null && !request.getPreferredStudyCity().isBlank()) {
            lead.setPreferredStudyCity(request.getPreferredStudyCity().trim());
        }

        // Visit planning enrichment
        if (Boolean.TRUE.equals(request.getPlanningToVisitUniversity())) {
            lead.setPlanningToVisitUniversity(true);
            if (request.getVisitDate() != null) {
                lead.setVisitDate(request.getVisitDate());
            }
            if (request.getVisitTime() != null) {
                lead.setVisitTime(request.getVisitTime());
            }
            if (request.getVisitRemarks() != null && !request.getVisitRemarks().isBlank()) {
                lead.setVisitRemarks(request.getVisitRemarks().trim());
            }
        }

        // Source details preservation / append
        if (request.getSourceDetails() != null && !request.getSourceDetails().isBlank()) {
            String incomingDetails = request.getSourceDetails().trim();
            if (lead.getSourceDetails() == null || lead.getSourceDetails().isBlank()) {
                lead.setSourceDetails(incomingDetails.length() > 255 ? incomingDetails.substring(0, 255) : incomingDetails);
            } else if (!lead.getSourceDetails().contains(incomingDetails)) {
                String merged = lead.getSourceDetails() + " | " + incomingDetails;
                lead.setSourceDetails(merged.length() > 255 ? merged.substring(0, 255) : merged);
            }
        }

        // Audit Trail: Record matched enquiry in LeadStatusHistory
        StringBuilder feedback = new StringBuilder();
        feedback.append(sourceAuditNote != null ? sourceAuditNote : "Inbound enquiry matched existing lead record.");
        feedback.append(" [Reused Lead Code: ").append(lead.getLeadCode()).append("]");
        if (matchResult.isEmailEnriched()) {
            feedback.append(" [Email enriched: ").append(lead.getEmail()).append("]");
        }
        if (matchResult.getNewSourcesToAdd() != null && !matchResult.getNewSourcesToAdd().isEmpty()) {
            feedback.append(" [Sources added: ")
                    .append(matchResult.getNewSourcesToAdd().stream().map(LeadSource::getName).collect(Collectors.joining(", ")))
                    .append("]");
        }
        if (matchResult.getNewCoursesToAdd() != null && !matchResult.getNewCoursesToAdd().isEmpty()) {
            feedback.append(" [Courses added: ")
                    .append(matchResult.getNewCoursesToAdd().stream().map(Course::getCourseName).collect(Collectors.joining(", ")))
                    .append("]");
        }
        if (matchResult.isAmbiguous()) {
            feedback.append(" [Note: Ambiguous match detected due to historical duplicate records; selected most recent active lead]");
        }

        LeadStatusHistory history = LeadStatusHistory.builder()
                .lead(lead)
                .previousStatus(lead.getCurrentStatus())
                .newStatus(lead.getCurrentStatus())
                .changedByUser(actingUser)
                .feedback(feedback.toString())
                .build();

        leadStatusHistoryRepository.save(history);
        if (lead.getStatusHistories() != null) {
            lead.getStatusHistories().add(history);
        }

        Lead saved = leadRepository.save(lead);
        log.info("Successfully updated and preserved matching lead: {} ({}) with multi-course/source state",
                saved.getFullName(), saved.getLeadCode());
        return saved;
    }

    @Override
    public <T> T executeWithPhoneLock(String normalizedPhone, Supplier<T> action) {
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return action.get();
        }
        Object lock = phoneLocks.computeIfAbsent(normalizedPhone, k -> new Object());
        synchronized (lock) {
            return action.get();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadDuplicateDiagnosticDTO> generateDuplicateDiagnostics() {
        List<Lead> allActive = leadRepository.findAll().stream()
                .filter(l -> !l.isDeleted() && l.getPhoneNumber() != null && !l.getPhoneNumber().isBlank())
                .toList();

        Map<String, List<Lead>> byPhone = allActive.stream()
                .collect(Collectors.groupingBy(l -> normalizePhone(l.getPhoneNumber())));

        List<LeadDuplicateDiagnosticDTO> report = new ArrayList<>();

        for (Map.Entry<String, List<Lead>> phoneEntry : byPhone.entrySet()) {
            String phone = phoneEntry.getKey();
            List<Lead> phoneGroup = phoneEntry.getValue();
            if (phoneGroup.size() <= 1) {
                continue;
            }

            Map<String, List<Lead>> byName = phoneGroup.stream()
                    .collect(Collectors.groupingBy(l -> normalizeName(l.getFullName())));

            for (Map.Entry<String, List<Lead>> nameEntry : byName.entrySet()) {
                String name = nameEntry.getKey();
                List<Lead> nameGroup = nameEntry.getValue();

                if (nameGroup.size() > 1) {
                    Set<String> distinctNonEmptyEmails = nameGroup.stream()
                            .map(Lead::getEmail)
                            .filter(Objects::nonNull)
                            .map(this::normalizeEmail)
                            .filter(e -> !e.isBlank())
                            .collect(Collectors.toSet());

                    boolean hasMissingEmail = nameGroup.stream()
                            .map(Lead::getEmail)
                            .anyMatch(e -> e == null || e.isBlank());

                    String classification;
                    if (distinctNonEmptyEmails.size() > 1) {
                        classification = "SAME_PHONE_DIFFERENT_EMAIL";
                    } else if (distinctNonEmptyEmails.size() == 1 && hasMissingEmail) {
                        classification = "COMPATIBLE_MISSING_EMAIL";
                    } else if (distinctNonEmptyEmails.size() == 1 && !hasMissingEmail) {
                        classification = "EXACT_DUPLICATE";
                    } else if (distinctNonEmptyEmails.isEmpty()) {
                        classification = "COMPATIBLE_MISSING_EMAIL";
                    } else {
                        classification = "AMBIGUOUS_MULTIPLE_MATCH";
                    }

                    List<LeadCandidateSummaryDTO> candidateDTOs = nameGroup.stream()
                            .map(this::toCandidateSummary)
                            .toList();

                    report.add(LeadDuplicateDiagnosticDTO.builder()
                            .normalizedPhone(phone)
                            .normalizedName(name)
                            .matchClassification(classification)
                            .candidateCount(nameGroup.size())
                            .candidates(candidateDTOs)
                            .build());
                }
            }

            // Also check for multiple distinct names on same phone
            if (byName.size() > 1) {
                List<LeadCandidateSummaryDTO> candidateDTOs = phoneGroup.stream()
                        .map(this::toCandidateSummary)
                        .toList();

                report.add(LeadDuplicateDiagnosticDTO.builder()
                        .normalizedPhone(phone)
                        .normalizedName("[MULTIPLE DIFFERENT NAMES]")
                        .matchClassification("SAME_PHONE_DIFFERENT_NAME")
                        .candidateCount(phoneGroup.size())
                        .candidates(candidateDTOs)
                        .build());
            }
        }

        return report;
    }

    private LeadCandidateSummaryDTO toCandidateSummary(Lead lead) {
        List<String> sources = lead.getLeadSources() != null
                ? lead.getLeadSources().stream().map(LeadSource::getName).filter(Objects::nonNull).toList()
                : List.of();

        List<String> courses = lead.getInterestedCourses() != null
                ? lead.getInterestedCourses().stream().map(Course::getCourseName).filter(Objects::nonNull).toList()
                : (lead.getCourse() != null ? List.of(lead.getCourse().getCourseName()) : List.of());

        return LeadCandidateSummaryDTO.builder()
                .id(lead.getId())
                .leadCode(lead.getLeadCode())
                .fullName(lead.getFullName())
                .phoneNumber(lead.getPhoneNumber())
                .alternatePhoneNumber(lead.getAlternatePhoneNumber())
                .email(lead.getEmail())
                .sourceNames(sources)
                .courseNames(courses)
                .currentStatus(lead.getCurrentStatus() != null ? lead.getCurrentStatus().getName() : "N/A")
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }
}
