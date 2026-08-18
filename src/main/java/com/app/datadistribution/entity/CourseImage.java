package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "course_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Column(name = "storage_key", length = 255)
    private String storageKey;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
