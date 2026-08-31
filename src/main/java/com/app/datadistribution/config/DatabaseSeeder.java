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
import java.util.Optional;
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

	@org.springframework.beans.factory.annotation.Value("${app.seed.default-superadmin-password:Admin@123}")
	private String defaultSuperAdminPassword;

	@org.springframework.beans.factory.annotation.Value("${app.seed.default-admin-password:Admin@123}")
	private String defaultAdminPassword;

	@org.springframework.beans.factory.annotation.Value("${app.seed.default-hod-password:Hod@123}")
	private String defaultHodPassword;

	@org.springframework.beans.factory.annotation.Value("${app.seed.default-counselor-password:Counselor@123}")
	private String defaultCounselorPassword;

	@Override
	@Transactional
	public void run(String... args) {
		log.info("Database seeding started...");
		seedPermissions();
		seedRoles();
		seedDepartments();
		migrateLegacyUserRoleToCounselor();
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
						.active(true)
						.build();
				permissionRepository.save(permission);
				log.info("Seeded permission: {}", name);
			}
		}
	}

	private void seedRoles() {
		Set<Permission> allPermissions = new HashSet<>(permissionRepository.findAll());

		// 1. SUPER_ADMIN Role (Full System Access)
		syncRoleDefaultPermissions(RoleType.SUPER_ADMIN.name(), "Super Administrator Role", allPermissions);

		// 2. ADMIN Role (Administrative System-wide Access)
		Set<Permission> adminPermissions = filterPermissions(allPermissions,
				"USER_", "AUTH_", "LEAD_", "LEADSOURCE_", "LEAD_STATUS_",
				"BOARD_", "GRADE_", "DASHBOARD_", "COURSE_", "FOLLOWUP_",
				"FOLLOW_UP_", "FEEDBACK_", "DEPARTMENT_", "ROLE_", "PERMISSION_", "DROPDOWN_");
		syncRoleDefaultPermissions(RoleType.ADMIN.name(), "Administrator Role", adminPermissions);

		// 3. HOD Role (Department-level Operational & Management Access)
		Set<Permission> hodPermissions = allPermissions.stream()
				.filter(p -> {
					String n = p.getName();
					return n.equals(PermissionType.LEAD_READ.name())
							|| n.equals(PermissionType.LEAD_CREATE.name())
							|| n.equals(PermissionType.LEAD_UPDATE.name())
							|| n.equals(PermissionType.LEAD_ASSIGN.name())
							|| n.equals(PermissionType.LEAD_STATUS_CHANGE.name())
							|| n.equals(PermissionType.LEAD_AVAIL.name())
							|| n.equals(PermissionType.LEAD_FEEDBACK_CREATE.name())
							|| n.equals(PermissionType.LEAD_FOLLOWUP_CREATE.name())
							|| n.equals(PermissionType.LEAD_HISTORY_READ.name())
							|| n.equals(PermissionType.LEAD_DISTRIBUTE.name())
							|| n.equals(PermissionType.LEAD_DISTRIBUTION_PREVIEW.name())
							|| n.equals(PermissionType.LEAD_REASSIGN.name())
							|| n.equals(PermissionType.LEAD_BULK_REASSIGN.name())
							|| n.equals(PermissionType.LEAD_INTERESTED_COURSE_UPDATE.name())
							|| n.equals(PermissionType.LEAD_REGISTERED_COURSE_UPDATE.name())
							|| n.startsWith("FOLLOWUP_")
							|| n.startsWith("FOLLOW_UP_")
							|| n.startsWith("FEEDBACK_")
							|| n.equals(PermissionType.DASHBOARD_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_VIEW_DEPARTMENT.name())
							|| n.equals(PermissionType.DASHBOARD_COURSE_TYPE_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_CARD_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_CARD_PREFERENCE_UPDATE.name())
							|| n.equals(PermissionType.DASHBOARD_CARD_ORDER_UPDATE.name())
							|| n.equals(PermissionType.DASHBOARD_USER_PREFERENCE_MANAGE.name())
							|| n.equals(PermissionType.DASHBOARD_LOW_DATA_USERS_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_LOW_DATA_USERS_READ.name())
							|| n.equals(PermissionType.DASHBOARD_USERS_NOT_LOGGED_IN_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_USERS_NOT_LOGGED_IN_READ.name())
							|| n.equals(PermissionType.DASHBOARD_FOLLOWUP_USERS_NOT_LOGGED_IN_11AM_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_FOLLOWUP_USERS_NOT_LOGGED_IN_11AM_READ.name())
							|| n.equals(PermissionType.DEPARTMENT_VIEW.name())
							|| n.equals(PermissionType.DEPARTMENT_USER_VIEW.name())
							|| n.equals(PermissionType.DEPARTMENT_HOD_VIEW.name())
							|| n.equals(PermissionType.DEPARTMENT_COUNSELLOR_VIEW.name())
							|| n.equals(PermissionType.DEPARTMENT_DATA_VIEW.name())
							|| n.equals(PermissionType.DEPARTMENT_DATA_CREATE.name())
							|| n.equals(PermissionType.DEPARTMENT_DATA_UPDATE.name())
							|| n.startsWith("COURSE_")
							|| n.startsWith("LEAD_STATUS_")
							|| n.startsWith("LEADSOURCE_")
							|| n.startsWith("BOARD_")
							|| n.startsWith("GRADE_")
							|| (n.startsWith("DROPDOWN_") && !n.equals(PermissionType.DROPDOWN_ROLE_VIEW.name()) && !n.equals(PermissionType.DROPDOWN_PERMISSION_VIEW.name()))
							|| n.equals(PermissionType.USER_READ.name())
							|| n.equals(PermissionType.AUTH_READ.name());
				})
				.collect(Collectors.toSet());
		syncRoleDefaultPermissions(RoleType.HOD.name(), "Head of Department Role", hodPermissions);

		// 4. COUNSELOR Role (Operational / Self-level Access)
		Set<Permission> counselorPermissions = allPermissions.stream()
				.filter(p -> {
					String n = p.getName();
					return n.equals(PermissionType.LEAD_READ.name())
							|| n.equals(PermissionType.LEAD_STATUS_CHANGE.name())
							|| n.equals(PermissionType.LEAD_AVAIL.name())
							|| n.equals(PermissionType.LEAD_FEEDBACK_CREATE.name())
							|| n.equals(PermissionType.LEAD_FOLLOWUP_CREATE.name())
							|| n.equals(PermissionType.LEAD_HISTORY_READ.name())
							|| n.equals(PermissionType.LEAD_INTERESTED_COURSE_UPDATE.name())
							|| n.equals(PermissionType.LEAD_REGISTERED_COURSE_UPDATE.name())
							|| n.equals(PermissionType.FOLLOWUP_VIEW.name())
							|| n.equals(PermissionType.FOLLOWUP_CREATE.name())
							|| n.equals(PermissionType.FOLLOWUP_UPDATE.name())
							|| n.equals(PermissionType.FEEDBACK_VIEW.name())
							|| n.equals(PermissionType.FEEDBACK_CREATE.name())
							|| n.equals(PermissionType.FEEDBACK_UPDATE.name())
							|| n.equals(PermissionType.DASHBOARD_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_COURSE_TYPE_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_CARD_VIEW.name())
							|| n.equals(PermissionType.DASHBOARD_CARD_PREFERENCE_UPDATE.name())
							|| n.equals(PermissionType.DASHBOARD_CARD_ORDER_UPDATE.name())
							|| n.equals(PermissionType.COURSE_VIEW.name())
							|| n.equals(PermissionType.COURSE_TYPE_VIEW.name())
							|| n.equals(PermissionType.COURSE_TEMPLATE_VIEW.name())
							|| n.equals(PermissionType.COURSE_TEMPLATE_SEND.name())
							|| n.equals(PermissionType.COURSE_TEMPLATE_SEND_EMAIL.name())
							|| n.equals(PermissionType.COURSE_TEMPLATE_SEND_WHATSAPP.name())
							|| n.equals(PermissionType.COURSE_TEMPLATE_IMAGE_SELECT.name())
							|| n.equals(PermissionType.COURSE_IMAGE_VIEW.name())
							|| n.equals(PermissionType.COURSE_USP_VIEW.name())
							|| n.equals(PermissionType.DEPARTMENT_VIEW.name())
							|| n.equals(PermissionType.USER_READ.name())
							|| n.equals(PermissionType.AUTH_READ.name())
							|| n.equals(PermissionType.LEAD_STATUS_VIEW.name())
							|| n.equals(PermissionType.LEAD_STATUS_HISTORY_VIEW.name())
							|| n.equals(PermissionType.LEADSOURCE_READ.name())
							|| n.equals(PermissionType.BOARD_VIEW.name())
							|| n.equals(PermissionType.GRADE_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_USER_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_DEPARTMENT_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_LEAD_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_COURSE_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_STATUS_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_SOURCE_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_GRADE_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_BOARD_VIEW.name())
							|| n.equals(PermissionType.DROPDOWN_COURSE_TYPE_VIEW.name());
				})
				.collect(Collectors.toSet());
		syncRoleDefaultPermissions(RoleType.COUNSELOR.name(), "Counselor Operational Role", counselorPermissions);
	}

	private Set<Permission> filterPermissions(Set<Permission> allPermissions, String... prefixes) {
		return allPermissions.stream()
				.filter(p -> {
					for (String prefix : prefixes) {
						if (p.getName().startsWith(prefix) || p.getName().equals(prefix)) {
							return true;
						}
					}
					return false;
				})
				.collect(Collectors.toSet());
	}

	private void syncRoleDefaultPermissions(String name, String description, Set<Permission> defaultPermissions) {
		Optional<Role> roleOpt = roleRepository.findByName(name);
		if (roleOpt.isEmpty()) {
			Role role = Role.builder()
					.name(name)
					.description(description)
					.active(true)
					.permissions(new HashSet<>(defaultPermissions))
					.build();
			roleRepository.save(role);
			log.info("Created default role: {} with {} initial permissions", name, defaultPermissions.size());
		} else {
			Role role = roleOpt.get();
			if (role.getPermissions() == null) {
				role.setPermissions(new HashSet<>());
			}
			boolean modified = false;
			for (Permission p : defaultPermissions) {
				if (!role.getPermissions().contains(p)) {
					role.getPermissions().add(p);
					modified = true;
				}
			}
			if (modified) {
				roleRepository.save(role);
				log.info("Added newly introduced default permissions to role {}. Total permissions now: {}", name, role.getPermissions().size());
			} else {
				log.debug("Role {} is up-to-date with default permissions. Dynamic RBAC preserved.", name);
			}
		}
	}

	private void seedDepartments() {
		createDepartmentIfNotExist("Admissions", "ADM", "Student Admissions & Enrollment Department");
		createDepartmentIfNotExist("Marketing", "MKT", "Lead Generation & Marketing Department");
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

	private void migrateLegacyUserRoleToCounselor() {
		Optional<Role> legacyUserRoleOpt = roleRepository.findByName(RoleType.USER.name());
		if (legacyUserRoleOpt.isEmpty()) {
			return;
		}
		Role legacyUserRole = legacyUserRoleOpt.get();
		Role counselorRole = roleRepository.findByName(RoleType.COUNSELOR.name())
				.orElseThrow(() -> new RuntimeException("COUNSELOR role not seeded before migration"));

		Department defaultDept = departmentRepository.findByCodeIgnoreCaseAndIsDeletedFalse("ADM").orElse(null);

		List<User> allUsers = userRepository.findAll();
		int migratedCount = 0;
		for (User user : allUsers) {
			if (user.getRoles() != null && user.getRoles().contains(legacyUserRole)) {
				user.getRoles().remove(legacyUserRole);
				user.getRoles().add(counselorRole);

				if ((user.getDepartments() == null || user.getDepartments().isEmpty()) && defaultDept != null) {
					if (user.getDepartments() == null) {
						user.setDepartments(new HashSet<>());
					}
					user.getDepartments().add(defaultDept);
				}
				userRepository.save(user);
				migratedCount++;
			}
		}

		if (migratedCount > 0) {
			log.info("Migrated {} users from legacy USER role to COUNSELOR role while preserving user data and department mappings", migratedCount);
		}

		if (legacyUserRole.isActive()) {
			legacyUserRole.setActive(false);
			roleRepository.save(legacyUserRole);
			log.info("Marked legacy USER role as inactive");
		}
	}

	private void seedUsers() {
		Department defaultDept = departmentRepository.findByCodeIgnoreCaseAndIsDeletedFalse("ADM").orElse(null);

		// 1. Seed default Super Admin
		if (!userRepository.findByUsername("superadmin").isPresent() && !userRepository.existsByEmail("superadmin@datadistribution.com")) {
			Role superAdminRole = roleRepository.findByName(RoleType.SUPER_ADMIN.name())
					.orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not seeded"));

			User superAdmin = User.builder()
					.firstName("Super")
					.lastName("Admin")
					.email("superadmin@datadistribution.com")
					.phone("+919999999999")
					.username("superadmin")
					.password(passwordEncoder.encode(defaultSuperAdminPassword))
					.active(true)
					.locked(false)
					.emailVerified(true)
					.roles(new HashSet<>(Set.of(superAdminRole)))
					.tokenVersion(1L)
					.build();
			userRepository.save(superAdmin);
			log.info("Seeded default superadmin user (without department mapping)");
		}

		// 2. Seed default Admin
		if (!userRepository.findByUsername("admin").isPresent() && !userRepository.existsByEmail("admin@datadistribution.com")) {
			Role adminRole = roleRepository.findByName(RoleType.ADMIN.name())
					.orElseThrow(() -> new RuntimeException("ADMIN role not seeded"));

			User admin = User.builder()
					.firstName("Standard")
					.lastName("Admin")
					.email("admin@datadistribution.com")
					.phone("+918888888888")
					.username("admin")
					.password(passwordEncoder.encode(defaultAdminPassword))
					.active(true)
					.locked(false)
					.emailVerified(true)
					.roles(new HashSet<>(Set.of(adminRole)))
					.tokenVersion(1L)
					.build();
			userRepository.save(admin);
			log.info("Seeded default admin user (without department mapping)");
		}

		// 3. Seed default HOD
		if (!userRepository.findByUsername("hod").isPresent() && !userRepository.existsByEmail("hod@datadistribution.com")) {
			Role hodRole = roleRepository.findByName(RoleType.HOD.name())
					.orElseThrow(() -> new RuntimeException("HOD role not seeded"));

			Set<Department> hodDepts = new HashSet<>();
			if (defaultDept != null) {
				hodDepts.add(defaultDept);
			}

			User hod = User.builder()
					.firstName("Head")
					.lastName("Department")
					.email("hod@datadistribution.com")
					.phone("+917777777777")
					.username("hod")
					.password(passwordEncoder.encode(defaultHodPassword))
					.active(true)
					.locked(false)
					.emailVerified(true)
					.hodAccessType(com.app.datadistribution.enums.HodAccessType.FULL_ACCESS)
					.roles(new HashSet<>(Set.of(hodRole)))
					.departments(hodDepts)
					.tokenVersion(1L)
					.build();
			userRepository.save(hod);
			log.info("Seeded default HOD user (mapped to department: Admissions, FULL_ACCESS)");
		}

		// 4. Seed default Counselor
		if (!userRepository.findByUsername("counselor").isPresent() && !userRepository.existsByEmail("counselor@datadistribution.com")) {
			Role counselorRole = roleRepository.findByName(RoleType.COUNSELOR.name())
					.orElseThrow(() -> new RuntimeException("COUNSELOR role not seeded"));

			Set<Department> counselorDepts = new HashSet<>();
			if (defaultDept != null) {
				counselorDepts.add(defaultDept);
			}

			User counselor = User.builder()
					.firstName("Lead")
					.lastName("Counselor")
					.email("counselor@datadistribution.com")
					.phone("+916666666666")
					.username("counselor")
					.password(passwordEncoder.encode(defaultCounselorPassword))
					.active(true)
					.locked(false)
					.emailVerified(true)
					.roles(new HashSet<>(Set.of(counselorRole)))
					.departments(counselorDepts)
					.tokenVersion(1L)
					.build();
			userRepository.save(counselor);
			log.info("Seeded default counselor user (mapped to department: Admissions)");
		}
	}

	private void seedLeadStatuses() {

	    // =========================
	    // ROOT STATUS
	    // =========================
	    createStatusIfNotExist("Raw", "RAW", "New lead received. Define the lead type as Inbound or Outbound.", 1, SentimentCategory.NEUTRAL, null, false, null, false);

	    // =========================
	    // PRIMARY OUTCOMES
	    // =========================
	    createStatusIfNotExist("Connected", "CONNECTED", "Contact successfully established with the lead.", 2, SentimentCategory.POSITIVE, "RAW", false, null, false);
	    createStatusIfNotExist("Not Connected", "NOT_CONNECTED", "Unable to establish contact with the lead.", 3, SentimentCategory.NEGATIVE, "RAW", true, 2, false);

	    // =========================
	    // CONNECTED FLOW
	    // =========================
	    createStatusIfNotExist("Interested", "INTERESTED", "Lead has shown interest in the course or service.", 4, SentimentCategory.POSITIVE, "CONNECTED", false, null, false);
	    createStatusIfNotExist("Bad", "BAD", "Invalid, incorrect, duplicate, or unusable lead.", 5, SentimentCategory.NEGATIVE, "CONNECTED", false, null, false);
	    createStatusIfNotExist("Not Interested", "NOT_INTERESTED", "Lead is currently not interested.", 6, SentimentCategory.NEGATIVE, "CONNECTED", false, null, false);

	    // =========================
	    // INTERESTED -> FOLLOW-UP FLOW
	    // =========================
	    createStatusIfNotExist("Form Follow-Up", "FORM_FOLLOW_UP", "Lead is interested and requires follow-up regarding form submission.", 7, SentimentCategory.NEUTRAL, "INTERESTED", false, null, true);
	    createStatusIfNotExist("Counseling Follow-Up", "COUNSELING_FOLLOW_UP", "Lead requires counseling and further follow-up.", 8, SentimentCategory.NEUTRAL, "INTERESTED", false, null, true);

	    // =========================
	    // FORM FOLLOW-UP OUTCOMES
	    // =========================
	    createStatusIfNotExist("Registered", "REGISTERED", "Lead has successfully completed registration.", 9, SentimentCategory.POSITIVE, "FORM_FOLLOW_UP", false, null, false);
	    createStatusIfNotExist("Form Not Interested", "FORM_NOT_INTERESTED", "Lead is not interested in completing the registration form.", 10, SentimentCategory.NEGATIVE, "FORM_FOLLOW_UP", false, null, false);
	    createStatusIfNotExist("Continuous Form Follow-Up", "CONTINUOUS_FORM_FOLLOW_UP", "Lead requires continuous follow-up for form completion.", 11, SentimentCategory.NEUTRAL, "FORM_FOLLOW_UP", false, null, true);

	    // =========================
	    // COUNSELING FOLLOW-UP FLOW
	    // =========================
	    createStatusIfNotExist("Continuous Follow-Up", "CONTINUOUS_FOLLOW_UP", "Lead requires continuous counseling follow-up.", 12, SentimentCategory.NEUTRAL, "COUNSELING_FOLLOW_UP", false, null, true);
	    createStatusIfNotExist("Interested Follow-Up", "INTERESTED_FOLLOW_UP", "Interested lead requires additional follow-up.", 13, SentimentCategory.POSITIVE, "CONTINUOUS_FOLLOW_UP", false, null, true);
	    createStatusIfNotExist("Go To Form Follow-Up", "GO_TO_FORM_FOLLOW_UP", "Move the interested lead to the form follow-up process.", 14, SentimentCategory.POSITIVE, "INTERESTED_FOLLOW_UP", false, null, true);
	    createStatusIfNotExist("Counseling Not Interested", "COUNSELING_NOT_INTERESTED", "Lead became not interested after counseling follow-up.", 15, SentimentCategory.NEGATIVE, "CONTINUOUS_FOLLOW_UP", false, null, false);

	    // =========================
	    // NOT CONNECTED FOLLOW-UP FLOW (SEQUENTIAL & DAILY LIMIT)
	    // =========================
	    createStatusIfNotExist("Not Connected - 1", "NOT_CONNECTED_1", "First unsuccessful contact attempt.", 16, SentimentCategory.NEGATIVE, "NOT_CONNECTED", true, 2, false);
	    createStatusIfNotExist("Not Connected - 2", "NOT_CONNECTED_2", "Second unsuccessful contact attempt.", 17, SentimentCategory.NEGATIVE, "NOT_CONNECTED_1", true, 2, false);
	    createStatusIfNotExist("Not Connected - 3", "NOT_CONNECTED_3", "Third unsuccessful contact attempt.", 18, SentimentCategory.NEGATIVE, "NOT_CONNECTED_2", true, 2, false);
	    createStatusIfNotExist("Finally Not Connected", "FINALLY_NOT_CONNECTED", "All contact attempts have failed and the lead is marked as finally not connected.", 19, SentimentCategory.NEGATIVE, "NOT_CONNECTED_3", true, 2, false);

	    migrateExistingLeadStatusData();
	}

	private void createStatusIfNotExist(String name, String code, String description, int displayOrder, SentimentCategory sentimentCategory, String parentCode, boolean isSequential, Integer dailyAttemptLimit, boolean isFollowUpStatus) {
		LeadStatus parent = parentCode != null ? leadStatusRepository.findByCodeIgnoreCase(parentCode).orElse(null) : null;
		Optional<LeadStatus> existingOpt = leadStatusRepository.findByCodeIgnoreCase(code);
		if (existingOpt.isEmpty()) {
			LeadStatus status = LeadStatus.builder()
					.name(name)
					.code(code)
					.description(description)
					.displayOrder(displayOrder)
					.sentimentCategory(sentimentCategory)
					.parentStatus(parent)
					.isSequential(isSequential)
					.dailyAttemptLimit(dailyAttemptLimit)
					.isFollowUpStatus(isFollowUpStatus)
					.active(true)
					.build();
			leadStatusRepository.save(status);
			log.info("Seeded default lead status: {} ({}) with parent: {}, followUpStatus: {}", name, code, parentCode, isFollowUpStatus);
		} else {
			LeadStatus status = existingOpt.get();
			boolean modified = false;
			if (status.getParentStatus() == null && parent != null) {
				status.setParentStatus(parent);
				modified = true;
			}
			if (status.isSequential() != isSequential) {
				status.setSequential(isSequential);
				modified = true;
			}
			if (dailyAttemptLimit != null && (status.getDailyAttemptLimit() == null || !status.getDailyAttemptLimit().equals(dailyAttemptLimit))) {
				status.setDailyAttemptLimit(dailyAttemptLimit);
				modified = true;
			}
			if (status.isFollowUpStatus() != isFollowUpStatus) {
				status.setFollowUpStatus(isFollowUpStatus);
				modified = true;
			}
			if (modified) {
				leadStatusRepository.save(status);
				log.info("Updated hierarchy/follow-up metadata on lead status: {}", code);
			}
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

//	private void createGradeIfNotExist(String name, String code, String description, int displayOrder) {
//		if (!gradeRepository.findByCodeIgnoreCase(code).isPresent()) {
//			Grade grade = Grade.builder()
//					.name(name)
//					.code(code)
//					.description(description)
//					.displayOrder(displayOrder)
//					.active(true)
//					.build();
//			gradeRepository.save(grade);
//			log.info("Seeded default grade: {} ({})", name, code);
//		}
//	}

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
		createCardIfNotExist("TOTAL_ALLOTTED_DATA", "Total Allotted Data", "Count of leads currently assigned to users", "OVERVIEW", "STAT_CARD", "user-check", 16, allRoles);
		createCardIfNotExist("TOTAL_UNALLOTTED_DATA", "Total Unallotted Data", "Count of leads currently unassigned", "OVERVIEW", "STAT_CARD", "user-x", 17, allRoles);
		createCardIfNotExist("TOTAL_AVAILED_DATA", "Total Availed Data", "Count of leads currently marked as availed", "OVERVIEW", "STAT_CARD", "check-circle", 18, allRoles);
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
