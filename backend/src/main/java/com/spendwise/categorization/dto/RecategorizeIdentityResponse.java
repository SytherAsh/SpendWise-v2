package com.spendwise.categorization.dto;

/** {@code recategorized} is how many transactions were re-attempted, not necessarily changed —
 * see {@code CategorizationService#recategorizeIdentity}'s Javadoc. */
public record RecategorizeIdentityResponse(int recategorized) {}
