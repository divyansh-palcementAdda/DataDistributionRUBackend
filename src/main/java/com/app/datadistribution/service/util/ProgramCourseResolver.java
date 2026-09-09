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

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProgramCourseResolver {

    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;

    /**
     * Resolves and validates that the course (and any interested courses) are mapped to the program if program is provided.
     *
     * @param programId UUID of the program (nullable)
     * @param courseId UUID of primary course (nullable)
     * @param interestedCourseIds List of interested course UUIDs (nullable)
     * @return Resolved Program entity, or null if programId is null
     * @throws BadRequestException 
     */
    public Program resolveAndValidate(UUID programId, UUID courseId, Collection<UUID> interestedCourseIds) throws BadRequestException {
        if (programId == null) {
            return null;
        }

        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found with ID: " + programId));

        if (courseId != null) {
            boolean isMapped = programRepository.isCourseMappedToProgram(programId, courseId);
            if (!isMapped) {
                Course course = courseRepository.findById(courseId).orElse(null);
                String courseName = (course != null) ? course.getCourseName() : courseId.toString();
                throw new BadRequestException("Course '" + courseName + "' is not mapped to Program '" + program.getName() + "'.");
            }
        }

        if (interestedCourseIds != null && !interestedCourseIds.isEmpty()) {
            for (UUID cId : interestedCourseIds) {
                if (cId != null && !programRepository.isCourseMappedToProgram(programId, cId)) {
                    Course course = courseRepository.findById(cId).orElse(null);
                    String courseName = (course != null) ? course.getCourseName() : cId.toString();
                    throw new BadRequestException("Interested Course '" + courseName + "' is not mapped to Program '" + program.getName() + "'.");
                }
            }
        }

        return program;
    }

    /**
     * Resolves a Program by name or code (case-insensitive, trimmed).
     */
    public Optional<Program> resolveProgramByNameOrCode(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = nameOrCode.trim();
        return programRepository.findByNameIgnoreCase(clean)
                .or(() -> programRepository.findByCodeIgnoreCase(clean));
    }

    /**
     * Resolves a Course by name or code (case-insensitive, trimmed).
     */
    public Optional<Course> resolveCourseByNameOrCode(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = nameOrCode.trim();
        return courseRepository.findByCourseNameIgnoreCase(clean)
                .or(() -> courseRepository.findByCourseCodeIgnoreCase(clean));
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
}
