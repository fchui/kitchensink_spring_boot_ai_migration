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
import static org.junit.Assert.assertTrue;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import jakarta.json.JsonObject;

import org.junit.Test;

/**
 * Contract tests for the unique-email rule of the members REST API.
 */
public class MemberDuplicateEmailIT {

    private static final int CONCURRENT_REQUESTS = 8;

    @Test
    public void secondRegistrationWithSameEmailIsRejectedWith409() throws Exception {
        String email = MemberApi.uniqueEmail("dup");

        HttpResponse<String> first = MemberApi.register("Jane Doe", email, "2125551234");
        assertEquals("first registration", 200, first.statusCode());

        HttpResponse<String> second = MemberApi.register("Janet Doe", email, "2125559876");
        assertEquals("duplicate registration", 409, second.statusCode());
        JsonObject body = MemberApi.readObject(second.body());
        assertEquals("Email taken", body.getString("email"));
        assertEquals("only the error field is returned", 1, body.size());

        assertEquals("duplicate must not be stored", 1, MemberApi.countMembersWithEmail(email));
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
     * Same email registered by several requests at once: exactly one may succeed.
     * The duplicate check runs outside the save's transaction, so requests that
     * lose the race are rejected by the database constraint instead. Today those
     * fall into the generic error handler (400); after the planned fix in
     * MIGRATION.md every rejection should be 409, and this test should be
     * tightened to assert that.
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

        assertEquals(summary, Integer.valueOf(1), statusCounts.getOrDefault(200, 0));
        for (int status : statusCounts.keySet()) {
            assertTrue(summary, status == 200 || status == 400 || status == 409);
        }
        assertEquals(summary, 1, MemberApi.countMembersWithEmail(email));
    }
}
