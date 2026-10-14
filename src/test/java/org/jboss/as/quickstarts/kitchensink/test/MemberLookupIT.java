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

import java.net.http.HttpResponse;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.Test;

/**
 * Contract tests for looking up a single member by id. Ids are always taken
 * from the member list, so these tests also hold if the id format changes;
 * only {@link #memberIdIsANumber()} pins the current format.
 */
public class MemberLookupIT {

    @Test
    public void seedMemberCanBeLookedUpById() throws Exception {
        JsonObject listed = MemberApi.findMemberByEmail("john.smith@mailinator.com");

        HttpResponse<String> response = MemberApi.getMember(MemberApi.idOf(listed));

        assertEquals(200, response.statusCode());
        assertEquals(listed, MemberApi.readObject(response.body()));
    }

    @Test
    public void registeredMemberCanBeLookedUpById() throws Exception {
        String email = MemberApi.uniqueEmail("lookup");
        assertEquals(200, MemberApi.register("Jane Doe", email, "2125551234").statusCode());
        String id = MemberApi.idOf(MemberApi.findMemberByEmail(email));

        HttpResponse<String> response = MemberApi.getMember(id);

        assertEquals(200, response.statusCode());
        JsonObject member = MemberApi.readObject(response.body());
        assertEquals(id, MemberApi.idOf(member));
        assertEquals("Jane Doe", member.getString("name"));
        assertEquals(email, member.getString("email"));
        assertEquals("2125551234", member.getString("phoneNumber"));
    }

    @Test
    public void unknownIdReturns404() throws Exception {
        assertEquals(404, MemberApi.getMember(String.valueOf(Long.MAX_VALUE)).statusCode());
    }

    @Test
    public void nonNumericIdReturns404() throws Exception {
        assertEquals(404, MemberApi.getMember("not-an-id").statusCode());
    }

    /**
     * Pins the current id format: a JSON number, with the seed member at 0.
     * Moving to MongoDB ObjectIds (strings) is an API change for clients; if
     * that is chosen, change this test deliberately, in its own commit.
     */
    @Test
    public void memberIdIsANumber() throws Exception {
        JsonObject seed = MemberApi.findMemberByEmail("john.smith@mailinator.com");

        assertEquals(JsonValue.ValueType.NUMBER, seed.get("id").getValueType());
        assertEquals(0, seed.getJsonNumber("id").longValueExact());
    }
}
