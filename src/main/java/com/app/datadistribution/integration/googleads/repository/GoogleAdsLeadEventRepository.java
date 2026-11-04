package com.app.datadistribution.integration.googleads.repository;

import com.app.datadistribution.integration.googleads.entity.GoogleAdsLeadEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoogleAdsLeadEventRepository extends JpaRepository<GoogleAdsLeadEvent, UUID> {

    Optional<GoogleAdsLeadEvent> findByProviderAndExternalLeadId(String provider, String externalLeadId);

    boolean existsByProviderAndExternalLeadId(String provider, String externalLeadId);
}

