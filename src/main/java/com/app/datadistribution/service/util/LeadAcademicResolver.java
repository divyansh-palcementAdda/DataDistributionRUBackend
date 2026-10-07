package com.app.datadistribution.service.util;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourceNotFoundException;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.service.dto.LeadAcademicResolutionResult;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Single Canonical Academic Domain Resolver for Course, Program, and Course Type.
 * Enforces all academic business rules across:
 * - Lead Create
 * - Lead Edit
 * - Lead Bulk Upload
 *
 * Rules:
 * 1. Course <-> Program is the ONLY valid academic hierarchy involving Program.
 * 2. NO Program -> Course Type relationship.
 * 3. NO Course Type -> Program relationship.
 * 4. Course Type never determines Program or Course.
 * 5. Program never determines Course Type.
 * 6. Course -> Program automatically resolves Program when Program is absent.
 * 7. Course -> Course Type automatically resolves Course Type from canonical Course relation when Course Type is absent.
 * 8. All academic fields are optional (can remain null).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadAcademicResolver {

    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;
    private final CourseTypeRepository courseTypeRepository;

    /**
     * Resolves academic mappings for Lead Add / Edit using entity UUIDs.
     */
    public LeadAcademicResolutionResult resolveLeadAcademicMappings(
            Collection<UUID> explicitProgramIds,
            UUID primaryCourseId,
            Collection<UUID> interestedCourseIds,
            UUID explicitCourseTypeId
    ) throws BadRequestException {

        // 1. Resolve Course (Primary)
        Course resolvedCourse = null;
        String courseSource = null;
        if (primaryCourseId != null) {
            resolvedCourse = courseRepository.findById(primaryCourseId)
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + primaryCourseId));
            if (!resolvedCourse.isActive()) {
                throw new BadRequestException("Cannot select inactive Course: " + resolvedCourse.getCourseName());
            }
            courseSource = "USER";
        }

        // 2. Resolve Interested Courses
        Set<Course> resolvedInterestedCourses = new HashSet<>();
        if (resolvedCourse != null) {
            resolvedInterestedCourses.add(resolvedCourse);
        }
        if (interestedCourseIds != null && !interestedCourseIds.isEmpty()) {
            for (UUID cId : interestedCourseIds) {
                if (cId == null) continue;
                Course c = courseRepository.findById(cId)
                        .filter(course -> !course.isDeleted() && course.isActive())
                        .orElse(null);
                if (c != null) {
                    resolvedInterestedCourses.add(c);
                }
            }
        }

        // 3. Resolve Explicit Programs
        Set<Program> explicitPrograms = new HashSet<>();
        String programSource = null;
        if (explicitProgramIds != null && !explicitProgramIds.isEmpty()) {
            for (UUID pId : explicitProgramIds) {
                if (pId == null) continue;
                Program prog = programRepository.findById(pId)
                        .filter(p -> !p.isDeleted())
                        .orElseThrow(() -> new ResourceNotFoundException("Program not found with ID: " + pId));
                if (!prog.isActive()) {
                    throw new BadRequestException("Cannot select inactive Program: " + prog.getName());
                }
                explicitPrograms.add(prog);
            }
            if (!explicitPrograms.isEmpty()) {
                programSource = "USER";
            }
        }

        // 4. Validate Course <-> Program or Auto-Resolve Program from Course
        Set<Program> finalPrograms = new HashSet<>();
        if (!explicitPrograms.isEmpty()) {
            // Explicit programs provided: validate course belongs to at least one selected program
            if (resolvedCourse != null) {
                boolean mapped = isCourseMappedToAnyProgram(explicitPrograms, resolvedCourse);
                if (!mapped) {
                    String progNames = explicitPrograms.stream().map(Program::getName).collect(Collectors.joining(", "));
                    throw new BadRequestException("COURSE_PROGRAM_MISMATCH: Course '" + resolvedCourse.getCourseName()
                            + "' is not mapped to selected Program(s) [" + progNames + "].");
                }
            }
            finalPrograms.addAll(explicitPrograms);
        } else if (resolvedCourse != null) {
            // Course selected directly without Program -> Auto-derive Program from Course
            List<Program> autoMapped = programRepository.findActiveProgramsByCourseId(resolvedCourse.getId());
            if (autoMapped.isEmpty() && resolvedCourse.getPrograms() != null) {
                autoMapped = resolvedCourse.getPrograms().stream()
                        .filter(Program::isActive)
                        .collect(Collectors.toList());
            }
            finalPrograms.addAll(autoMapped);
            if (!finalPrograms.isEmpty()) {
                programSource = "COURSE";
            }
        }

        // 5. Resolve Course Type
        CourseType resolvedCourseType = null;
        String courseTypeSource = null;
        if (explicitCourseTypeId != null) {
            resolvedCourseType = courseTypeRepository.findById(explicitCourseTypeId)
                    .filter(ct -> !ct.isDeleted())
                    .orElseThrow(() -> new ResourceNotFoundException("Course Type not found with ID: " + explicitCourseTypeId));
            courseTypeSource = "USER";

            // Validate consistency if course is also selected
            if (resolvedCourse != null && resolvedCourse.getCourseType() != null) {
                if (!resolvedCourse.getCourseType().getId().equals(resolvedCourseType.getId())) {
                    throw new BadRequestException("COURSE_TYPE_MISMATCH: Course '" + resolvedCourse.getCourseName()
                            + "' belongs to Course Type '" + resolvedCourse.getCourseType().getName()
                            + "', not '" + resolvedCourseType.getName() + "'.");
                }
            }
        } else if (resolvedCourse != null && resolvedCourse.getCourseType() != null) {
            // Auto-resolve Course Type from canonical Course relation
            resolvedCourseType = resolvedCourse.getCourseType();
            courseTypeSource = "COURSE";
        }

        Program primaryProgram = finalPrograms.isEmpty() ? null : finalPrograms.iterator().next();

        return LeadAcademicResolutionResult.builder()
                .course(resolvedCourse)
                .interestedCourses(resolvedInterestedCourses)
                .programs(finalPrograms)
                .program(primaryProgram)
                .courseType(resolvedCourseType)
                .courseSource(courseSource)
                .programSource(programSource)
                .courseTypeSource(courseTypeSource)
                .build();
    }

    /**
     * Resolves academic mappings for Bulk Lead Upload (supporting row context, raw strings, and structured logging).
     */
    public LeadAcademicResolutionResult resolveLeadAcademicMappingsForUpload(
            String rawCourseInput,
            UUID defaultCourseId,
            String rawProgramInput,
            UUID defaultProgramId,
            String rawCourseTypeInput,
            UUID defaultCourseTypeId,
            String importId,
            int rowNumber
    ) throws BadRequestException {

        // -------------------------------------------------------------
        // 1. Course Resolution
        // -------------------------------------------------------------
        Course resolvedCourse = null;
        String courseSource = null;

        if (rawCourseInput != null && !rawCourseInput.isBlank()) {
            String cleanCourse = rawCourseInput.trim();
            resolvedCourse = courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse(cleanCourse)
                    .or(() -> courseRepository.findByCourseCodeIgnoreCaseAndIsDeletedFalse(cleanCourse))
                    .filter(Course::isActive)
                    .orElse(null);

            if (resolvedCourse == null) {
                log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=interestedCourse\nrawValue={}\nerrorCode=COURSE_NOT_FOUND\nmessage=Course '{}' was not found or is inactive",
                        importId, rowNumber, cleanCourse, cleanCourse);
                throw new BadRequestException("COURSE_NOT_FOUND: Course '" + cleanCourse + "' was not found or is inactive");
            }

            courseSource = "EXCEL";
            log.info("COURSE_RESOLUTION\nrow={}\nrawValue={}\nnormalizedValue={}\nresult=FOUND\ncourseId={}\ncourseName={}",
                    rowNumber, rawCourseInput, cleanCourse, resolvedCourse.getId(), resolvedCourse.getCourseName());

        } else if (defaultCourseId != null) {
            resolvedCourse = courseRepository.findById(defaultCourseId)
                    .filter(c -> !c.isDeleted() && c.isActive())
                    .orElse(null);
            if (resolvedCourse != null) {
                courseSource = "USER";
            }
        }

        // -------------------------------------------------------------
        // 2. Program Resolution (Explicit from Excel or User Filter)
        // -------------------------------------------------------------
        Set<Program> explicitPrograms = new HashSet<>();
        String programSource = null;

        if (rawProgramInput != null && !rawProgramInput.isBlank()) {
            String[] tokens = rawProgramInput.split(",");
            for (String tok : tokens) {
                String cleanProg = tok.trim();
                if (cleanProg.isEmpty()) continue;

                Program prog = programRepository.findByNameIgnoreCaseAndIsDeletedFalse(cleanProg)
                        .or(() -> programRepository.findByCodeIgnoreCaseAndIsDeletedFalse(cleanProg))
                        .filter(Program::isActive)
                        .orElse(null);

                if (prog == null) {
                    log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=program\nrawValue={}\nerrorCode=PROGRAM_NOT_FOUND\nmessage=Program '{}' was not found or is inactive",
                            importId, rowNumber, cleanProg, cleanProg);
                    throw new BadRequestException("PROGRAM_NOT_FOUND: Program '" + cleanProg + "' was not found or is inactive");
                }

                explicitPrograms.add(prog);
                log.info("PROGRAM_RESOLUTION\nrow={}\nrawValue={}\nresult=FOUND\nprogramId={}\nprogramName={}",
                        rowNumber, cleanProg, prog.getId(), prog.getName());
            }
            if (!explicitPrograms.isEmpty()) {
                programSource = "EXCEL";
            }
        } else if (defaultProgramId != null) {
            Program prog = programRepository.findById(defaultProgramId)
                    .filter(p -> !p.isDeleted() && p.isActive())
                    .orElse(null);
            if (prog != null) {
                explicitPrograms.add(prog);
                programSource = "USER";
            }
        }

        // -------------------------------------------------------------
        // 3. Course <-> Program Validation & Automatic Derivation
        // -------------------------------------------------------------
        Set<Program> finalPrograms = new HashSet<>();

        if (!explicitPrograms.isEmpty()) {
            // User/Excel explicitly provided Program(s)
            if (resolvedCourse != null) {
                boolean isMapped = isCourseMappedToAnyProgram(explicitPrograms, resolvedCourse);
                if (!isMapped) {
                    String progNames = explicitPrograms.stream().map(Program::getName).collect(Collectors.joining(", "));
                    log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\ncourse={}\nprogram={}\nerrorCode=COURSE_PROGRAM_MISMATCH\nmessage=Course '{}' is not mapped to Program(s) [{}]",
                            importId, rowNumber, resolvedCourse.getCourseName(), progNames, resolvedCourse.getCourseName(), progNames);
                    throw new BadRequestException("COURSE_PROGRAM_MISMATCH: Course '" + resolvedCourse.getCourseName()
                            + "' is not mapped to Program(s) [" + progNames + "]");
                }
            }
            finalPrograms.addAll(explicitPrograms);

        } else if (resolvedCourse != null) {
            // Program is blank, but Course is present -> Auto-derive Program from Course
            List<Program> autoMapped = programRepository.findActiveProgramsByCourseId(resolvedCourse.getId());
            if (autoMapped.isEmpty() && resolvedCourse.getPrograms() != null) {
                autoMapped = resolvedCourse.getPrograms().stream()
                        .filter(Program::isActive)
                        .collect(Collectors.toList());
            }
            finalPrograms.addAll(autoMapped);

            if (!finalPrograms.isEmpty()) {
                programSource = "COURSE";
                String progNames = finalPrograms.stream().map(Program::getName).collect(Collectors.joining(", "));
                log.info("PROGRAM_AUTO_RESOLUTION\nrow={}\nsource=COURSE\ncourse={}\nprogram={}\nresult=SUCCESS",
                        rowNumber, resolvedCourse.getCourseName(), progNames);
            }
        }

        // -------------------------------------------------------------
        // 4. Course Type Resolution (Independent or Auto-derived from Course)
        // -------------------------------------------------------------
        CourseType resolvedCourseType = null;
        String courseTypeSource = null;

        if (rawCourseTypeInput != null && !rawCourseTypeInput.isBlank()) {
            String cleanCt = rawCourseTypeInput.trim();
            resolvedCourseType = courseTypeRepository.findByNameIgnoreCaseAndIsDeletedFalse(cleanCt)
                    .or(() -> courseTypeRepository.findByNameIgnoreCase(cleanCt).filter(ct -> !ct.isDeleted()))
                    .orElse(null);

            if (resolvedCourseType == null) {
                log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=courseType\nrawValue={}\nerrorCode=COURSE_TYPE_NOT_FOUND\nmessage=Course Type '{}' was not found",
                        importId, rowNumber, cleanCt, cleanCt);
                throw new BadRequestException("COURSE_TYPE_NOT_FOUND: Course Type '" + cleanCt + "' was not found");
            }

            courseTypeSource = "EXCEL";
            log.info("COURSE_TYPE_RESOLUTION\nrow={}\nrawValue={}\nresult=FOUND\ncourseTypeId={}\ncourseTypeName={}",
                    rowNumber, cleanCt, resolvedCourseType.getId(), resolvedCourseType.getName());

            // Validate against Course if Course has a canonical CourseType
            if (resolvedCourse != null && resolvedCourse.getCourseType() != null) {
                if (!resolvedCourse.getCourseType().getId().equals(resolvedCourseType.getId())) {
                    log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\ncourse={}\ncourseType={}\nerrorCode=COURSE_TYPE_MISMATCH",
                            importId, rowNumber, resolvedCourse.getCourseName(), cleanCt);
                    throw new BadRequestException("COURSE_TYPE_MISMATCH: Course '" + resolvedCourse.getCourseName()
                            + "' belongs to Course Type '" + resolvedCourse.getCourseType().getName()
                            + "', but '" + cleanCt + "' was specified");
                }
            }

        } else if (defaultCourseTypeId != null) {
            resolvedCourseType = courseTypeRepository.findById(defaultCourseTypeId)
                    .filter(ct -> !ct.isDeleted())
                    .orElse(null);
            if (resolvedCourseType != null) {
                courseTypeSource = "USER";
                if (resolvedCourse != null && resolvedCourse.getCourseType() != null) {
                    if (!resolvedCourse.getCourseType().getId().equals(resolvedCourseType.getId())) {
                        throw new BadRequestException("COURSE_TYPE_MISMATCH: Course '" + resolvedCourse.getCourseName()
                                + "' belongs to Course Type '" + resolvedCourse.getCourseType().getName()
                                + "', but '" + resolvedCourseType.getName() + "' was selected");
                    }
                }
            }

        } else if (resolvedCourse != null && resolvedCourse.getCourseType() != null) {
            // Auto-resolve Course Type from Course (canonical relationship)
            resolvedCourseType = resolvedCourse.getCourseType();
            courseTypeSource = "COURSE";
            log.info("COURSE_TYPE_AUTO_RESOLUTION\nrow={}\nsource=COURSE\ncourse={}\ncourseType={}\nresult=SUCCESS",
                    rowNumber, resolvedCourse.getCourseName(), resolvedCourseType.getName());
        }

        // Interested courses set contains primary course if resolved
        Set<Course> interestedCourses = new HashSet<>();
        if (resolvedCourse != null) {
            interestedCourses.add(resolvedCourse);
        }

        Program primaryProg = finalPrograms.isEmpty() ? null : finalPrograms.iterator().next();

        return LeadAcademicResolutionResult.builder()
                .course(resolvedCourse)
                .interestedCourses(interestedCourses)
                .programs(finalPrograms)
                .program(primaryProg)
                .courseType(resolvedCourseType)
                .courseSource(courseSource)
                .programSource(programSource)
                .courseTypeSource(courseTypeSource)
                .build();
    }

    public boolean isCourseMappedToAnyProgram(Collection<Program> programs, Course course) {
        if (programs == null || programs.isEmpty() || course == null) {
            return true;
        }
        Set<UUID> progIds = programs.stream().map(Program::getId).collect(Collectors.toSet());
        return programRepository.isCourseMappedToAnyProgram(progIds, course.getId());
    }
}
