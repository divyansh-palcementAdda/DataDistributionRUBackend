package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "course_communication_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCommunicationConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, unique = true)
    private Course course;

    // Channel default templates
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "info_panel_template_id")
    private CourseTemplate infoPanelTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_template_id")
    private CourseTemplate emailTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "whatsapp_template_id")
    private CourseTemplate whatsappTemplate;

    // Channel default images
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "info_panel_image_id")
    private CourseImage infoPanelImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_image_id")
    private CourseImage emailImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "whatsapp_image_id")
    private CourseImage whatsappImage;
}
