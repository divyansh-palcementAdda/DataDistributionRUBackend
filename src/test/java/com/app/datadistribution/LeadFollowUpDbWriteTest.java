package com.app.datadistribution;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;

@SpringBootTest
public class LeadFollowUpDbWriteTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LeadFollowUpRepository leadFollowUpRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testJdbcAndHibernateWrite() throws Exception {
        User user = userRepository.findByUsername("admin").orElseGet(() ->
                userRepository.save(User.builder().username("testuser_" + UUID.randomUUID()).build())
        );

        Lead lead = leadRepository.save(Lead.builder()
                .leadCode("TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .fullName("Test Lead")
                .phoneNumber("9999999999")
                .assignedTo(user)
                .build());

        LocalDateTime targetDate1 = LocalDateTime.of(2026, 9, 9, 0, 0, 0);
        LeadFollowUp f1 = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(targetDate1)
                .status(FollowUpStatus.PENDING)
                .completed(false)
                .createdByUser(user)
                .remarks("Test midnight")
                .build();

        LeadFollowUp saved1 = leadFollowUpRepository.save(f1);

        LocalDateTime targetDate2 = LocalDateTime.of(2026, 9, 9, 11, 30, 0);
        LeadFollowUp f2 = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(targetDate2)
                .status(FollowUpStatus.UPCOMING)
                .completed(false)
                .createdByUser(user)
                .remarks("Test 11:30")
                .build();

        LeadFollowUp saved2 = leadFollowUpRepository.save(f2);

        System.out.println("=== HIBERNATE SAVED VALUES IN MEMORY ===");
        System.out.println("saved1 Java LocalDateTime: " + saved1.getFollowUpDate());
        System.out.println("saved2 Java LocalDateTime: " + saved2.getFollowUpDate());

        try (Connection conn = dataSource.getConnection()) {
            System.out.println("=== RAW MYSQL VALUES VIA JDBC (DATE_FORMAT) ===");
            try (PreparedStatement ps = conn.prepareStatement("SELECT DATE_FORMAT(follow_up_date, '%Y-%m-%d %H:%i:%s.%f'), follow_up_date, remarks FROM lead_follow_ups ORDER BY created_at DESC LIMIT 2")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("RAW DB DATE_FORMAT: " + rs.getString(1) + " | getObject: " + rs.getObject(2) + " | remarks: " + rs.getString(3));
                    }
                }
            }
        }

        LeadFollowUp fetched1 = leadFollowUpRepository.findById(saved1.getId()).orElseThrow();
        LeadFollowUp fetched2 = leadFollowUpRepository.findById(saved2.getId()).orElseThrow();
        System.out.println("=== HIBERNATE READ BACK VALUES ===");
        System.out.println("fetched1 Java LocalDateTime: " + fetched1.getFollowUpDate());
        System.out.println("fetched2 Java LocalDateTime: " + fetched2.getFollowUpDate());
    }
}

