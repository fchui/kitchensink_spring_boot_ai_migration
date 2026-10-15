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
package org.spring.quickstarts.kitchensink.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.quickstarts.kitchensink.data.MemberRepository;
import org.spring.quickstarts.kitchensink.model.Member;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Registers and lists members. Both the web page and the REST API go through this service, so the
 * registration rules (no id on new members, unique email) apply to both.
 */
@Service
public class MemberRegistration {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistration.class);

    private final MemberRepository memberRepository;

    private final ApplicationEventPublisher eventPublisher;

    public MemberRegistration(MemberRepository memberRepository, ApplicationEventPublisher eventPublisher) {
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Member> findAllOrderedByName() {
        return memberRepository.findAllByOrderByNameAsc();
    }

    public Optional<Member> findById(long id) {
        return memberRepository.findById(id);
    }

    /**
     * @throws IllegalArgumentException if the member already has an id
     * @throws DuplicateEmailException if the email is taken, including when a concurrent registration wins
     */
    public void register(Member member) {
        // save() would replace a member with the same id, overwriting it; registration only creates
        if (member.getId() != null) {
            throw new IllegalArgumentException("A new member must not have an id");
        }
        if (memberRepository.existsByEmail(member.getEmail())) {
            throw new DuplicateEmailException(member.getEmail(), null);
        }
        log.info("Registering {}", member.getName());
        try {
            // a single-document insert needs no transaction; a concurrent registration with the same email
            // passes the check above but fails here, on the unique index (DuplicateKeyException)
            memberRepository.insert(member);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException(member.getEmail(), e);
        }
        eventPublisher.publishEvent(member);
    }
}
