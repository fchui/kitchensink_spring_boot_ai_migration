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
package org.spring.as.quickstarts.kitchensink.rest;

import java.util.List;

import jakarta.validation.Valid;

import org.spring.as.quickstarts.kitchensink.model.Member;
import org.spring.as.quickstarts.kitchensink.service.MemberRegistration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API for members. Error responses (400 with {field: message}, 409 for a taken email) are produced by
 * {@link MemberRestExceptionHandler}.
 */
@RestController
@RequestMapping("/rest/members")
public class MemberResourceRESTService {

    private final MemberRegistration registration;

    public MemberResourceRESTService(MemberRegistration registration) {
        this.registration = registration;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Member> listAllMembers() {
        return registration.findAllOrderedByName();
    }

    @GetMapping(path = "/{id:[0-9][0-9]*}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Member lookupMemberById(@PathVariable("id") long id) {
        return registration.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * Registers a new member. Returns 200 with an empty body; validation and duplicate-email errors are mapped by
     * {@link MemberRestExceptionHandler}.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createMember(@Valid @RequestBody Member member) {
        registration.register(member);
        return ResponseEntity.ok().build();
    }
}
