package com.spendwise.user;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidRelationshipTypeException extends ApiException {

    public InvalidRelationshipTypeException(String value) {
        super(
                "INVALID_RELATIONSHIP_TYPE",
                HttpStatus.BAD_REQUEST,
                "relationshipType must be one of family, friend, self, settlement, got: " + value);
    }
}
