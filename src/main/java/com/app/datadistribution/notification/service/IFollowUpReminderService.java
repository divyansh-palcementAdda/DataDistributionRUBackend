package com.app.datadistribution.notification.service;

import java.util.UUID;

import com.app.datadistribution.entity.LeadFollowUp;

public interface IFollowUpReminderService {

    void scheduleRemindersForFollowUp(LeadFollowUp followUp);

    void rescheduleReminders(LeadFollowUp followUp);

    void cancelReminders(UUID followUpId);

    void reassignReminders(LeadFollowUp followUp, UUID newUserId);

    void dispatchDueReminders();
}

