package com.app.datadistribution.payload;


import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ActivityDTO {

    private Long id;
    private String type;
    private String title;
    private String description;
    private String user;
    private LocalDateTime timestamp;
    private String icon;
}
