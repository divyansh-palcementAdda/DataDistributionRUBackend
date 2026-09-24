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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "course_info_panel_competitors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseInfoPanelCompetitor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "info_panel_id", nullable = false)
    private CourseInfoPanel infoPanel;

    @Column(name = "college_name", nullable = false, length = 200)
    private String collegeName;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "competitor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CourseInfoPanelCompetitorBranch> branches = new ArrayList<>();

    @OneToOne(mappedBy = "competitor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CompetitorCourseComparison comparison;
}
