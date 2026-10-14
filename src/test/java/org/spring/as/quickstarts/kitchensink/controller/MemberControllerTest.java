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
package org.spring.as.quickstarts.kitchensink.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.spring.as.quickstarts.kitchensink.data.MemberRepository;
import org.spring.as.quickstarts.kitchensink.model.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the registration page through MockMvc, against the real service and in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberControllerTest {

    private static final String SEED_EMAIL = "john.smith@mailinator.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void pageShowsFormAndMemberList() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("newMember", "members"))
                .andExpect(content().string(containsString("id=\"reg\"")))
                .andExpect(content().string(containsString(SEED_EMAIL)));
    }

    @Test
    void validRegistrationRedirectsWithSuccessMessage() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/").param("name", "Web User").param("email", email).param("phoneNumber", "2125550101"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("registered", true));

        assertEquals(1, memberRepository.findAll().stream().filter(m -> email.equals(m.getEmail())).count());
    }

    @Test
    void invalidRegistrationShowsAnErrorForEachField() throws Exception {
        mockMvc.perform(post("/").param("name", "R2D2").param("email", "not-an-email").param("phoneNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("newMember", "name", "email", "phoneNumber"))
                .andExpect(model().attributeExists("members"))
                .andExpect(content().string(containsString("must contain only letters and spaces")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));
    }

    @Test
    void duplicateEmailShowsFieldErrorInsteadOfDatabaseError() throws Exception {
        mockMvc.perform(post("/").param("name", "Johnny Smith").param("email", SEED_EMAIL).param("phoneNumber", "2125550102"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrorCode("newMember", "email", "email.taken"))
                .andExpect(content().string(containsString("Email taken")))
                .andExpect(content().string(not(containsString("Unique index or primary key violation"))))
                .andExpect(content().string(not(containsString("CONSTRAINT_INDEX"))));
    }

    @Test
    void duplicateEmailMessageIsLocalized() throws Exception {
        mockMvc.perform(post("/").header("Accept-Language", "fr")
                .param("name", "Johnny Smith").param("email", SEED_EMAIL).param("phoneNumber", "2125550102"))
                .andExpect(content().string(containsString("Cet e-mail est d")));
    }

    @Test
    void postedIdIsIgnored() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/").param("id", "0").param("name", "Mallory").param("email", email)
                .param("phoneNumber", "2125550104"))
                .andExpect(status().is3xxRedirection());

        Member registered = memberRepository.findAll().stream()
                .filter(m -> email.equals(m.getEmail())).findFirst().orElseThrow();
        assertNotEquals(0L, registered.getId());
        assertEquals("John Smith", memberRepository.findById(0L).orElseThrow().getName());
    }

    @Test
    void pageIsLocalizedFromAcceptLanguage() throws Exception {
        mockMvc.perform(get("/").header("Accept-Language", "fr"))
                .andExpect(content().string(containsString("Bienvenue !")))
                .andExpect(content().string(containsString("lang=\"fr\"")));
    }

    @Test
    void legacyJsfUrlsRedirectPermanentlyToThePage() throws Exception {
        for (String legacyPage : LegacyUrlConfig.LEGACY_PAGES) {
            mockMvc.perform(get(legacyPage))
                    .andExpect(status().isMovedPermanently())
                    .andExpect(redirectedUrl("/"));
        }
    }

    private static String uniqueEmail() {
        return "web-" + UUID.randomUUID().toString().substring(0, 8) + "@mailinator.com";
    }
}
