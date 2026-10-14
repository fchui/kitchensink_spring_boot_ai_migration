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
package org.jboss.as.quickstarts.kitchensink.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the validation rules declared on {@link Member}.
 * Tests marked as characterization record current behavior that may not be
 * desired; change them deliberately, in their own commit.
 */
public class MemberValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    public static void createValidator() {
        factory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    public static void closeValidator() {
        factory.close();
    }

    @Test
    public void validMemberHasNoViolations() {
        assertEquals(Set.of(), invalidFields(member("Jane Doe", "jane@mailinator.com", "2125551234")));
    }

    // name

    @Test
    public void nameIsRequired() {
        assertEquals(Set.of("name"), invalidFields(member(null, "jane@mailinator.com", "2125551234")));
        assertEquals(Set.of("name"), invalidFields(member("", "jane@mailinator.com", "2125551234")));
    }

    @Test
    public void nameAllowsUpTo25Characters() {
        assertEquals(Set.of(), invalidFields(member("A".repeat(25), "jane@mailinator.com", "2125551234")));
        assertEquals(Set.of("name"), invalidFields(member("A".repeat(26), "jane@mailinator.com", "2125551234")));
    }

    @Test
    public void nameMustNotContainDigits() {
        assertEquals(Set.of("name"), invalidFields(member("Jane Doe 2", "jane@mailinator.com", "2125551234")));
    }

    /** Characterization: the rule only rejects digits, although its message says "only letters and spaces". */
    @Test
    public void nameAcceptsSymbols() {
        assertEquals(Set.of(), invalidFields(member("@#!$", "jane@mailinator.com", "2125551234")));
    }

    // email

    @Test
    public void emailIsRequired() {
        assertEquals(Set.of("email"), invalidFields(member("Jane Doe", null, "2125551234")));
        assertEquals(Set.of("email"), invalidFields(member("Jane Doe", "", "2125551234")));
    }

    @Test
    public void emailMustBeWellFormed() {
        assertEquals(Set.of("email"), invalidFields(member("Jane Doe", "not-an-email", "2125551234")));
        assertEquals(Set.of("email"), invalidFields(member("Jane Doe", "jane@", "2125551234")));
    }

    /** Characterization: an address without a domain ending is accepted. */
    @Test
    public void emailAcceptsDomainWithoutDot() {
        assertEquals(Set.of(), invalidFields(member("Jane Doe", "jane@localhost", "2125551234")));
    }

    // phone number

    @Test
    public void phoneNumberIsRequired() {
        assertEquals(Set.of("phoneNumber"), invalidFields(member("Jane Doe", "jane@mailinator.com", null)));
    }

    @Test
    public void phoneNumberAllows10To12Digits() {
        assertEquals(Set.of(), invalidFields(member("Jane Doe", "jane@mailinator.com", "2125551234")));
        assertEquals(Set.of(), invalidFields(member("Jane Doe", "jane@mailinator.com", "442125551234")));
        assertEquals(Set.of("phoneNumber"), invalidFields(member("Jane Doe", "jane@mailinator.com", "212555123")));
        assertEquals(Set.of("phoneNumber"), invalidFields(member("Jane Doe", "jane@mailinator.com", "4421255512345")));
    }

    @Test
    public void phoneNumberMustBeNumeric() {
        assertEquals(Set.of("phoneNumber"), invalidFields(member("Jane Doe", "jane@mailinator.com", "212555CALL")));
        assertEquals(Set.of("phoneNumber"), invalidFields(member("Jane Doe", "jane@mailinator.com", "212-555-12")));
        assertEquals(Set.of("phoneNumber"), invalidFields(member("Jane Doe", "jane@mailinator.com", "212555.123")));
    }

    /**
     * Characterization: @Digits parses the text as a number, so values that are
     * not plain digit strings are accepted as long as they are 10-12 characters.
     */
    @Test
    public void phoneNumberAcceptsNumericFormatsThatAreNotPhoneNumbers() {
        assertEquals(Set.of(), invalidFields(member("Jane Doe", "jane@mailinator.com", "-212555121")));
        assertEquals(Set.of(), invalidFields(member("Jane Doe", "jane@mailinator.com", "+12125551212")));
        assertEquals(Set.of(), invalidFields(member("Jane Doe", "jane@mailinator.com", "2.1255E+10")));
    }

    private static Member member(String name, String email, String phoneNumber) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPhoneNumber(phoneNumber);
        return member;
    }

    private static Set<String> invalidFields(Member member) {
        return validator.validate(member).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
