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
package org.spring.as.quickstarts.kitchensink.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.spring.as.quickstarts.kitchensink.data.MemberRepository;
import org.spring.as.quickstarts.kitchensink.model.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Tests the registration rules shared by the web page and the REST API, against the in-memory database.
 */
@SpringBootTest
@RecordApplicationEvents
class MemberRegistrationTest {

    @Autowired
    private MemberRegistration registration;

    @Autowired
    private MemberRepository repository;

    @Autowired
    private ApplicationEvents events;

    @Test
    void registerSavesMemberAndPublishesEvent() {
        Member member = member("Jane Doe", uniqueEmail());

        registration.register(member);

        assertNotNull(member.getId());
        assertEquals(1, countStored(member.getEmail()));
        assertEquals(1, countEvents(member.getEmail()));
    }

    @Test
    void duplicateEmailIsRejectedWithoutPublishing() {
        String email = uniqueEmail();
        registration.register(member("Jane Doe", email));

        assertThrows(DuplicateEmailException.class, () -> registration.register(member("Janet Doe", email)));

        assertEquals(1, countStored(email));
        assertEquals(1, countEvents(email));
    }

    @Test
    void memberWithIdIsRejected() {
        Member member = member("Mallory", uniqueEmail());
        member.setId(0L);

        assertThrows(IllegalArgumentException.class, () -> registration.register(member));

        assertEquals("John Smith", repository.findById(0L).orElseThrow().getName());
        assertEquals(0, countStored(member.getEmail()));
    }

    @Test
    void membersAreListedByName() {
        registration.register(member("Zoe Last", uniqueEmail()));
        registration.register(member("Aaron First", uniqueEmail()));

        List<String> names = registration.findAllOrderedByName().stream().map(Member::getName).toList();

        assertEquals(names.stream().sorted().toList(), names);
    }

    private long countStored(String email) {
        return repository.findAll().stream().filter(m -> email.equals(m.getEmail())).count();
    }

    private long countEvents(String email) {
        return events.stream(Member.class).filter(m -> email.equals(m.getEmail())).count();
    }

    private static Member member(String name, String email) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPhoneNumber("2125551234");
        return member;
    }

    private static String uniqueEmail() {
        return "svc-" + UUID.randomUUID().toString().substring(0, 8) + "@mailinator.com";
    }
}
