package com.app.datadistribution.dto.dashboard;

import java.util.List;
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
public class DashboardCardDTO {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private String section;
    private String cardType;
    private String icon;
    private int displayOrder;
    private boolean visible;
    private Object value;
    private List<GroupCountDTO> groupData;
}
