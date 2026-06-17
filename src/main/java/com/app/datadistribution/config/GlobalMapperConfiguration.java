package com.app.datadistribution.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Global configuration for MapStruct mappers.
 * Helps avoid repeating the same annotations on each mapper.
 */
@MapperConfig(
    componentModel = "spring", // Allows mappers to be injected as Spring beans
    unmappedTargetPolicy = ReportingPolicy.IGNORE // Ignores unmapped fields instead of erroring
)
public interface GlobalMapperConfiguration {

}
