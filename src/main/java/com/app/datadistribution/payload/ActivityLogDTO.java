package com.app.datadistribution.payload;

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
    private String time;           // human-readable: "10 mins ago", "2 days ago"
    private LocalDateTime createdAt; // for sorting if needed
}