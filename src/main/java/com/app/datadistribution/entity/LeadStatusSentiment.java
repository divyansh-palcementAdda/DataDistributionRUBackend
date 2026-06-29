package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.LeadStatus;
import com.app.datadistribution.enums.SentimentCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lead_status_sentiments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadStatusSentiment extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "lead_status", nullable = false, unique = true, length = 50)
    private LeadStatus leadStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment_category", nullable = false, length = 20)
    private SentimentCategory sentimentCategory;
}
