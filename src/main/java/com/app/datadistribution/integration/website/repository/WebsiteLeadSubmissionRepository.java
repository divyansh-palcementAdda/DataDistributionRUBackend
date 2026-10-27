package com.app.datadistribution.integration.website.repository;

import com.app.datadistribution.integration.website.entity.WebsiteLeadSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebsiteLeadSubmissionRepository extends JpaRepository<WebsiteLeadSubmission, UUID> {

    Optional<WebsiteLeadSubmission> findByExternalLeadIdAndSourceCode(String externalLeadId, String sourceCode);

    boolean existsByExternalLeadIdAndSourceCode(String externalLeadId, String sourceCode);
}
