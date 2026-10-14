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
package org.jboss.as.quickstarts.kitchensink.controller;

import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;

import org.jboss.as.quickstarts.kitchensink.data.MemberRepository;
import org.jboss.as.quickstarts.kitchensink.model.Member;
import org.jboss.as.quickstarts.kitchensink.service.MemberRegistration;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Web controller for the registration page: shows the form and the member list, and registers new members.
 */
@Controller
public class MemberController {

    private final MemberRegistration memberRegistration;

    private final MemberRepository memberRepository;

    private final MessageSource messageSource;

    public MemberController(MemberRegistration memberRegistration, MemberRepository memberRepository,
            MessageSource messageSource) {
        this.memberRegistration = memberRegistration;
        this.memberRepository = memberRepository;
        this.messageSource = messageSource;
    }

    @ModelAttribute("members")
    public List<Member> members() {
        return memberRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("newMember", new Member());
        return "index";
    }

    @PostMapping("/")
    public String register(@Valid @ModelAttribute("newMember") Member newMember, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes, Locale locale) {
        if (bindingResult.hasErrors()) {
            return "index";
        }
        try {
            memberRegistration.register(newMember);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("registeredMessage", null, locale));
            return "redirect:/";
        } catch (Exception e) {
            model.addAttribute("errorMessage", getRootErrorMessage(e, locale));
            return "index";
        }
    }

    private String getRootErrorMessage(Exception e, Locale locale) {
        // Default to general error message that registration failed.
        String errorMessage = messageSource.getMessage("defaultErrorMessage", null, locale);
        if (e == null) {
            // This shouldn't happen, but return the default messages
            return errorMessage;
        }

        // Start with the exception and recurse to find the root cause
        Throwable t = e;
        while (t != null) {
            // Get the message from the Throwable class instance
            errorMessage = t.getLocalizedMessage();
            t = t.getCause();
        }
        // This is the root cause message
        return errorMessage;
    }
}
