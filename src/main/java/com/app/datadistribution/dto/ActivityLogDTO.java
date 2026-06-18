package com.app.datadistribution.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogDTO {
    private String icon;
    private String iconBg;
    private String title;
    private String desc;
    private String by;
    private String time;
    private LocalDateTime createdAt;
}
