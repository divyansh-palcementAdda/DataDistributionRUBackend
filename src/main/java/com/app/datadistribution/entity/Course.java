package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "courses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course extends BaseEntity {

    @Column(name = "course_name", nullable = false, unique = true, length = 150)
    private String courseName;

    @Column(name = "course_code", nullable = false, unique = true, length = 50)
    private String courseCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer duration;

    @Column(name = "duration_unit", nullable = false, length = 50)
    private String durationUnit;

    @Column(nullable = false)
    private Double fees;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_type_id", nullable = false)
    private CourseType courseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Lead> leads = new ArrayList<>();
}
