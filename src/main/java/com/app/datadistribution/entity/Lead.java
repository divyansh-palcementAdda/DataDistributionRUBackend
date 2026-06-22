package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.LeadStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leads")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private LeadSource source;

    @Column(name = "source_details", length = 255)
    private String sourceDetails;

    @Column(name = "course_interested", length = 150)
    private String courseInterested;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 50)
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

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LeadStatusHistory> statusHistories = new ArrayList<>();

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LeadAssignmentHistory> assignmentHistories = new ArrayList<>();
}
