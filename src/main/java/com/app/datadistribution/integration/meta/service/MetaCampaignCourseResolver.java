package com.app.datadistribution.integration.meta.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.repository.CourseRepository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetaCampaignCourseResolver {

    private final CourseRepository courseRepository;

    // Standard course aliases mapped to canonical course code/identifiers
    private static final Map<String, List<String>> CANONICAL_ALIASES = new HashMap<>();

    static {
        // MBA
        CANONICAL_ALIASES.put("MBA", List.of(
                "mba", "m.b.a", "master of business administration", "m.b.a."
        ));
        // BBA
        CANONICAL_ALIASES.put("BBA", List.of(
                "bba", "b.b.a", "bachelor of business administration", "b.b.a."
        ));
        // MCA
        CANONICAL_ALIASES.put("MCA", List.of(
                "mca", "m.c.a", "master of computer applications", "m.c.a."
        ));
        // BCA
        CANONICAL_ALIASES.put("BCA", List.of(
                "bca", "b.c.a", "bachelor of computer applications", "b.c.a."
        ));
        // B.Tech
        CANONICAL_ALIASES.put("B.TECH", List.of(
                "b.tech", "btech", "b tech", "b-tech", "bachelor of technology",
                "b.e", "b.e.", "bachelor of engineering"
        ));
        // M.Tech
        CANONICAL_ALIASES.put("M.TECH", List.of(
                "m.tech", "mtech", "m tech", "m-tech", "master of technology",
                "m.e", "m.e.", "master of engineering"
        ));
        // B.Com
        CANONICAL_ALIASES.put("B.COM", List.of(
                "b.com", "bcom", "b com", "b-com", "bachelor of commerce"
        ));
        // M.Com
        CANONICAL_ALIASES.put("M.COM", List.of(
                "m.com", "mcom", "m com", "m-com", "master of commerce"
        ));
        // B.Sc
        CANONICAL_ALIASES.put("B.SC", List.of(
                "b.sc", "bsc", "b sc", "b-sc", "bachelor of science"
        ));
        // M.Sc
        CANONICAL_ALIASES.put("M.SC", List.of(
                "m.sc", "msc", "m sc", "m-sc", "master of science"
        ));
        // LLB
        CANONICAL_ALIASES.put("LLB", List.of(
                "llb", "ll.b", "ll.b.", "bachelor of law", "bachelor of laws"
        ));
        // LLM
        CANONICAL_ALIASES.put("LLM", List.of(
                "llm", "ll.m", "ll.m.", "master of law", "master of laws"
        ));
        // B.Des
        CANONICAL_ALIASES.put("B.DES", List.of(
                "b.des", "bdes", "b des", "bachelor of design"
        ));
        // M.Des
        CANONICAL_ALIASES.put("M.DES", List.of(
                "m.des", "mdes", "m des", "master of design"
        ));
        // B.Pharm
        CANONICAL_ALIASES.put("B.PHARM", List.of(
                "b.pharm", "bpharm", "b pharm", "bachelor of pharmacy"
        ));
        // D.Pharm
        CANONICAL_ALIASES.put("D.PHARM", List.of(
                "d.pharm", "dpharm", "d pharm", "diploma in pharmacy"
        ));
        // BA
        CANONICAL_ALIASES.put("BA", List.of(
                "b.a", "b.a.", "bachelor of arts"
        ));
        // MA
        CANONICAL_ALIASES.put("MA", List.of(
                "m.a", "m.a.", "master of arts"
        ));
    }

    /**
     * Resolves the corresponding existing Course Master record from a Meta Campaign Name.
     *
     * @param campaignName The raw campaign name string from Meta (e.g. "MBA Admission 2027")
     * @return CourseResolutionResult containing status (SUCCESS, NO_MATCH, AMBIGUOUS) and the resolved Course
     */
    public CourseResolutionResult resolveCourse(String campaignName) {
        if (campaignName == null || campaignName.trim().isBlank()) {
            return CourseResolutionResult.noMatch("Campaign name is null or blank");
        }

        String normalizedCampaign = normalizeText(campaignName);
        if (normalizedCampaign.isBlank()) {
            return CourseResolutionResult.noMatch("Campaign name is blank after normalization");
        }

        // Fetch all currently active, non-deleted Course records from the database
        List<Course> activeCourses = courseRepository.findByStatusAndIsDeletedFalseOrderByCourseNameAsc(Status.ACTIVE);
        if (activeCourses.isEmpty()) {
            log.warn("Course resolution skipped: No active courses exist in Course Master");
            return CourseResolutionResult.noMatch("No active courses in Course Master");
        }

        // 1. Identify all matching active courses
        Map<Course, MatchCandidate> matchedCourses = new HashMap<>();

        for (Course course : activeCourses) {
            MatchCandidate candidate = matchCourse(course, normalizedCampaign);
            if (candidate != null) {
                matchedCourses.put(course, candidate);
            }
        }

        if (matchedCourses.isEmpty()) {
            return CourseResolutionResult.noMatch("No active course matched campaign name: " + campaignName);
        }

        // 2. Filter subsumed matches (e.g., "B.Tech Computer Science" vs generic "B.Tech")
        Set<Course> filteredMatches = resolveSubsumption(matchedCourses);

        if (filteredMatches.size() == 1) {
            Course resolved = filteredMatches.iterator().next();
            MatchCandidate candidate = matchedCourses.get(resolved);
            log.info("Meta campaign course resolver: matched course '{}' ({}) via token '{}' in campaign '{}'",
                    resolved.getCourseName(), resolved.getId(), candidate.matchedToken, campaignName);
            return CourseResolutionResult.success(resolved, candidate.matchedToken);
        }

        // 3. If multiple distinct non-subsumed courses match (e.g. "BBA and MBA Admission"), do not guess
        List<String> matchedNames = filteredMatches.stream().map(Course::getCourseName).toList();
        log.warn("Meta campaign course resolver: Ambiguous match in campaign '{}'. Detected multiple courses: {}",
                campaignName, matchedNames);
        return CourseResolutionResult.ambiguous("Ambiguous match: multiple courses detected " + matchedNames);
    }

    private MatchCandidate matchCourse(Course course, String normalizedCampaign) {
        String cName = course.getCourseName() != null ? course.getCourseName().trim() : "";
        String cCode = course.getCourseCode() != null ? course.getCourseCode().trim() : "";

        // Collect all test phrases for this course (course name, course code, and canonical aliases)
        List<String> termsToTest = new ArrayList<>();
        if (!cName.isEmpty()) termsToTest.add(cName);
        if (!cCode.isEmpty()) termsToTest.add(cCode);

        // Find canonical aliases matching either course code or course name
        for (Map.Entry<String, List<String>> entry : CANONICAL_ALIASES.entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase(cCode) || key.equalsIgnoreCase(cName)
                    || normalizeCode(key).equals(normalizeCode(cCode))
                    || normalizeCode(key).equals(normalizeCode(cName))) {
                termsToTest.addAll(entry.getValue());
            }
        }

        // Test each term against normalized campaign text
        String bestMatchToken = null;
        int maxTokenLength = -1;

        for (String term : termsToTest) {
            if (term == null || term.isBlank()) continue;
            if (matchesDiscreteTerm(normalizedCampaign, term)) {
                if (term.length() > maxTokenLength) {
                    maxTokenLength = term.length();
                    bestMatchToken = term;
                }
            }
        }

        if (bestMatchToken != null) {
            return new MatchCandidate(course, bestMatchToken, maxTokenLength);
        }

        return null;
    }

    /**
     * Checks if a term appears as a discrete word or token in the target text.
     */
    private boolean matchesDiscreteTerm(String targetText, String term) {
        String normTerm = term.trim().toLowerCase(Locale.ROOT);
        if (normTerm.isEmpty()) return false;

        // Build pattern ensuring word/boundary isolation
        // Allows boundary chars: start/end, spaces, hyphen, slash, comma, parenthesis
        String regex = "(?i)(?<=^|[\\s_\\-\\[\\](),/])" + Pattern.quote(normTerm) + "(?=$|[\\s_\\-\\[\\](),/])";
        return Pattern.compile(regex).matcher(targetText).find();
    }

    /**
     * If one matched course's matched token is a strict substring of another matched token
     * (e.g. "b.tech" inside "b.tech computer science"), the longer, more specific course takes precedence.
     */
    private Set<Course> resolveSubsumption(Map<Course, MatchCandidate> candidates) {
        Set<Course> result = new HashSet<>(candidates.keySet());
        if (result.size() <= 1) {
            return result;
        }

        for (Course c1 : candidates.keySet()) {
            MatchCandidate cand1 = candidates.get(c1);
            for (Course c2 : candidates.keySet()) {
                if (c1.equals(c2)) continue;
                MatchCandidate cand2 = candidates.get(c2);

                // If cand2's matched token is longer and contains cand1's token
                if (cand2.tokenLength > cand1.tokenLength
                        && cand2.matchedToken.toLowerCase().contains(cand1.matchedToken.toLowerCase())) {
                    result.remove(c1);
                }
            }
        }
        return result;
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        return input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String normalizeCode(String code) {
        if (code == null) return "";
        return code.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static class MatchCandidate {
        final Course course;
        final String matchedToken;
        final int tokenLength;

        MatchCandidate(Course course, String matchedToken, int tokenLength) {
            this.course = course;
            this.matchedToken = matchedToken;
            this.tokenLength = tokenLength;
        }
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CourseResolutionResult {

        public enum Status {
            SUCCESS,
            NO_MATCH,
            AMBIGUOUS
        }

        private final Status status;
        private final Course course;
        private final String matchReason;

        public boolean isSuccess() {
            return status == Status.SUCCESS && course != null;
        }

        public boolean isAmbiguous() {
            return status == Status.AMBIGUOUS;
        }

        public static CourseResolutionResult success(Course course, String reason) {
            return CourseResolutionResult.builder()
                    .status(Status.SUCCESS)
                    .course(course)
                    .matchReason(reason)
                    .build();
        }

        public static CourseResolutionResult noMatch(String reason) {
            return CourseResolutionResult.builder()
                    .status(Status.NO_MATCH)
                    .course(null)
                    .matchReason(reason)
                    .build();
        }

        public static CourseResolutionResult ambiguous(String reason) {
            return CourseResolutionResult.builder()
                    .status(Status.AMBIGUOUS)
                    .course(null)
                    .matchReason(reason)
                    .build();
        }
    }
}

