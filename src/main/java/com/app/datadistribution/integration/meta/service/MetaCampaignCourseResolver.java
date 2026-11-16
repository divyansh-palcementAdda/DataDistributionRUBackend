package com.app.datadistribution.integration.meta.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.integration.meta.dto.MetaLeadFieldDataDTO;
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
                "mba", "m.b.a", "master of business administration", "m.b.a.", "mba core"
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
        // B.Sc CS
        CANONICAL_ALIASES.put("B.SC CS", List.of(
                "bsc cs", "b.sc cs", "bsc computer science", "b.sc computer science",
                "b.sc. computer science", "bachelor of science in computer science",
                "bsc-cs", "b.sc-cs"
        ));
        // M.Sc
        CANONICAL_ALIASES.put("M.SC", List.of(
                "m.sc", "msc", "m sc", "m-sc", "master of science"
        ));
        // M.Sc Food
        CANONICAL_ALIASES.put("M.SC FOOD", List.of(
                "msc food", "m.sc food", "msc food technology", "m.sc food technology",
                "msc food science", "m.sc food science", "master of science in food technology"
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
                "b.des", "bdes", "b des", "b design", "b.design", "bachelor of design"
        ));
        // M.Des
        CANONICAL_ALIASES.put("M.DES", List.of(
                "m.des", "mdes", "m des", "m design", "m.design", "master of design"
        ));
        // MFM
        CANONICAL_ALIASES.put("MFM", List.of(
                "mfm", "m.f.m", "master of fashion management", "master in fashion management"
        ));
        // B.Pharm
        CANONICAL_ALIASES.put("B.PHARM", List.of(
                "b.pharm", "bpharm", "b pharm", "bachelor of pharmacy"
        ));
        // D.Pharm
        CANONICAL_ALIASES.put("D.PHARM", List.of(
                "d.pharm", "dpharm", "d pharm", "diploma in pharmacy"
        ));
        // BMLT
        CANONICAL_ALIASES.put("BMLT", List.of(
                "bmlt", "b.m.l.t", "bachelor in medical laboratory technology",
                "bachelor of medical laboratory technology", "b.sc mlt", "bsc mlt"
        ));
        // DMLT
        CANONICAL_ALIASES.put("DMLT", List.of(
                "dmlt", "d.m.l.t", "diploma in medical laboratory technology",
                "diploma of medical laboratory technology"
        ));
        // DXRT
        CANONICAL_ALIASES.put("DXRT", List.of(
                "dxrt", "d.x.r.t", "diploma in x-ray technology",
                "diploma in x-ray & radiology technology", "diploma in radiology technology",
                "diploma in radiotherapy technology"
        ));
        // BPT
        CANONICAL_ALIASES.put("BPT", List.of(
                "bpt", "b.p.t", "bachelor of physiotherapy", "bachelors in physiotherapy"
        ));
        // MPT
        CANONICAL_ALIASES.put("MPT", List.of(
                "mpt", "m.p.t", "master of physiotherapy", "masters in physiotherapy"
        ));
        // BA
        CANONICAL_ALIASES.put("BA", List.of(
                "b.a", "b.a.", "bachelor of arts"
        ));
        // MA
        CANONICAL_ALIASES.put("MA", List.of(
                "m.a", "m.a.", "master of arts"
        ));
        // MA Psychology
        CANONICAL_ALIASES.put("MA PSY", List.of(
                "ma psy", "m.a psy", "ma psychology", "m.a psychology",
                "master of arts in psychology", "ma-psy"
        ));
        // BA Psychology
        CANONICAL_ALIASES.put("BA PSY", List.of(
                "ba psy", "b.a psy", "ba psychology", "b.a psychology",
                "bachelor of arts in psychology", "ba-psy"
        ));
    }

    // Configurable question keywords for identifying course-selection question in Meta lead forms
    private static final List<String> COURSE_QUESTION_KEYWORDS = List.of(
            "which program are you interested in",
            "which course are you interested in",
            "select your preferred course",
            "select your preferred program",
            "what program would you like to apply for",
            "what course would you like to apply for",
            "which program would you like to apply for",
            "which course would you like to apply for",
            "interested course",
            "course of interest",
            "program of interest",
            "interested program",
            "preferred course",
            "preferred program",
            "select course",
            "select program",
            "course interested",
            "program interested",
            "what course are you interested in",
            "what program are you interested in"
    );

    /**
     * Resolves the corresponding existing Course Master record using both Meta Campaign Name
     * and Meta Lead Form field_data, following all Phase 2 and Phase 5 priority and decision rules.
     *
     * @param campaignName The raw campaign name string from Meta (e.g. "RU_LG_BMLT/DMLT/DXRT 14 NOV")
     * @param fieldData    The list of form field data entries returned from Meta Graph API
     * @return MetaCourseResolutionResult with resolved Course, mapping method, and traceability reason
     */
    public MetaCourseResolutionResult resolve(String campaignName, List<MetaLeadFieldDataDTO> fieldData) {
        List<Course> activeCourses = courseRepository.findByStatusAndIsDeletedFalseOrderByCourseNameAsc(Status.ACTIVE);
        if (activeCourses.isEmpty()) {
            log.warn("Course resolution skipped: No active courses exist in Course Master");
            return MetaCourseResolutionResult.noMatch("No active courses in Course Master", null, null, Collections.emptyList(), false);
        }

        // 1. Parse form field_data to identify course-selection question and applicant's answer
        FormCourseAnswer formAnswer = extractFormCourseAnswer(fieldData);
        String formQName = formAnswer != null ? formAnswer.getQuestionName() : null;
        String formAnsRaw = formAnswer != null ? formAnswer.getRawAnswer() : null;

        Course matchedFormCourse = null;
        if (formAnsRaw != null && !formAnsRaw.isBlank()) {
            matchedFormCourse = matchCourseAnswer(formAnsRaw, activeCourses);
        }

        // 2. Extract course candidates from Campaign Name
        List<Course> campaignCandidates = extractCampaignCourseCandidates(campaignName, activeCourses);
        boolean isMultiCourseCampaign = campaignCandidates.size() > 1;

        // 3. Apply Decision Logic (Phase 2 & Phase 5)

        // --- CASE 2: TYPE B - MULTIPLE-COURSE CAMPAIGNS (e.g. RU_LG_BMLT/DMLT/DXRT 14 NOV) ---
        if (isMultiCourseCampaign) {
            List<String> candidateNames = campaignCandidates.stream().map(Course::getCourseName).toList();
            log.info("Meta campaign '{}' detected as MULTI-COURSE campaign with candidates: {}", campaignName, candidateNames);

            // Applicant's selected form answer is authoritative for multi-course campaigns
            if (formAnsRaw != null && !formAnsRaw.isBlank()) {
                if (matchedFormCourse != null) {
                    String reason = String.format("Authoritative form answer '%s' matched course '%s' from multi-course campaign (%s)",
                            formAnsRaw, matchedFormCourse.getCourseName(), String.join(", ", candidateNames));
                    log.info("Meta lead resolved course: {}", reason);
                    return MetaCourseResolutionResult.success(
                            matchedFormCourse,
                            MetaCourseResolutionResult.ResolutionMethod.FORM_ANSWER,
                            reason,
                            formQName,
                            formAnsRaw,
                            campaignCandidates,
                            true
                    );
                } else {
                    String reason = String.format("Applicant selected course '%s' from multi-course campaign (%s) but not found in Course Master",
                            formAnsRaw, String.join(", ", candidateNames));
                    log.warn("Meta lead course unresolved: {}", reason);
                    return MetaCourseResolutionResult.noMatch(
                            reason,
                            formQName,
                            formAnsRaw,
                            campaignCandidates,
                            true
                    );
                }
            } else {
                // Multi-course campaign without selected answer: NEVER guess, NEVER pick first
                String reason = String.format("Multi-course campaign (%s) but applicant course selection answer is missing in lead form",
                        String.join(", ", candidateNames));
                log.info("Meta lead course unresolved: {}", reason);
                return MetaCourseResolutionResult.multiCourseMissingAnswer(
                        reason,
                        formQName,
                        null,
                        campaignCandidates
                );
            }
        }

        // --- CASE 1: TYPE A - SINGLE-COURSE CAMPAIGNS (e.g. RU_LG_MBA_CORE_12-11-26, RU_LG_MSc_Food_12-11-26) ---
        if (campaignCandidates.size() == 1) {
            Course singleCampaignCourse = campaignCandidates.get(0);

            // Check consistency with form answer if present
            if (formAnsRaw != null && !formAnsRaw.isBlank()) {
                if (matchedFormCourse != null) {
                    if (matchedFormCourse.getId().equals(singleCampaignCourse.getId())) {
                        String reason = String.format("Resolved course '%s' from single-course campaign '%s' and verified by form answer '%s'",
                                singleCampaignCourse.getCourseName(), campaignName, formAnsRaw);
                        return MetaCourseResolutionResult.success(
                                singleCampaignCourse,
                                MetaCourseResolutionResult.ResolutionMethod.CAMPAIGN_NAME,
                                reason,
                                formQName,
                                formAnsRaw,
                                campaignCandidates,
                                false
                        );
                    } else {
                        // CONFLICT: Single-course campaign indicates Course A, but applicant explicitly selected Course B
                        String reason = String.format("Conflict detected: Campaign indicated '%s' but applicant explicitly selected '%s' on form. Prioritizing applicant form choice.",
                                singleCampaignCourse.getCourseName(), matchedFormCourse.getCourseName());
                        log.warn("Meta lead course mapping conflict: {}", reason);
                        return MetaCourseResolutionResult.conflict(
                                matchedFormCourse,
                                MetaCourseResolutionResult.ResolutionMethod.FORM_ANSWER,
                                reason,
                                formQName,
                                formAnsRaw,
                                campaignCandidates
                        );
                    }
                } else {
                    // Form answer unrecognized, but campaign is unequivocally a single-course campaign
                    String reason = String.format("Resolved course '%s' from single-course campaign '%s' (unrecognized form answer: '%s')",
                            singleCampaignCourse.getCourseName(), campaignName, formAnsRaw);
                    return MetaCourseResolutionResult.success(
                            singleCampaignCourse,
                            MetaCourseResolutionResult.ResolutionMethod.CAMPAIGN_NAME,
                            reason,
                            formQName,
                            formAnsRaw,
                            campaignCandidates,
                            false
                    );
                }
            } else {
                // No form answer provided; single-course campaign name is authoritative
                String reason = String.format("Resolved course '%s' from single-course campaign '%s'",
                        singleCampaignCourse.getCourseName(), campaignName);
                return MetaCourseResolutionResult.success(
                        singleCampaignCourse,
                        MetaCourseResolutionResult.ResolutionMethod.CAMPAIGN_NAME,
                        reason,
                        formQName,
                        null,
                        campaignCandidates,
                        false
                );
            }
        }

        // --- CASE 3: CAMPAIGN NAME DOES NOT MATCH ANY COURSE (OR BLANK) ---
        if (formAnsRaw != null && !formAnsRaw.isBlank()) {
            if (matchedFormCourse != null) {
                String reason = String.format("Resolved course '%s' from lead form answer '%s'",
                        matchedFormCourse.getCourseName(), formAnsRaw);
                return MetaCourseResolutionResult.success(
                        matchedFormCourse,
                        MetaCourseResolutionResult.ResolutionMethod.FORM_ANSWER,
                        reason,
                        formQName,
                        formAnsRaw,
                        campaignCandidates,
                        false
                );
            } else {
                String reason = String.format("Applicant selected course '%s' not found in Course Master", formAnsRaw);
                return MetaCourseResolutionResult.noMatch(
                        reason,
                        formQName,
                        formAnsRaw,
                        campaignCandidates,
                        false
                );
            }
        }

        return MetaCourseResolutionResult.noMatch(
                "No active course matched campaign name '" + campaignName + "' and no course question answered in form",
                formQName,
                null,
                campaignCandidates,
                false
        );
    }

    /**
     * Backward-compatible method resolving course only from campaign name.
     */
    public CourseResolutionResult resolveCourse(String campaignName) {
        if (campaignName == null || campaignName.trim().isBlank()) {
            return CourseResolutionResult.noMatch("Campaign name is null or blank");
        }

        List<Course> activeCourses = courseRepository.findByStatusAndIsDeletedFalseOrderByCourseNameAsc(Status.ACTIVE);
        if (activeCourses.isEmpty()) {
            log.warn("Course resolution skipped: No active courses exist in Course Master");
            return CourseResolutionResult.noMatch("No active courses in Course Master");
        }

        List<Course> candidates = extractCampaignCourseCandidates(campaignName, activeCourses);
        if (candidates.isEmpty()) {
            return CourseResolutionResult.noMatch("No active course matched campaign name: " + campaignName);
        }
        if (candidates.size() == 1) {
            Course c = candidates.get(0);
            return CourseResolutionResult.success(c, "Matched course " + c.getCourseName() + " in campaign " + campaignName);
        }

        List<String> names = candidates.stream().map(Course::getCourseName).toList();
        return CourseResolutionResult.ambiguous("Ambiguous match: multiple courses detected " + names);
    }

    /**
     * Extracts course candidates from a Meta campaign name by stripping known prefixes,
     * stripping date suffixes, splitting on delimiters ('/', ','), and applying subsumption.
     */
    public List<Course> extractCampaignCourseCandidates(String campaignName, List<Course> activeCourses) {
        if (campaignName == null || campaignName.trim().isBlank() || activeCourses == null || activeCourses.isEmpty()) {
            return Collections.emptyList();
        }

        String cleaned = cleanCampaignName(campaignName);
        Map<Course, MatchCandidate> matched = new HashMap<>();

        // If delimited by '/' or ',', evaluate each segment individually (e.g. "BMLT/DMLT/DXRT", "MFM/M Design")
        if (cleaned.contains("/") || cleaned.contains(",")) {
            String[] segments = cleaned.split("[/,]");
            for (String seg : segments) {
                String trimmedSeg = seg.trim();
                if (trimmedSeg.isEmpty()) continue;
                for (Course course : activeCourses) {
                    MatchCandidate cand = matchCourseAgainstText(course, trimmedSeg);
                    if (cand != null) {
                        if (!matched.containsKey(course) || cand.tokenLength > matched.get(course).tokenLength) {
                            matched.put(course, cand);
                        }
                    }
                }
            }
        }

        // Also evaluate across the cleaned campaign string as a whole
        for (Course course : activeCourses) {
            MatchCandidate cand = matchCourseAgainstText(course, cleaned);
            if (cand != null) {
                if (!matched.containsKey(course) || cand.tokenLength > matched.get(course).tokenLength) {
                    matched.put(course, cand);
                }
            }
        }

        // Also evaluate across the raw campaign string in case cleaning was too aggressive
        for (Course course : activeCourses) {
            MatchCandidate cand = matchCourseAgainstText(course, campaignName);
            if (cand != null) {
                if (!matched.containsKey(course) || cand.tokenLength > matched.get(course).tokenLength) {
                    matched.put(course, cand);
                }
            }
        }

        if (matched.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter subsumed matches (e.g. "B.Tech Computer Science" vs generic "B.Tech", "MSc Food" vs "MSc")
        Set<Course> nonSubsumed = resolveSubsumption(matched);
        return new ArrayList<>(nonSubsumed);
    }

    /**
     * Cleans campaign prefixes (e.g. RU_LG_, RU_BA_) and date suffixes (e.g. 14 NOV, 12-11-26, 21-10-2026).
     */
    public String cleanCampaignName(String campaignName) {
        if (campaignName == null) return "";
        String s = campaignName.trim();

        // 1. Remove prefixes like RU_LG_, RU_BA_, RU_, LG_, BA_ (case-insensitive)
        s = s.replaceAll("^(?i)(ru[_\\s]+(lg|ba)[_\\s]+|ru[_\\s]+|lg[_\\s]+|ba[_\\s]+)", "");

        // 2. Remove date suffixes: e.g. _12-11-26, _21-10-2026, 14 NOV, 14-NOV, 12-10-2026
        s = s.replaceAll("(?i)([_\\s]+\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})$", "");
        s = s.replaceAll("(?i)([_\\s]+\\d{1,2}\\s+[a-z]{3,9})$", "");
        s = s.replaceAll("(?i)([_\\s]+\\d{1,2}[-_][a-z]{3,9})$", "");

        // 3. Remove common marketing suffixes: e.g. "Admission 2027", "Admissions Campaign", "Lead Generation"
        s = s.replaceAll("(?i)([_\\s]+(admissions?|lead[_\\s]+generation|campaign))(\\s+\\d{4})?", "");

        return s.trim();
    }

    /**
     * Extracts the course-selection question and the applicant's selected answer from Meta field_data.
     */
    public FormCourseAnswer extractFormCourseAnswer(List<MetaLeadFieldDataDTO> fieldData) {
        if (fieldData == null || fieldData.isEmpty()) {
            return null;
        }

        // 1. First pass: Check for explicit course-interest question phrases
        for (MetaLeadFieldDataDTO f : fieldData) {
            String qName = f.getName();
            if (qName == null) continue;
            String normalizedQ = normalizeQuestionName(qName);
            for (String pattern : COURSE_QUESTION_KEYWORDS) {
                if (normalizedQ.equals(pattern) || normalizedQ.contains(pattern)) {
                    String val = f.getFirstValue();
                    if (val != null && !val.isBlank()) {
                        return new FormCourseAnswer(qName, val.trim());
                    }
                }
            }
        }

        // 2. Second pass: Check if question contains "course" or "program" (excluding unrelated fields)
        for (MetaLeadFieldDataDTO f : fieldData) {
            String qName = f.getName();
            if (qName == null) continue;
            String normalizedQ = normalizeQuestionName(qName);
            if ((normalizedQ.contains("course") || normalizedQ.contains("program"))
                    && !normalizedQ.contains("course type")
                    && !normalizedQ.contains("course fee")
                    && !normalizedQ.contains("program code")) {
                String val = f.getFirstValue();
                if (val != null && !val.isBlank()) {
                    return new FormCourseAnswer(qName, val.trim());
                }
            }
        }

        return null;
    }

    /**
     * Matches an applicant's form answer against active Course Master records.
     */
    public Course matchCourseAnswer(String answer, List<Course> activeCourses) {
        if (answer == null || answer.trim().isBlank() || activeCourses == null || activeCourses.isEmpty()) {
            return null;
        }

        String trimmedAns = answer.trim();
        String normAns = trimmedAns.toLowerCase(Locale.ROOT);
        String codeNormAns = normalizeCode(trimmedAns);

        // 1. Exact courseName or courseCode match (case-insensitive)
        for (Course c : activeCourses) {
            if (c.getCourseName() != null && c.getCourseName().equalsIgnoreCase(trimmedAns)) {
                return c;
            }
            if (c.getCourseCode() != null && c.getCourseCode().equalsIgnoreCase(trimmedAns)) {
                return c;
            }
        }

        // 2. Normalized alphanumeric code match
        if (!codeNormAns.isEmpty()) {
            for (Course c : activeCourses) {
                if (codeNormAns.equals(normalizeCode(c.getCourseCode()))
                        || codeNormAns.equals(normalizeCode(c.getCourseName()))) {
                    return c;
                }
            }
        }

        // 3. Match against canonical aliases
        for (Course c : activeCourses) {
            String cCode = c.getCourseCode() != null ? c.getCourseCode().trim() : "";
            String cName = c.getCourseName() != null ? c.getCourseName().trim() : "";

            for (Map.Entry<String, List<String>> entry : CANONICAL_ALIASES.entrySet()) {
                String key = entry.getKey();
                if (key.equalsIgnoreCase(cCode) || key.equalsIgnoreCase(cName)
                        || normalizeCode(key).equals(normalizeCode(cCode))
                        || normalizeCode(key).equals(normalizeCode(cName))) {
                    for (String alias : entry.getValue()) {
                        if (alias.equalsIgnoreCase(trimmedAns) || normalizeCode(alias).equals(codeNormAns)) {
                            return c;
                        }
                    }
                }
            }
        }

        // 4. Discrete boundary match
        MatchCandidate best = null;
        for (Course c : activeCourses) {
            MatchCandidate cand = matchCourseAgainstText(c, trimmedAns);
            if (cand != null) {
                if (best == null || cand.tokenLength > best.tokenLength) {
                    best = cand;
                }
            }
        }
        return best != null ? best.course : null;
    }

    private MatchCandidate matchCourseAgainstText(Course course, String text) {
        if (text == null || text.isBlank()) return null;

        String cName = course.getCourseName() != null ? course.getCourseName().trim() : "";
        String cCode = course.getCourseCode() != null ? course.getCourseCode().trim() : "";

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

        String bestMatchToken = null;
        int maxTokenLength = -1;

        for (String term : termsToTest) {
            if (term == null || term.isBlank()) continue;
            if (matchesDiscreteTerm(text, term)) {
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
     * Checks if a term appears as a discrete token or word in targetText,
     * treating underscores and spaces equivalently while respecting token boundaries.
     */
    private boolean matchesDiscreteTerm(String targetText, String term) {
        if (targetText == null || term == null) return false;
        String normTerm = term.trim().toLowerCase(Locale.ROOT);
        if (normTerm.isEmpty()) return false;

        // 1. Normalized check: replace underscores with spaces for comparison
        String normalizedTarget = targetText.replace('_', ' ').replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normalizedTerm = normTerm.replace('_', ' ').replaceAll("\\s+", " ");

        String regex = "(?i)(?<=^|[\\s\\-\\[\\](),/])" + Pattern.quote(normalizedTerm) + "(?=$|[\\s\\-\\[\\](),/])";
        if (Pattern.compile(regex).matcher(normalizedTarget).find()) {
            return true;
        }

        // 2. Direct check with underscores allowed as boundary
        String origRegex = "(?i)(?<=^|[\\s_\\-\\[\\](),/])" + Pattern.quote(normTerm) + "(?=$|[\\s_\\-\\[\\](),/])";
        return Pattern.compile(origRegex).matcher(targetText).find();
    }

    /**
     * Resolves subsumption between matched courses (e.g. "B.Tech Computer Science" vs "B.Tech",
     * "MSc Food" vs "MSc"). The longer, more specific course token takes precedence.
     */
    private Set<Course> resolveSubsumption(Map<Course, MatchCandidate> candidates) {
        Set<Course> result = new LinkedHashSet<>(candidates.keySet());
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
                        && cand2.matchedToken.toLowerCase(Locale.ROOT).contains(cand1.matchedToken.toLowerCase(Locale.ROOT))) {
                    result.remove(c1);
                }
            }
        }
        return result;
    }

    private String normalizeQuestionName(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
    @AllArgsConstructor
    public static class FormCourseAnswer {
        private final String questionName;
        private final String rawAnswer;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MetaCourseResolutionResult {

        public enum ResolutionMethod {
            CAMPAIGN_NAME,
            FORM_ANSWER,
            UNRESOLVED
        }

        public enum Status {
            SUCCESS,
            NO_MATCH,
            AMBIGUOUS,
            MULTI_COURSE_MISSING_ANSWER,
            CONFLICT
        }

        private final Status status;
        private final Course course;
        private final ResolutionMethod mappingMethod;
        private final String mappingReason;
        private final String courseQuestion;
        private final String courseAnswer;
        private final List<Course> campaignCandidates;
        private final boolean multiCourseCampaign;

        public boolean isSuccess() {
            return course != null && (status == Status.SUCCESS || status == Status.CONFLICT);
        }

        public static MetaCourseResolutionResult success(
                Course course,
                ResolutionMethod method,
                String reason,
                String question,
                String answer,
                List<Course> candidates,
                boolean multiCourse
        ) {
            return MetaCourseResolutionResult.builder()
                    .status(Status.SUCCESS)
                    .course(course)
                    .mappingMethod(method)
                    .mappingReason(reason)
                    .courseQuestion(question)
                    .courseAnswer(answer)
                    .campaignCandidates(candidates)
                    .multiCourseCampaign(multiCourse)
                    .build();
        }

        public static MetaCourseResolutionResult conflict(
                Course course,
                ResolutionMethod method,
                String reason,
                String question,
                String answer,
                List<Course> candidates
        ) {
            return MetaCourseResolutionResult.builder()
                    .status(Status.CONFLICT)
                    .course(course)
                    .mappingMethod(method)
                    .mappingReason(reason)
                    .courseQuestion(question)
                    .courseAnswer(answer)
                    .campaignCandidates(candidates)
                    .multiCourseCampaign(false)
                    .build();
        }

        public static MetaCourseResolutionResult multiCourseMissingAnswer(
                String reason,
                String question,
                String answer,
                List<Course> candidates
        ) {
            return MetaCourseResolutionResult.builder()
                    .status(Status.MULTI_COURSE_MISSING_ANSWER)
                    .course(null)
                    .mappingMethod(ResolutionMethod.UNRESOLVED)
                    .mappingReason(reason)
                    .courseQuestion(question)
                    .courseAnswer(answer)
                    .campaignCandidates(candidates)
                    .multiCourseCampaign(true)
                    .build();
        }

        public static MetaCourseResolutionResult noMatch(
                String reason,
                String question,
                String answer,
                List<Course> candidates,
                boolean multiCourse
        ) {
            return MetaCourseResolutionResult.builder()
                    .status(Status.NO_MATCH)
                    .course(null)
                    .mappingMethod(ResolutionMethod.UNRESOLVED)
                    .mappingReason(reason)
                    .courseQuestion(question)
                    .courseAnswer(answer)
                    .campaignCandidates(candidates)
                    .multiCourseCampaign(multiCourse)
                    .build();
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
