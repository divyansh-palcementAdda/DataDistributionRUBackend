package com.app.datadistribution.config;

import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.repository.PermissionRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.LeadStatusSentimentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

	private final PermissionRepository permissionRepository;
	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final LeadStatusSentimentRepository leadStatusSentimentRepository;
	private final LeadStatusRepository leadStatusRepository;
	private final com.app.datadistribution.repository.BoardRepository boardRepository;
	private final com.app.datadistribution.repository.GradeRepository gradeRepository;
	private final com.app.datadistribution.repository.DashboardCardRepository dashboardCardRepository;
	private final com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;
	private final com.app.datadistribution.repository.LeadSourceRepository leadSourceRepository;
	private final com.app.datadistribution.entity.LeadSource leadSourceEntityHelper = null;
	private final jakarta.persistence.EntityManager entityManager;

	@Override
	@Transactional
	public void run(String... args) {
		log.info("Database seeding started...");
		seedPermissions();
		seedRoles();
		seedUsers();
		seedLeadStatuses();
		seedLeadSources();
		seedBoards();
		seedGrades();
		seedDashboardCards();
		log.info("Database seeding completed successfully!");
	}

	private void seedPermissions() {
		for (PermissionType type : PermissionType.values()) {
			String name = type.name();
			if (!permissionRepository.findByName(name).isPresent()) {
				Permission permission = Permission.builder().name(name).description("Dynamic permission for " + name)
						.build();
				permissionRepository.save(permission);
				log.info("Seeded permission: {}", name);
			}
		}
	}

	private void seedRoles() {
		Set<Permission> allPermissions = new HashSet<>(permissionRepository.findAll());

		// 1. SUPER_ADMIN Role
		createRoleIfNotExist(RoleType.SUPER_ADMIN.name(), "Super Administrator Role", allPermissions);

		// 2. ADMIN Role
		Set<Permission> adminPermissions = allPermissions.stream()
				.filter(p -> p.getName().equals(PermissionType.USER_CREATE.name())
						|| p.getName().equals(PermissionType.USER_READ.name())
						|| p.getName().equals(PermissionType.USER_UPDATE.name())
						|| p.getName().equals(PermissionType.AUTH_READ.name())
						|| p.getName().startsWith("LEAD_")
						|| p.getName().startsWith("LEADSOURCE_")
						|| p.getName().startsWith("BOARD_")
						|| p.getName().startsWith("GRADE_")
						|| p.getName().startsWith("DASHBOARD_")
						|| p.getName().equals(PermissionType.COURSE_VIEW.name())
						|| p.getName().equals(PermissionType.COURSE_TYPE_VIEW.name())
						|| p.getName().startsWith("FOLLOWUP_")
						|| p.getName().startsWith("FEEDBACK_"))
				.collect(Collectors.toSet());
		createRoleIfNotExist(RoleType.ADMIN.name(), "Administrator Role", adminPermissions);

		// 3. USER Role
		Set<Permission> userPermissions = allPermissions.stream()
				.filter(p -> p.getName().equals(PermissionType.USER_READ.name())
						|| p.getName().startsWith("FOLLOWUP_")
						|| p.getName().startsWith("FEEDBACK_")
						|| p.getName().equals(PermissionType.DASHBOARD_VIEW.name())
						|| p.getName().equals(PermissionType.DASHBOARD_CARD_VIEW.name())
						|| p.getName().equals(PermissionType.DASHBOARD_CARD_PREFERENCE_UPDATE.name())
						|| p.getName().equals(PermissionType.DASHBOARD_CARD_ORDER_UPDATE.name()))
				.collect(Collectors.toSet());
		createRoleIfNotExist(RoleType.USER.name(), "Standard User Role", userPermissions);
	}

	private void createRoleIfNotExist(String name, String description, Set<Permission> permissions) {
		if (!roleRepository.findByName(name).isPresent()) {
			Role role = Role.builder().name(name).description(description).active(true).permissions(permissions)
					.build();
			roleRepository.save(role);
			log.info("Seeded role: {}", name);
		} else {
			// Update permissions to match code config
			Role role = roleRepository.findByName(name).get();
			role.setPermissions(permissions);
			roleRepository.save(role);
		}
	}

	private void seedUsers() {
		// Seed default Super Admin
		if (!userRepository.findByUsername("superadmin").isPresent()) {
			Role superAdminRole = roleRepository.findByName(RoleType.SUPER_ADMIN.name())
					.orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not seeded"));

			User superAdmin = User.builder().firstName("Super").lastName("Admin")
					.email("superadmin@datadistribution.com").phone("+919999999999").username("superadmin")
					.password(passwordEncoder.encode("Admin@123")).active(true).locked(false).emailVerified(true)

					.roles(new HashSet<>(Arrays.asList(superAdminRole))).build();
			userRepository.save(superAdmin);
			log.info("Seeded default superadmin user");
		}

		// Seed default Admin
		if (!userRepository.findByUsername("admin").isPresent()) {
			Role adminRole = roleRepository.findByName(RoleType.ADMIN.name())
					.orElseThrow(() -> new RuntimeException("ADMIN role not seeded"));

			User admin = User.builder().firstName("Standard").lastName("Admin").email("admin@datadistribution.com")
					.phone("+918888888888").username("admin").password(passwordEncoder.encode("Admin@123")).active(true)
					.locked(false).emailVerified(true)

					.roles(new HashSet<>(Arrays.asList(adminRole))).build();
			userRepository.save(admin);
			log.info("Seeded default admin user");
		}

		// Seed default User
		if (!userRepository.findByUsername("user").isPresent()) {
			Role userRole = roleRepository.findByName(RoleType.USER.name())
					.orElseThrow(() -> new RuntimeException("USER role not seeded"));

			User user = User.builder().firstName("Standard").lastName("User").email("user@datadistribution.com")
					.phone("+917777777777").username("user").password(passwordEncoder.encode("User@123")).active(true)
					.locked(false).emailVerified(true)

					.roles(new HashSet<>(Arrays.asList(userRole))).build();
			userRepository.save(user);
			log.info("Seeded default standard user");
		}
	}

	private void seedLeadStatuses() {
		createStatusIfNotExist("Raw", "RAW", "Newly received unverified lead", 1, SentimentCategory.NEUTRAL);
		createStatusIfNotExist("Connected", "CONNECTED", "Contact established with lead", 2, SentimentCategory.POSITIVE);
		createStatusIfNotExist("Not Connected", "NOT_CONNECTED", "Unable to establish contact", 3, SentimentCategory.NEGATIVE);
		createStatusIfNotExist("Hot Lead", "HOT_LEAD", "High intent lead ready for conversion", 4, SentimentCategory.POSITIVE);
		createStatusIfNotExist("Cold Lead", "COLD_LEAD", "Low engagement or inactive lead", 5, SentimentCategory.NEGATIVE);
		createStatusIfNotExist("Bad Lead", "BAD_LEAD", "Invalid contact or unreachable lead", 6, SentimentCategory.NEGATIVE);
		createStatusIfNotExist("Interested Lead", "INTERESTED_LEAD", "Expressing interest in course/service", 7, SentimentCategory.POSITIVE);
		createStatusIfNotExist("Not Interested", "NOT_INTERESTED", "Lead explicitly declined offer", 8, SentimentCategory.NEGATIVE);
		createStatusIfNotExist("Not Registered Yet", "NOT_REGISTERED_YET", "Pending registration details", 9, SentimentCategory.NEUTRAL);

		migrateExistingLeadStatusData();
	}

	private void createStatusIfNotExist(String name, String code, String description, int displayOrder, SentimentCategory sentimentCategory) {
		if (!leadStatusRepository.findByCodeIgnoreCase(code).isPresent()) {
			LeadStatus status = LeadStatus.builder()
					.name(name)
					.code(code)
					.description(description)
					.displayOrder(displayOrder)
					.sentimentCategory(sentimentCategory)
					.active(true)
					.build();
			leadStatusRepository.save(status);
			log.info("Seeded default lead status: {} ({})", name, code);
		}
	}

	private void migrateExistingLeadStatusData() {
		if (columnExists("leads", "current_status")) {
			try {
				entityManager.createNativeQuery(
					"UPDATE leads l " +
					"JOIN lead_statuses ls ON LOWER(l.current_status) = LOWER(ls.code) " +
					"SET l.lead_status_id = ls.id " +
					"WHERE l.lead_status_id IS NULL AND l.current_status IS NOT NULL"
				).executeUpdate();
			} catch (Exception e) {
				log.debug("Native migration for leads.lead_status_id skipped or completed: {}", e.getMessage());
			}
		}

		if (columnExists("lead_status_histories", "old_status")) {
			try {
				entityManager.createNativeQuery(
					"UPDATE lead_status_histories h " +
					"JOIN lead_statuses ls ON LOWER(h.old_status) = LOWER(ls.code) " +
					"SET h.previous_status_id = ls.id " +
					"WHERE h.previous_status_id IS NULL AND h.old_status IS NOT NULL"
				).executeUpdate();
			} catch (Exception e) {
				log.debug("Native migration for lead_status_histories.previous_status_id skipped or completed: {}", e.getMessage());
			}
		}

		if (columnExists("lead_status_histories", "new_status")) {
			try {
				entityManager.createNativeQuery(
					"UPDATE lead_status_histories h " +
					"JOIN lead_statuses ls ON LOWER(h.new_status) = LOWER(ls.code) " +
					"SET h.new_status_id = ls.id " +
					"WHERE h.new_status_id IS NULL AND h.new_status IS NOT NULL"
				).executeUpdate();
			} catch (Exception e) {
				log.debug("Native migration for lead_status_histories.new_status_id skipped or completed: {}", e.getMessage());
			}
		}

		if (columnExists("lead_feedbacks", "status_at_time")) {
			try {
				entityManager.createNativeQuery(
					"UPDATE lead_feedbacks f " +
					"JOIN lead_statuses ls ON LOWER(f.status_at_time) = LOWER(ls.code) " +
					"SET f.status_at_time_id = ls.id " +
					"WHERE f.status_at_time_id IS NULL AND f.status_at_time IS NOT NULL"
				).executeUpdate();
			} catch (Exception e) {
				log.debug("Native migration for lead_feedbacks.status_at_time_id skipped or completed: {}", e.getMessage());
			}
		}

		// Fallback: Assign default 'RAW' status ID to any remaining null status foreign keys
		try {
			leadStatusRepository.findByCodeIgnoreCase("RAW").ifPresent(rawStatus -> {
				UUID defaultId = rawStatus.getId();
				entityManager.createNativeQuery("UPDATE leads SET lead_status_id = :defaultId WHERE lead_status_id IS NULL")
						.setParameter("defaultId", defaultId)
						.executeUpdate();
				entityManager.createNativeQuery("UPDATE lead_status_histories SET new_status_id = :defaultId WHERE new_status_id IS NULL")
						.setParameter("defaultId", defaultId)
						.executeUpdate();
				entityManager.createNativeQuery("UPDATE lead_feedbacks SET status_at_time_id = :defaultId WHERE status_at_time_id IS NULL")
						.setParameter("defaultId", defaultId)
						.executeUpdate();
			});
		} catch (Exception e) {
			log.debug("Fallback migration for default status ID skipped or completed: {}", e.getMessage());
		}
	}

	private void seedLeadSources() {
		List<com.app.datadistribution.entity.LeadSource> sources = leadSourceRepository.findAll();
		for (com.app.datadistribution.entity.LeadSource source : sources) {
			if (source.getCode() == null || source.getCode().isBlank()) {
				String baseCode = source.getName().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
				if (baseCode.length() > 20) baseCode = baseCode.substring(0, 20);
				if (baseCode.isBlank()) baseCode = "SRC";
				String candidate = baseCode;
				if (leadSourceRepository.existsByCodeIgnoreCaseAndIdNot(candidate, source.getId())) {
					candidate = baseCode + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
				}
				source.setCode(candidate);
				leadSourceRepository.save(source);
				log.info("Assigned code {} to lead source {}", candidate, source.getName());
			}
		}

		if (leadSourceRepository.count() == 0) {
			createSourceIfNotExist("Meta Ads", "META_ADS", "Meta / Facebook / Instagram Advertising");
			createSourceIfNotExist("Google Search", "GOOGLE_SEARCH", "Google Ads and Organic Search");
			createSourceIfNotExist("Website Form", "WEBSITE_FORM", "Direct website inquiry forms");
			createSourceIfNotExist("Referral", "REFERRAL", "Customer and employee referrals");
			createSourceIfNotExist("Walk In", "WALK_IN", "Direct campus or office walk-in");
		}

		migrateExistingLeadSourcesJoin();
	}

	private void createSourceIfNotExist(String name, String code, String description) {
		if (!leadSourceRepository.findByNameIgnoreCase(name).isPresent()) {
			com.app.datadistribution.entity.LeadSource source = com.app.datadistribution.entity.LeadSource.builder()
					.name(name)
					.code(code)
					.description(description)
					.active(true)
					.build();
			leadSourceRepository.save(source);
			log.info("Seeded default lead source: {} ({})", name, code);
		}
	}

	private void migrateExistingLeadSourcesJoin() {
		if (columnExists("leads", "source_id")) {
			try {
				entityManager.createNativeQuery(
					"INSERT IGNORE INTO lead_lead_sources (lead_id, lead_source_id) " +
					"SELECT id, source_id FROM leads WHERE source_id IS NOT NULL"
				).executeUpdate();
			} catch (Exception e) {
				log.debug("Native migration for lead_lead_sources skipped or completed: {}", e.getMessage());
			}
		}
	}

	private boolean columnExists(String tableName, String columnName) {
		try {
			Object result = entityManager.createNativeQuery(
				"SELECT COUNT(*) FROM information_schema.COLUMNS " +
				"WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName AND COLUMN_NAME = :columnName"
			)
			.setParameter("tableName", tableName)
			.setParameter("columnName", columnName)
			.getSingleResult();
			return result != null && ((Number) result).longValue() > 0;
		} catch (Exception e) {
			return false;
		}
	}

	private void seedBoards() {
		createBoardIfNotExist("CBSE", "CBSE", "Central Board of Secondary Education", 1);
		createBoardIfNotExist("ICSE", "ICSE", "Indian Certificate of Secondary Education", 2);
		createBoardIfNotExist("State Board", "STATE_BOARD", "State Board of Education", 3);
		createBoardIfNotExist("IB", "IB", "International Baccalaureate", 4);
		createBoardIfNotExist("IGCSE", "IGCSE", "International General Certificate of Secondary Education", 5);
	}

	private void createBoardIfNotExist(String name, String code, String description, int displayOrder) {
		if (!boardRepository.findByCodeIgnoreCase(code).isPresent()) {
			com.app.datadistribution.entity.Board board = com.app.datadistribution.entity.Board.builder()
					.name(name)
					.code(code)
					.description(description)
					.displayOrder(displayOrder)
					.active(true)
					.build();
			boardRepository.save(board);
			log.info("Seeded default board: {} ({})", name, code);
		}
	}

	private void seedGrades() {
		createGradeIfNotExist("Grade A Data", "GRADE_A", "Grade A high-quality lead data", 1);
		createGradeIfNotExist("Grade B Data", "GRADE_B", "Grade B standard lead data", 2);
		createGradeIfNotExist("Grade C Data", "GRADE_C", "Grade C average lead data", 3);
		createGradeIfNotExist("Grade D Data", "GRADE_D", "Grade D low priority lead data", 4);
	}

	private void createGradeIfNotExist(String name, String code, String description, int displayOrder) {
		if (!gradeRepository.findByCodeIgnoreCase(code).isPresent()) {
			com.app.datadistribution.entity.Grade grade = com.app.datadistribution.entity.Grade.builder()
					.name(name)
					.code(code)
					.description(description)
					.displayOrder(displayOrder)
					.active(true)
					.build();
			gradeRepository.save(grade);
			log.info("Seeded default grade: {} ({})", name, code);
		}
	}

	private void seedDashboardCards() {
		Set<com.app.datadistribution.entity.Role> allRoles = new HashSet<>(roleRepository.findAll());
		createCardIfNotExist("TOTAL_LEADS", "Total Leads", "Total leads in scope", "SUMMARY", "METRIC", "trending_up", 1, allRoles);
		createCardIfNotExist("TOTAL_FOLLOWUPS_TODAY", "Follow-Ups Scheduled Today", "Follow-ups scheduled today", "SUMMARY", "METRIC", "calendar_today", 2, allRoles);
		createCardIfNotExist("TOTAL_COUNSELLORS_LOGGED_TODAY", "Counsellors Logged Today", "Total counsellors logged in today", "SUMMARY", "METRIC", "people", 3, allRoles);
		createCardIfNotExist("TOTAL_COUNSELLORS_WORKING", "Counsellors Currently Working", "Active counsellors working in last 8 hours", "SUMMARY", "METRIC", "badge", 4, allRoles);
		createCardIfNotExist("CONVERSATION_RATIO", "Conversation Ratio", "Feedback count to total lead ratio", "SUMMARY", "METRIC", "bar_chart", 5, allRoles);
		createCardIfNotExist("LEAD_STATUS_GROUP", "Lead Status Distribution", "Dynamic lead breakdown by status", "LEAD_STATUS", "GROUP_CHART", "pie_chart", 6, allRoles);
		createCardIfNotExist("LEAD_SOURCE_GROUP", "Lead Source Distribution", "Dynamic lead breakdown by source", "LEAD_SOURCE", "GROUP_CHART", "source", 7, allRoles);
		createCardIfNotExist("BOARD_GROUP", "Board Wise Overview", "Dynamic lead breakdown by board", "BOARD", "GROUP_CHART", "school", 8, allRoles);
		createCardIfNotExist("GRADE_GROUP", "Grade Wise Overview", "Dynamic lead breakdown by grade", "GRADE", "GROUP_CHART", "grade", 9, allRoles);
		createCardIfNotExist("COURSE_GROUP", "Course Wise Overview", "Dynamic lead breakdown by course", "COURSE", "GROUP_CHART", "book", 10, allRoles);
		createCardIfNotExist("RECENT_ACTIVITY", "Recent System Activity", "Recent status changes and feedback logs", "ACTIVITY", "LIST", "history", 11, allRoles);
	}

	private void createCardIfNotExist(String code, String name, String description, String section, String cardType, String icon, int displayOrder, Set<com.app.datadistribution.entity.Role> roles) {
		if (!dashboardCardRepository.findByCodeIgnoreCase(code).isPresent()) {
			com.app.datadistribution.entity.DashboardCard card = com.app.datadistribution.entity.DashboardCard.builder()
					.code(code)
					.name(name)
					.description(description)
					.section(section)
					.cardType(cardType)
					.icon(icon)
					.displayOrder(displayOrder)
					.active(true)
					.allowedRoles(roles)
					.build();
			dashboardCardPermissionService.registerCardAndPermission(card);
			log.info("Seeded default dashboard card with unique permission: {} ({})", name, code);
		} else {
			dashboardCardRepository.findByCodeIgnoreCase(code).ifPresent(dashboardCardPermissionService::registerCardAndPermission);
		}
	}
}

