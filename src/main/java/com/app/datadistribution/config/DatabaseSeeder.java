package com.app.datadistribution.config;

import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.repository.DepartmentRepository;
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
	
	
//	RAW
//	├── CONNECTED
//	│   ├── INTERESTED
//	│   │   ├── FORM_FOLLOW_UP
//	│   │   │   ├── REGISTERED
//	│   │   │   ├── FORM_NOT_INTERESTED
//	│   │   │   └── CONTINUOUS_FORM_FOLLOW_UP
//	│   │   │
//	│   │   └── COUNSELING_FOLLOW_UP
//	│   │       └── CONTINUOUS_FOLLOW_UP
//	│   │           ├── INTERESTED_FOLLOW_UP
//	│   │           │   └── GO_TO_FORM_FOLLOW_UP
//	│   │           └── COUNSELING_NOT_INTERESTED
//	│   │
//	│   ├── BAD
//	│   └── NOT_INTERESTED
//	│
//	└── NOT_CONNECTED
//	    ├── NOT_CONNECTED_1
//	    ├── NOT_CONNECTED_2
//	    ├── NOT_CONNECTED_3
//	    └── FINALLY_NOT_CONNECTED
//	
//	
//	

	private final PermissionRepository permissionRepository;
	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final DepartmentRepository departmentRepository;
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
		seedDepartments();
		seedUsers();
		seedLeadStatuses();
		seedLeadSources();
		seedBoards();
//		seedGrades();
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
						|| p.getName().startsWith("COURSE_")
						|| p.getName().startsWith("FOLLOWUP_")
						|| p.getName().startsWith("FEEDBACK_")
						|| p.getName().startsWith("DEPARTMENT_"))
				.collect(Collectors.toSet());
		createRoleIfNotExist(RoleType.ADMIN.name(), "Administrator Role", adminPermissions);

		// 3. USER Role
		Set<Permission> userPermissions = allPermissions.stream()
				.filter(p -> p.getName().equals(PermissionType.USER_READ.name())
						|| p.getName().startsWith("FOLLOWUP_")
						|| p.getName().startsWith("FEEDBACK_")
						|| p.getName().equals(PermissionType.DASHBOARD_VIEW.name())
						|| p.getName().equals(PermissionType.DASHBOARD_COURSE_TYPE_VIEW.name())
						|| p.getName().equals(PermissionType.DASHBOARD_CARD_VIEW.name())
						|| p.getName().equals(PermissionType.DASHBOARD_CARD_PREFERENCE_UPDATE.name())
						|| p.getName().equals(PermissionType.DASHBOARD_CARD_ORDER_UPDATE.name())
						|| p.getName().equals(PermissionType.LEAD_INTERESTED_COURSE_UPDATE.name())
						|| p.getName().equals(PermissionType.LEAD_REGISTERED_COURSE_UPDATE.name())
						|| p.getName().equals(PermissionType.COURSE_TEMPLATE_VIEW.name())
						|| p.getName().equals(PermissionType.COURSE_TEMPLATE_SEND.name())
						|| p.getName().equals(PermissionType.COURSE_IMAGE_VIEW.name())
						|| p.getName().equals(PermissionType.COURSE_USP_VIEW.name())
						|| p.getName().equals(PermissionType.COURSE_TEMPLATE_SEND_EMAIL.name())
						|| p.getName().equals(PermissionType.COURSE_TEMPLATE_SEND_WHATSAPP.name())
						|| p.getName().equals(PermissionType.COURSE_TEMPLATE_IMAGE_SELECT.name())
						|| p.getName().equals(PermissionType.DEPARTMENT_VIEW.name()))
				.collect(Collectors.toSet());
		createRoleIfNotExist(RoleType.USER.name(), "Standard User Role", userPermissions);
	}

	private void seedDepartments() {
		createDepartmentIfNotExist("Admissions", "ADM", "Student Admissions & Enrollment Department");
//		createDepartmentIfNotExist("Marketing", "MKT", "Lead Generation & Marketing Department");
//		createDepartmentIfNotExist("Academics", "ACA", "Academic Course Guidance Department");
//		createDepartmentIfNotExist("Finance", "FIN", "Fee Processing & Financial Aid Department");
	}

	private void createDepartmentIfNotExist(String name, String code, String description) {
		if (!departmentRepository.findByCodeIgnoreCaseAndIsDeletedFalse(code).isPresent()) {
			Department dept = Department.builder()
					.name(name)
					.code(code)
					.description(description)
					.active(true)
					.build();
			departmentRepository.save(dept);
			log.info("Seeded default department: {} ({})", name, code);
		}
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

	    // =========================
	    // ROOT STATUS
	    // =========================
	    createStatusIfNotExist(
	            "Raw",
	            "RAW",
	            "New lead received. Define the lead type as Inbound or Outbound.",
	            1,
	            SentimentCategory.NEUTRAL
	    );

	    // =========================
	    // PRIMARY OUTCOMES
	    // =========================
	    createStatusIfNotExist(
	            "Connected",
	            "CONNECTED",
	            "Contact successfully established with the lead.",
	            2,
	            SentimentCategory.POSITIVE
	    );

	    createStatusIfNotExist(
	            "Not Connected",
	            "NOT_CONNECTED",
	            "Unable to establish contact with the lead.",
	            3,
	            SentimentCategory.NEGATIVE
	    );

	    // =========================
	    // CONNECTED FLOW
	    // =========================
	    createStatusIfNotExist(
	            "Interested",
	            "INTERESTED",
	            "Lead has shown interest in the course or service.",
	            4,
	            SentimentCategory.POSITIVE
	    );

	    createStatusIfNotExist(
	            "Bad",
	            "BAD",
	            "Invalid, incorrect, duplicate, or unusable lead.",
	            5,
	            SentimentCategory.NEGATIVE
	    );

	    createStatusIfNotExist(
	            "Not Interested",
	            "NOT_INTERESTED",
	            "Lead is currently not interested.",
	            6,
	            SentimentCategory.NEGATIVE
	    );

	    // =========================
	    // INTERESTED -> FOLLOW-UP FLOW
	    // =========================
	    createStatusIfNotExist(
	            "Form Follow-Up",
	            "FORM_FOLLOW_UP",
	            "Lead is interested and requires follow-up regarding form submission.",
	            7,
	            SentimentCategory.NEUTRAL
	    );

	    createStatusIfNotExist(
	            "Counseling Follow-Up",
	            "COUNSELING_FOLLOW_UP",
	            "Lead requires counseling and further follow-up.",
	            8,
	            SentimentCategory.NEUTRAL
	    );

	    // =========================
	    // FORM FOLLOW-UP OUTCOMES
	    // =========================
	    createStatusIfNotExist(
	            "Registered",
	            "REGISTERED",
	            "Lead has successfully completed registration.",
	            9,
	            SentimentCategory.POSITIVE
	    );

	    createStatusIfNotExist(
	            "Form Not Interested",
	            "FORM_NOT_INTERESTED",
	            "Lead is not interested in completing the registration form.",
	            10,
	            SentimentCategory.NEGATIVE
	    );

	    createStatusIfNotExist(
	            "Continuous Form Follow-Up",
	            "CONTINUOUS_FORM_FOLLOW_UP",
	            "Lead requires continuous follow-up for form completion.",
	            11,
	            SentimentCategory.NEUTRAL
	    );

	    // =========================
	    // COUNSELING FOLLOW-UP FLOW
	    // =========================
	    createStatusIfNotExist(
	            "Continuous Follow-Up",
	            "CONTINUOUS_FOLLOW_UP",
	            "Lead requires continuous counseling follow-up.",
	            12,
	            SentimentCategory.NEUTRAL
	    );

	    createStatusIfNotExist(
	            "Interested Follow-Up",
	            "INTERESTED_FOLLOW_UP",
	            "Interested lead requires additional follow-up.",
	            13,
	            SentimentCategory.POSITIVE
	    );

	    createStatusIfNotExist(
	            "Go To Form Follow-Up",
	            "GO_TO_FORM_FOLLOW_UP",
	            "Move the interested lead to the form follow-up process.",
	            14,
	            SentimentCategory.POSITIVE
	    );

	    createStatusIfNotExist(
	            "Counseling Not Interested",
	            "COUNSELING_NOT_INTERESTED",
	            "Lead became not interested after counseling follow-up.",
	            15,
	            SentimentCategory.NEGATIVE
	    );

	    // =========================
	    // NOT CONNECTED FOLLOW-UP FLOW
	    // =========================
	    createStatusIfNotExist(
	            "Not Connected - 1",
	            "NOT_CONNECTED_1",
	            "First unsuccessful contact attempt.",
	            16,
	            SentimentCategory.NEGATIVE
	    );

	    createStatusIfNotExist(
	            "Not Connected - 2",
	            "NOT_CONNECTED_2",
	            "Second unsuccessful contact attempt.",
	            17,
	            SentimentCategory.NEGATIVE
	    );

	    createStatusIfNotExist(
	            "Not Connected - 3",
	            "NOT_CONNECTED_3",
	            "Third unsuccessful contact attempt.",
	            18,
	            SentimentCategory.NEGATIVE
	    );

	    createStatusIfNotExist(
	            "Finally Not Connected",
	            "FINALLY_NOT_CONNECTED",
	            "All contact attempts have failed and the lead is marked as finally not connected.",
	            19,
	            SentimentCategory.NEGATIVE
	    );

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
	}

	private boolean columnExists(String tableName, String columnName) {
		try {
			Object result = entityManager.createNativeQuery(
				"SELECT COUNT(*) FROM information_schema.columns " +
				"WHERE table_schema = DATABASE() AND table_name = :tableName AND column_name = :columnName"
			)
			.setParameter("tableName", tableName)
			.setParameter("columnName", columnName)
			.getSingleResult();
			return result != null && ((Number) result).longValue() > 0;
		} catch (Exception e) {
			log.debug("Column exists check failed for {}.{}: {}", tableName, columnName, e.getMessage());
			return false;
		}
	}

	private void seedLeadSources() {
		createSourceIfNotExist("Organic Website", "ORGANIC_WEBSITE", "Direct inbound traffic via company website");
		createSourceIfNotExist("Google Ads", "GOOGLE_ADS", "Paid search marketing campaigns on Google");
		createSourceIfNotExist("Meta Ads", "META_ADS", "Facebook and Instagram marketing campaigns");
		createSourceIfNotExist("LinkedIn", "LINKEDIN", "Professional networking outreach and sponsored content");
		createSourceIfNotExist("Referral", "REFERRAL", "Word of mouth or existing client referral");
		createSourceIfNotExist("Cold Call", "COLD_CALL", "Outbound phone sales outreach");

		migrateExistingLeadSourceData();
	}

	private void createSourceIfNotExist(String name, String code, String description) {
		if (!leadSourceRepository.findByCodeIgnoreCase(code).isPresent()) {
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

	private void migrateExistingLeadSourceData() {
		if (columnExists("leads", "source")) {
			try {
				entityManager.createNativeQuery(
					"INSERT IGNORE INTO lead_lead_sources (lead_id, lead_source_id) " +
					"SELECT l.id, ls.id " +
					"FROM leads l " +
					"JOIN lead_sources ls ON LOWER(l.source) = LOWER(ls.code) " +
					"WHERE l.source IS NOT NULL"
				).executeUpdate();
			} catch (Exception e) {
				log.debug("Native migration for lead_sources skipped or completed: {}", e.getMessage());
			}
		}
	}

	private void seedBoards() {
		createBoardIfNotExist("CBSE", "CBSE", "Central Board of Secondary Education");
		createBoardIfNotExist("ICSE", "ICSE", "Indian Certificate of Secondary Education");
		createBoardIfNotExist("State Board", "STATE_BOARD", "Respective State Higher Secondary Education Board");
		createBoardIfNotExist("IB", "IB", "International Baccalaureate Diploma Program");
	}

	private void createBoardIfNotExist(String name, String code, String description) {
		if (!boardRepository.findByCodeIgnoreCase(code).isPresent()) {
			Board board = Board.builder()
					.name(name)
					.code(code)
					.description(description)
					.active(true)
					.build();
			boardRepository.save(board);
			log.info("Seeded default board: {} ({})", name, code);
		}
	}

//	private void seedGrades() {
//		createGradeIfNotExist("Grade 9", "GRADE_9", "9th Grade / High School Freshman", 1);
//		createGradeIfNotExist("Grade 10", "GRADE_10", "10th Grade / Secondary School", 2);
//		createGradeIfNotExist("Grade 11", "GRADE_11", "11th Grade / Higher Secondary 1st Year", 3);
//		createGradeIfNotExist("Grade 12", "GRADE_12", "12th Grade / Higher Secondary Senior", 4);
//		createGradeIfNotExist("Undergraduate", "UG", "Bachelor Degree Aspirant / Student", 5);
//	}

	private void createGradeIfNotExist(String name, String code, String description, int displayOrder) {
		if (!gradeRepository.findByCodeIgnoreCase(code).isPresent()) {
			Grade grade = Grade.builder()
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

		createCardIfNotExist("TOTAL_LEADS", "Total System Leads", "Summary of total recorded active leads", "OVERVIEW", "STAT_CARD", "users", 1, allRoles);
		createCardIfNotExist("NEW_LEADS_TODAY", "Today's New Inbound Leads", "Count of leads created today", "OVERVIEW", "STAT_CARD", "user-plus", 2, allRoles);
		createCardIfNotExist("HOT_LEADS_COUNT", "Hot Leads Count", "Leads with HOT_LEAD status", "OVERVIEW", "STAT_CARD", "flame", 3, allRoles);
		createCardIfNotExist("CONVERSION_RATE", "Overall Lead Conversion Rate", "Percentage of registered leads against total", "OVERVIEW", "STAT_CARD", "trending-up", 4, allRoles);

		createCardIfNotExist("LEAD_STATUS_DISTRIBUTION", "Lead Status Breakdown", "Distribution of leads by current status", "ANALYTICS", "PIE_CHART", "pie-chart", 5, allRoles);
		createCardIfNotExist("LEAD_SOURCE_DISTRIBUTION", "Lead Source Breakdown", "Distribution of leads by inbound channel", "ANALYTICS", "BAR_CHART", "bar-chart-2", 6, allRoles);
		createCardIfNotExist("COURSE_INTEREST_BREAKDOWN", "Course Interest Breakdown", "Leads grouped by interested courses", "ANALYTICS", "DONUT_CHART", "disc", 7, allRoles);
		createCardIfNotExist("BOARD_GRADE_DISTRIBUTION", "Board & Grade Breakdown", "Leads grouped by education board & grade", "ANALYTICS", "BAR_CHART", "layers", 8, allRoles);

		createCardIfNotExist("PENDING_FOLLOW_UPS", "Pending Follow-Ups", "Actionable pending follow-up schedules", "OPERATIONS", "LIST", "calendar", 9, allRoles);
		createCardIfNotExist("TODAY_FOLLOW_UPS", "Today's Scheduled Follow-Ups", "Follow-ups due for contact today", "OPERATIONS", "LIST", "clock", 10, allRoles);
		createCardIfNotExist("OVERDUE_FOLLOW_UPS", "Overdue Follow-Ups Alert", "Follow-ups past their scheduled time", "OPERATIONS", "LIST", "alert-circle", 11, allRoles);
		createCardIfNotExist("RECENT_ACTIVITY", "Recent System Activity", "Recent status changes and feedback logs", "ACTIVITY", "LIST", "history", 12, allRoles);
		createCardIfNotExist("LOW_DATA_USERS", "Low Data Users Alert", "Count of users with unavailed leads below threshold", "OPERATIONS", "STAT_CARD", "user-minus", 13, allRoles);
		createCardIfNotExist("USERS_NOT_LOGGED_IN", "Users Not Logged In Today", "Count of eligible users who have not logged in today", "OPERATIONS", "STAT_CARD", "user-x", 14, allRoles);
		createCardIfNotExist("FOLLOWUP_USERS_NOT_LOGGED_IN_11AM", "Follow-up Users Not Logged In by 11 AM", "Count of users with scheduled follow-ups today who did not log in by 11 AM IST", "OPERATIONS", "STAT_CARD", "clock-alert", 15, allRoles);
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
