package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "competitor_course_comparisons")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorCourseComparison extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitor_id", nullable = false, unique = true)
    private CourseInfoPanelCompetitor competitor;

    @Column(name = "course_fee_per_year", length = 100)
    private String courseFeePerYear;

    @Column(name = "duration", length = 100)
    private String duration;

    @Column(name = "odds", columnDefinition = "TEXT")
    private String odds;

    @Column(name = "eligibility", columnDefinition = "TEXT")
    private String eligibility;

    @Column(name = "hostel", length = 100)
    private String hostel;

    @Column(name = "distance_from_city", length = 100)
    private String distanceFromCity;

    @Column(name = "registration_fee", length = 100)
    private String registrationFee;

    @Column(name = "average_placements", length = 100)
    private String averagePlacements;

    @Column(name = "highest_placement", length = 100)
    private String highestPlacement;
}
