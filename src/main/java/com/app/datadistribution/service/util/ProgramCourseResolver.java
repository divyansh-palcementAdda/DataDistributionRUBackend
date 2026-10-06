package com.app.datadistribution.service.util;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourceNotFoundException;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Canonical service component for Program <-> Course bidirectional resolution and validation.
 * Serves as the single source of truth across Lead Create, Lead Update, and Bulk Upload.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProgramCourseResolver {

    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;

    /**
     * Single canonical resolution method for Lead <-> Program <-> Course relationships.
     * Enforces consistency and derives programs when courses are directly selected.
     *
     * @param explicitProgramIds Program UUIDs chosen by user / client (can be null/empty)
     * @param primaryCourseId Primary registered course UUID (nullable)
     * @param interestedCourseIds Additional interested course UUIDs (nullable)
     * @return Validated, canonical Set of Program entities to be persisted with the Lead
     * @throws BadRequestException if Course and Program are mutually inconsistent
     */
    public Set<Program> resolveLeadProgramCourseRelationship(
            Collection<UUID> explicitProgramIds,
            UUID primaryCourseId,
            Collection<UUID> interestedCourseIds) throws BadRequestException {

        Set<UUID> cleanProgIds = (explicitProgramIds != null)
                ? explicitProgramIds.stream().filter(Objects::nonNull).collect(Collectors.toSet())
                : Collections.emptySet();

        Set<UUID> cleanInterestedCourseIds = (interestedCourseIds != null)
                ? interestedCourseIds.stream().filter(Objects::nonNull).collect(Collectors.toSet())
                : Collections.emptySet();

        // Case 1: Explicit programs provided
        if (!cleanProgIds.isEmpty()) {
            Set<Program> explicitPrograms = new HashSet<>();
            for (UUID pId : cleanProgIds) {
                Program program = programRepository.findById(pId)
                        .filter(p -> !p.isDeleted())
                        .orElseThrow(() -> new ResourceNotFoundException("Program not found with ID: " + pId));
                if (!program.isActive()) {
                    throw new BadRequestException("Cannot select inactive Program: " + program.getName());
                }
                explicitPrograms.add(program);
            }

            // Consistency Validation: If primary course is selected, ensure it belongs to AT LEAST ONE selected program
            if (primaryCourseId != null) {
                boolean isMapped = programRepository.isCourseMappedToAnyProgram(cleanProgIds, primaryCourseId);
                if (!isMapped) {
                    Course course = courseRepository.findById(primaryCourseId).orElse(null);
                    String courseName = (course != null) ? course.getCourseName() : primaryCourseId.toString();
                    String programNames = explicitPrograms.stream().map(Program::getName).collect(Collectors.joining(", "));
                    throw new BadRequestException("COURSE_PROGRAM_MISMATCH: Course '" + courseName + "' is not mapped to selected Program(s) [" + programNames + "].");
                }
            }

            // Consistency Validation: If interested courses are selected, ensure each belongs to AT LEAST ONE selected program
            if (!cleanInterestedCourseIds.isEmpty()) {
                for (UUID cId : cleanInterestedCourseIds) {
                    boolean isMapped = programRepository.isCourseMappedToAnyProgram(cleanProgIds, cId);
                    if (!isMapped) {
                        Course course = courseRepository.findById(cId).orElse(null);
                        String courseName = (course != null) ? course.getCourseName() : cId.toString();
                        String programNames = explicitPrograms.stream().map(Program::getName).collect(Collectors.joining(", "));
                        throw new BadRequestException("COURSE_PROGRAM_MISMATCH: Interested Course '" + courseName + "' is not mapped to selected Program(s) [" + programNames + "].");
                    }
                }
            }

            return explicitPrograms;
        }

        // Case 2: No explicit programs provided, but Course is selected directly (Course -> Program auto-resolution)
        Set<Program> autoResolvedPrograms = new HashSet<>();
        if (primaryCourseId != null) {
            List<Program> mapped = programRepository.findActiveProgramsByCourseId(primaryCourseId);
            autoResolvedPrograms.addAll(mapped);
        }

        if (!cleanInterestedCourseIds.isEmpty() && autoResolvedPrograms.isEmpty()) {
            List<Program> mapped = programRepository.findActiveProgramsByCourseIds(cleanInterestedCourseIds);
            autoResolvedPrograms.addAll(mapped);
        }

        return autoResolvedPrograms;
    }

    /**
     * Backward-compatible delegation method for single programId.
     */
    public Program resolveAndValidate(UUID programId, UUID courseId, Collection<UUID> interestedCourseIds) throws BadRequestException {
        if (programId == null && courseId == null) {
            return null;
        }
        Collection<UUID> progIds = programId != null ? List.of(programId) : Collections.emptyList();
        Set<Program> resolved = resolveLeadProgramCourseRelationship(progIds, courseId, interestedCourseIds);
        return resolved.isEmpty() ? null : resolved.iterator().next();
    }

    /**
     * Resolves a Program by name or code (case-insensitive, trimmed).
     */
    public Optional<Program> resolveProgramByNameOrCode(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = nameOrCode.trim();
        return programRepository.findByNameIgnoreCaseAndIsDeletedFalse(clean)
                .or(() -> programRepository.findByCodeIgnoreCaseAndIsDeletedFalse(clean))
                .filter(Program::isActive);
    }

    /**
     * Resolves multiple Programs by comma-separated or list of names/codes.
     */
    public Set<Program> resolveProgramsByNameOrCodes(Collection<String> namesOrCodes) throws BadRequestException {
        if (namesOrCodes == null || namesOrCodes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Program> result = new HashSet<>();
        for (String raw : namesOrCodes) {
            if (raw == null || raw.isBlank()) continue;
            for (String token : raw.split(",")) {
                String clean = token.trim();
                if (clean.isEmpty()) continue;
                Program prog = resolveProgramByNameOrCode(clean)
                        .orElseThrow(() -> new BadRequestException("Program '" + clean + "' not found or is inactive"));
                result.add(prog);
            }
        }
        return result;
    }

    /**
     * Resolves a Course by name or code (case-insensitive, trimmed).
     */
    public Optional<Course> resolveCourseByNameOrCode(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = nameOrCode.trim();
        return courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse(clean)
                .or(() -> courseRepository.findByCourseCodeIgnoreCaseAndIsDeletedFalse(clean))
                .filter(Course::isActive);
    }

    /**
     * Validates if a course is mapped to a program.
     */
    public boolean isCourseMappedToProgram(Program program, Course course) {
        if (program == null || course == null) {
            return true; // No restriction if program or course is null
        }
        return programRepository.isCourseMappedToProgram(program.getId(), course.getId());
    }

    /**
     * Validates if a course is mapped to ANY program in a set of programs.
     */
    public boolean isCourseMappedToAnyProgram(Collection<Program> programs, Course course) {
        if (programs == null || programs.isEmpty() || course == null) {
            return true;
        }
        Set<UUID> progIds = programs.stream().map(Program::getId).collect(Collectors.toSet());
        return programRepository.isCourseMappedToAnyProgram(progIds, course.getId());
    }
}
