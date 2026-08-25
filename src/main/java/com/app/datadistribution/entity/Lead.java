package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leads", indexes = {
    @Index(name = "idx_lead_assigned_to", columnList = "assigned_to_id"),
    @Index(name = "idx_lead_department", columnList = "department_id"),
    @Index(name = "idx_lead_status", columnList = "lead_status_id"),
    @Index(name = "idx_lead_is_deleted", columnList = "is_deleted"),
    @Index(name = "idx_lead_created_by", columnList = "created_by_user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lead extends BaseEntity {

    @Column(name = "lead_code", nullable = false, unique = true, length = 50)
    private String leadCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "alternate_phone_number", length = 20)
    private String alternatePhoneNumber;

    @Column(length = 100)
    private String email;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String country;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "lead_lead_sources",
        joinColumns = @JoinColumn(name = "lead_id"),
        inverseJoinColumns = @JoinColumn(name = "lead_source_id")
    )
    @Builder.Default
    private Set<LeadSource> leadSources = new HashSet<>();

    @Column(name = "source_details", length = 255)
    private String sourceDetails;

    @Column(name = "course_interested", length = 150)
    private String courseInterested;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "lead_interested_courses",
        joinColumns = @JoinColumn(name = "lead_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @Builder.Default
    private Set<Course> interestedCourses = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    public Course getRegisteredCourse() {
        return this.course;
    }

    public void setRegisteredCourse(Course registeredCourse) {
        this.course = registeredCourse;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_status_id")
    private LeadStatus currentStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_contacted_at")
    private LocalDateTime lastContactedAt;

    @Column(name = "next_follow_up_date")
    private LocalDateTime nextFollowUpDate;

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LeadFeedback> feedbacks = new ArrayList<>();

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LeadFollowUp> followUps = new ArrayList<>();

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LeadStatusHistory> statusHistories = new ArrayList<>();

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LeadAssignmentHistory> assignmentHistories = new ArrayList<>();

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LeadAvailed> availedRecords = new ArrayList<>();
}
