package com.app.datadistribution.dto.dashboard;

import jakarta.validation.constraints.NotNull;
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
public class CardOrderUpdateRequest {

    @NotNull(message = "Card orders list is required")
    private List<CardOrderItem> cardOrders;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardOrderItem {
        @NotNull(message = "Card ID is required")
        private UUID cardId;

        @NotNull(message = "Display order is required")
        private Integer displayOrder;
    }
}
