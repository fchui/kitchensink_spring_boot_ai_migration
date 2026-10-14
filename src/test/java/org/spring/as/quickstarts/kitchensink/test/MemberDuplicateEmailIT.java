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
package org.spring.as.quickstarts.kitchensink.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the unique-email rule of the members REST API.
 */
public class MemberDuplicateEmailIT {

    private static final int CONCURRENT_REQUESTS = 8;

    @Test
    public void secondRegistrationWithSameEmailIsRejectedWith409() throws Exception {
        String email = MemberApi.uniqueEmail("dup");

        HttpResponse<String> first = MemberApi.register("Jane Doe", email, "2125551234");
        assertEquals(200, first.statusCode(), "first registration");
        assertEquals("", first.body(), "successful registration has an empty body");

        HttpResponse<String> second = MemberApi.register("Janet Doe", email, "2125559876");
        assertEquals(409, second.statusCode(), "duplicate registration");
        JsonObject body = MemberApi.readObject(second.body());
        assertEquals("Email taken", body.getString("email"));
        assertEquals(1, body.size(), "only the error field is returned");

        assertEquals(1, MemberApi.countMembersWithEmail(email), "duplicate must not be stored");
    }

    @Test
    public void seedMemberEmailIsAlreadyTaken() throws Exception {
        HttpResponse<String> response = MemberApi.register("Johnny Smith", "john.smith@mailinator.com", "2125550000");

        assertEquals(409, response.statusCode());
        assertEquals("Email taken", MemberApi.readObject(response.body()).getString("email"));
        assertEquals(1, MemberApi.countMembersWithEmail("john.smith@mailinator.com"));
    }

    /**
     * Characterization test: records current behavior, which may not be desired.
     * The duplicate check is an exact match and the database constraint is
     * case-sensitive, so emails differing only by case are both accepted.
     * If this is changed deliberately, update this test in its own commit.
     */
    @Test
    public void emailsDifferingOnlyByCaseAreBothAccepted() throws Exception {
        String email = MemberApi.uniqueEmail("case");
        String upperCaseEmail = email.toUpperCase();

        assertEquals(200, MemberApi.register("Jane Doe", email, "2125551234").statusCode());
        assertEquals(200, MemberApi.register("Jane Doe", upperCaseEmail, "2125551234").statusCode());

        assertEquals(2, MemberApi.countMembersWithEmailIgnoreCase(email));
    }

    /**
     * Same email registered by several requests at once: exactly one may succeed,
     * and every other request gets 409, also those that pass the duplicate check
     * and are only stopped by the database's unique constraint.
     */
    @Test
    public void concurrentRegistrationsWithSameEmailStoreExactlyOneMember() throws Exception {
        String email = MemberApi.uniqueEmail("race");
        String body = MemberApi.memberJson("Race Condition", email, "2125551234");

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            futures.add(MemberApi.postAsync(body));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<Integer, Integer> statusCounts = new TreeMap<>();
        for (CompletableFuture<HttpResponse<String>> future : futures) {
            statusCounts.merge(future.get().statusCode(), 1, Integer::sum);
        }
        String summary = "status codes: " + statusCounts;

        assertEquals(1, statusCounts.getOrDefault(200, 0), summary);
        assertEquals(CONCURRENT_REQUESTS - 1, statusCounts.getOrDefault(409, 0), summary);
        assertEquals(1, MemberApi.countMembersWithEmail(email), summary);
    }
}
