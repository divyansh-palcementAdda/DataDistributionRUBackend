package com.app.datadistribution.integration.meta.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.datadistribution.integration.meta.config.MetaProperties;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEvent;
import com.app.datadistribution.integration.meta.repository.MetaWebhookEventRepository;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaLeadIngestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetaEventRetryScheduler {

    private final MetaProperties metaProperties;
    private final MetaWebhookEventRepository eventRepository;
    private final IMetaLeadIngestionService leadIngestionService;

    @Scheduled(cron = "${app.meta.retry-cron:0 */2 * * * *}")
    public void retryPendingAndFailedEvents() {
        if (!metaProperties.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<MetaWebhookEvent> retryableEvents = eventRepository.findRetryableEvents(now);

        if (retryableEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending or retryable Meta webhook events to process", retryableEvents.size());

        for (MetaWebhookEvent event : retryableEvents) {
            try {
                leadIngestionService.processEvent(event);
            } catch (Exception e) {
                log.error("Scheduled retry failed for Meta event id: {}, leadgen_id: {}",
                        event.getId(), event.getLeadgenId(), e);
            }
        }
    }
}
