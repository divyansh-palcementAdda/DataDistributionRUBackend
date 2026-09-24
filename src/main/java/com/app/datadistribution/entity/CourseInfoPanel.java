package com.app.datadistribution.entity;

import java.util.ArrayList;
import java.util.List;

import com.app.datadistribution.common.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "course_info_panels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseInfoPanel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "academic_session", nullable = false, length = 50)
    @Builder.Default
    private String academicSession = "2026-27";

    @Column(name = "school", length = 150)
    private String school;

    @Column(name = "course_name", length = 150)
    private String courseName;

    @Column(name = "course_fee", length = 100)
    private String courseFee;

    @Column(name = "duration", length = 100)
    private String duration;

    @Column(name = "eligibility", columnDefinition = "TEXT")
    private String eligibility;

    @Column(name = "job_opportunities", columnDefinition = "TEXT")
    private String jobOpportunities;

    @Column(name = "hostel_fee", length = 100)
    private String hostelFee;

    @Column(name = "course_details", columnDefinition = "TEXT")
    private String courseDetails;

    @Column(name = "course_specialities", columnDefinition = "TEXT")
    private String courseSpecialities;

    @Column(name = "ru_usps", columnDefinition = "TEXT")
    private String renaissanceUniversityUsps;

    @Column(name = "how_we_are_different", columnDefinition = "TEXT")
    private String howWeAreDifferent;

    @Column(name = "caller_guidance", columnDefinition = "TEXT")
    private String callerGuidance;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "infoPanel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<CourseInfoPanelCompetitor> competitors = new ArrayList<>();
}
