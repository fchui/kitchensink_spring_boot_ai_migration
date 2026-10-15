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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.quickstarts.kitchensink.model.Member;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with the sample member John Smith (id 0) on startup, as {@code import.sql} did for the
 * relational database. Existing data is kept: the member is only added when no member has id 0.
 */
@Component
public class MemberDataInitializer implements ApplicationRunner {

    static final long SEED_ID = 0L;

    private static final Logger log = LoggerFactory.getLogger(MemberDataInitializer.class);

    private final MemberRepository memberRepository;

    public MemberDataInitializer(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (memberRepository.existsById(SEED_ID)) {
            return;
        }
        Member seed = new Member();
        seed.setId(SEED_ID);
        seed.setName("John Smith");
        seed.setEmail("john.smith@mailinator.com");
        seed.setPhoneNumber("2125551212");
        try {
            memberRepository.insert(seed);
            log.info("Added sample member {}", seed.getName());
        } catch (DuplicateKeyException e) {
            // another instance seeded it first, or the email is already registered
            log.debug("Sample member not added: {}", e.getMessage());
        }
    }
}
