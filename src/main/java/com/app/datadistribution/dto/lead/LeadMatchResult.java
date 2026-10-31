package com.app.datadistribution.dto.lead;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.Program;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadMatchResult {

    public enum MatchStatus {
        NO_MATCH,
        EXACT_MATCH,
        ENRICHED_MATCH,
        AMBIGUOUS_MATCH
    }

    private MatchStatus status;
    private Lead matchedLead;
    private boolean emailEnriched;
    private String emailToEnrich;
    private boolean ambiguous;
    private String matchReason;

    @Builder.Default
    private Set<Course> newCoursesToAdd = new HashSet<>();

    @Builder.Default
    private Set<LeadSource> newSourcesToAdd = new HashSet<>();

    @Builder.Default
    private Set<Program> newProgramsToAdd = new HashSet<>();

    public boolean isMatched() {
        return matchedLead != null;
    }

    public static LeadMatchResult noMatch() {
        return noMatch("No match found");
    }

    public static LeadMatchResult noMatch(String reason) {
        return LeadMatchResult.builder()
                .status(MatchStatus.NO_MATCH)
                .matchReason(reason)
                .newCoursesToAdd(new HashSet<>())
                .newSourcesToAdd(new HashSet<>())
                .newProgramsToAdd(new HashSet<>())
                .build();
    }
}

