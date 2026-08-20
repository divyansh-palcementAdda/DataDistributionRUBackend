package com.app.datadistribution.dto.reassign;

import com.app.datadistribution.enums.FollowUpStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpReassignableDTO {
    private UUID followUpId;
    private UUID leadId;
    private String studentName;
    private String studentPhone;
    private String studentEmail;
    private LocalDateTime followUpDate;
    private FollowUpStatus status;
    private boolean completed;
    private String remarks;

    private UUID currentResponsibleUserId;
    private String currentResponsibleUserName;

    private UUID originalCreatorUserId;
    private String originalCreatorUserName;
}
