package com.app.datadistribution.dto.dashboard;

import java.util.UUID;
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
public class UserDashboardPreferenceDTO {
    private UUID cardId;
    private String cardCode;
    private String cardName;
    private String section;
    private boolean visible;
    private int displayOrder;
}
