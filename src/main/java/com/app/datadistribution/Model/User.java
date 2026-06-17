package com.app.datadistribution.Model;


import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_status", columnList = "status")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @NotBlank
    @Size(min = 3, max = 80)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, digits, dots, underscores, and hyphens")
    @Column(nullable = false, length = 80)
    private String username;

    @NotBlank
    @Size(min = 8, max = 64)
    @Column(nullable = false, length = 200)
    private String password;

    @NotBlank
    @Email
    @Size(max = 200)
    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Pattern(
        regexp = "^(\\+?[0-9]{10,15})?$",
        message = "Invalid mobile number"
    )
    @Column(length = 15)
    private String mobile;

    @NotBlank
    @Size(min = 2, max = 80)
    @Pattern(regexp = "^[a-zA-Z ]+$", message = "Full name can only contain alphabets and spaces")
    @Column(nullable = false, length = 80)
    private String fullName;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(length = 200)
    private String verificationToken;

    @Size(max = 200)
    @Column(length = 200)
    private String pendingEmail;

    @Size(max = 10)
    @Column(length = 10)
    private String emailVerificationOtp;

    private LocalDateTime emailVerificationExpiry;

    @Size(max = 200)
    @Column(length = 200)
    private String emailVerificationToken;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Long tokenVersion = 1L;

    public Long getTokenVersion() {
        return tokenVersion == null ? 1L : tokenVersion;
    }

    @PastOrPresent
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PastOrPresent
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.tokenVersion == null) {
            this.tokenVersion = 1L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


}
