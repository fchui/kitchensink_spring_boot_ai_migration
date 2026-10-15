/*
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
package org.spring.quickstarts.kitchensink.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.spring.quickstarts.kitchensink.model.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

/**
 * Tests the MongoDB mapping, the unique email index and the numeric ids, against embedded MongoDB.
 */
@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
@Import(MemberIdGenerator.class)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository repository;

    @Test
    void insertAssignsIncreasingNumericIds() {
        Member first = repository.insert(member("Jane Doe", uniqueEmail()));
        Member second = repository.insert(member("Jane Doe", uniqueEmail()));

        assertNotNull(first.getId());
        assertTrue(second.getId() > first.getId());
    }

    @Test
    void findsByIdAndChecksEmail() {
        Member saved = repository.insert(member("Jane Doe", uniqueEmail()));

        assertEquals(saved.getEmail(), repository.findById(saved.getId()).orElseThrow().getEmail());
        assertTrue(repository.existsByEmail(saved.getEmail()));
        assertFalse(repository.existsByEmail(uniqueEmail()));
    }

    @Test
    void duplicateEmailIsRejectedByTheUniqueIndex() {
        String email = uniqueEmail();
        repository.insert(member("Jane Doe", email));

        assertThrows(DuplicateKeyException.class, () -> repository.insert(member("Janet Doe", email)));
    }

    @Test
    void emailUniquenessIsCaseSensitive() {
        String email = uniqueEmail();
        repository.insert(member("Jane Doe", email));

        repository.insert(member("Jane Doe", email.toUpperCase()));
    }

    @Test
    void listIsSortedByName() {
        repository.insert(member("Zoe Last", uniqueEmail()));
        repository.insert(member("Aaron First", uniqueEmail()));

        List<String> names = repository.findAllByOrderByNameAsc().stream().map(Member::getName).toList();
        assertEquals(names.stream().sorted().toList(), names);
    }

    private static Member member(String name, String email) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPhoneNumber("2125551234");
        return member;
    }

    private static String uniqueEmail() {
        return "repo-" + UUID.randomUUID().toString().substring(0, 8) + "@mailinator.com";
    }
}
