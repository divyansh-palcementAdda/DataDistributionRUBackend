package com.app.datadistribution.entity;

import java.util.HashSet;
import java.util.Set;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.Status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing an academic Program (School / Faculty / Institute).
 * Maps Many-to-Many with Course and One-to-Many with Lead.
 */
@Entity
@Table(name = "programs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Program extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "program_courses",
        joinColumns = @JoinColumn(name = "program_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @Builder.Default
    private Set<Course> courses = new HashSet<>();

    @OneToMany(mappedBy = "program", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Lead> leads = new HashSet<>();

    public boolean isActive() {
        return this.status == Status.ACTIVE;
    }
}
