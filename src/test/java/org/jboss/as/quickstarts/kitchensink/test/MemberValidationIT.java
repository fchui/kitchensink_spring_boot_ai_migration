/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.quickstarts.kitchensink.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.net.http.HttpResponse;
import java.util.Set;

import jakarta.json.JsonObject;

import org.junit.Test;

/**
 * Contract tests for validation errors returned by the members REST API.
 * Invalid requests must return 400 with a {field: message} body and must not
 * store anything.
 */
public class MemberValidationIT {

    private static final String VALID_NAME = "Jane Doe";
    private static final String VALID_PHONE = "2125551234";

    @Test
    public void nameWithDigitsReturns400WithNameError() throws Exception {
        String email = MemberApi.uniqueEmail("badname");

        HttpResponse<String> response = MemberApi.register("Jane Doe 2", email, VALID_PHONE);

        assertValidationErrors(response, Set.of("name"));
        assertEquals(0, MemberApi.countMembersWithEmail(email));
    }

    @Test
    public void nameLongerThan25CharactersReturns400WithNameError() throws Exception {
        String email = MemberApi.uniqueEmail("longname");

        HttpResponse<String> response = MemberApi.register("A".repeat(26), email, VALID_PHONE);

        assertValidationErrors(response, Set.of("name"));
        assertEquals(0, MemberApi.countMembersWithEmail(email));
    }

    @Test
    public void malformedEmailReturns400WithEmailError() throws Exception {
        HttpResponse<String> response = MemberApi.register(VALID_NAME, "not-an-email", VALID_PHONE);

        assertValidationErrors(response, Set.of("email"));
        assertEquals(0, MemberApi.countMembersWithEmail("not-an-email"));
    }

    @Test
    public void tooShortPhoneNumberReturns400WithPhoneError() throws Exception {
        String email = MemberApi.uniqueEmail("shortphone");

        HttpResponse<String> response = MemberApi.register(VALID_NAME, email, "212555123");

        assertValidationErrors(response, Set.of("phoneNumber"));
        assertEquals(0, MemberApi.countMembersWithEmail(email));
    }

    @Test
    public void nonNumericPhoneNumberReturns400WithPhoneError() throws Exception {
        String email = MemberApi.uniqueEmail("alphaphone");

        HttpResponse<String> response = MemberApi.register(VALID_NAME, email, "212555CALL");

        assertValidationErrors(response, Set.of("phoneNumber"));
        assertEquals(0, MemberApi.countMembersWithEmail(email));
    }

    @Test
    public void allFieldsInvalidReturnsAnErrorForEachField() throws Exception {
        HttpResponse<String> response = MemberApi.register("R2D2", "not-an-email", "123");

        assertValidationErrors(response, Set.of("name", "email", "phoneNumber"));
    }

    @Test
    public void missingFieldsReturnAnErrorForEachField() throws Exception {
        HttpResponse<String> response = MemberApi.post("{}");

        assertValidationErrors(response, Set.of("name", "email", "phoneNumber"));
    }

    @Test
    public void nullBodyReturns400() throws Exception {
        assertEquals(400, MemberApi.post("null").statusCode());
    }

    @Test
    public void emptyBodyReturns400() throws Exception {
        assertEquals(400, MemberApi.post("").statusCode());
    }

    /**
     * Characterization: records a known bug. The JSON parsing error (JsonbException)
     * is not mapped to a client error, so the server answers 500. The desired
     * behavior is 400; change this assertion in the commit that fixes it.
     */
    @Test
    public void malformedJsonCurrentlyReturns500() throws Exception {
        assertEquals(500, MemberApi.post("{\"name\": \"Jane Doe\",").statusCode());
    }

    private static void assertValidationErrors(HttpResponse<String> response, Set<String> expectedFields) {
        assertEquals("status, body: " + response.body(), 400, response.statusCode());
        JsonObject errors = MemberApi.readObject(response.body());
        assertEquals("fields with errors", expectedFields, errors.keySet());
        for (String field : expectedFields) {
            assertFalse("message for " + field, errors.getString(field).isBlank());
        }
    }
}
