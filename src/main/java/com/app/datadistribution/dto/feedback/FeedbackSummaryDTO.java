package com.app.datadistribution.dto.feedback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackSummaryDTO {
    private long totalFeedbacks;
    private long todayFeedbacks;
    private long positiveFeedbacks;
    private long negativeFeedbacks;
}
