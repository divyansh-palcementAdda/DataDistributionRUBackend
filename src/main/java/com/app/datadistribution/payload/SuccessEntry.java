package com.app.datadistribution.payload;

import lombok.Getter;

@Getter
public class SuccessEntry {
	private final Long id;
	private final String identifier; // Email or Name
	private final RowStatus status;

	public SuccessEntry(Long id, String identifier) {
		this(id, identifier, RowStatus.SUCCESS);
	}

	public SuccessEntry(Long id, String identifier, RowStatus status) {
		this.id = id;
		this.identifier = identifier;
		this.status = status;
	}

	public enum RowStatus {
		SUCCESS, CREATED, UPDATED, SKIPPED
	}
}
