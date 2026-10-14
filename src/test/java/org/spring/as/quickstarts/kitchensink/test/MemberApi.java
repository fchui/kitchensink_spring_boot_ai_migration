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

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Black-box HTTP client for the members REST API, shared by the contract tests.
 * The tests only depend on the HTTP contract, so they are independent of the
 * framework and persistence technology behind the API.
 */
final class MemberApi {

    private static final String DEFAULT_SERVER_HOST = "http://localhost:8080";

    // HTTP/1.1: the default HTTP/2 upgrade attempt can stall POST requests on Undertow.
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private MemberApi() {
    }

    static String membersUrl() {
        String serverHost = System.getenv("SERVER_HOST");
        if (serverHost == null) {
            serverHost = System.getProperty("server.host");
        }
        if (serverHost == null) {
            serverHost = DEFAULT_SERVER_HOST;
        }
        return serverHost + "/kitchensink/rest/members";
    }

    /** Unique per call, so the suite can run repeatedly against the same server. */
    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@mailinator.com";
    }

    static String memberJson(String name, String email, String phoneNumber) {
        return Json.createObjectBuilder()
                .add("name", name)
                .add("email", email)
                .add("phoneNumber", phoneNumber)
                .build()
                .toString();
    }

    static HttpRequest postRequest(String body) {
        return HttpRequest.newBuilder(URI.create(membersUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    static HttpResponse<String> post(String body) throws Exception {
        return CLIENT.send(postRequest(body), HttpResponse.BodyHandlers.ofString());
    }

    static CompletableFuture<HttpResponse<String>> postAsync(String body) {
        return CLIENT.sendAsync(postRequest(body), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> register(String name, String email, String phoneNumber) throws Exception {
        return post(memberJson(name, email, phoneNumber));
    }

    static JsonArray listMembers() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(membersUrl())).GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Listing members failed with HTTP " + response.statusCode());
        }
        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            return reader.readArray();
        }
    }

    static HttpResponse<String> getMember(String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(membersUrl() + "/" + id)).GET().build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static JsonObject findMemberByEmail(String email) throws Exception {
        return listMembers().stream()
                .map(JsonValue::asJsonObject)
                .filter(m -> m.getString("email").equals(email))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No member listed with email " + email));
    }

    /** The member's id as it appears in URLs, whether the API returns it as a JSON number or string. */
    static String idOf(JsonObject member) {
        JsonValue id = member.get("id");
        return id instanceof JsonString ? ((JsonString) id).getString() : id.toString();
    }

    static long countMembersWithEmail(String email) throws Exception {
        return listMembers().stream()
                .map(JsonValue::asJsonObject)
                .filter(m -> m.getString("email").equals(email))
                .count();
    }

    static long countMembersWithEmailIgnoreCase(String email) throws Exception {
        return listMembers().stream()
                .map(JsonValue::asJsonObject)
                .filter(m -> m.getString("email").equalsIgnoreCase(email))
                .count();
    }

    static JsonObject readObject(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }
}
