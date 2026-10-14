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

import jakarta.validation.Valid;

import org.spring.as.quickstarts.kitchensink.model.Member;
import org.spring.as.quickstarts.kitchensink.service.DuplicateEmailException;
import org.spring.as.quickstarts.kitchensink.service.MemberRegistration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Registration page: shows the form and the member list, and registers new members (post/redirect/get).
 */
@Controller
public class MemberController {

    static final String VIEW = "index";

    private final MemberRegistration memberRegistration;

    public MemberController(MemberRegistration memberRegistration) {
        this.memberRegistration = memberRegistration;
    }

    /** The form never sets the id; ignoring it keeps posted ids from reaching the service. */
    @InitBinder("newMember")
    void ignoreId(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    @GetMapping("/")
    public String showRegistrationPage(Model model) {
        model.addAttribute("newMember", new Member());
        return registrationPage(model);
    }

    @PostMapping("/")
    public String register(@Valid @ModelAttribute("newMember") Member newMember, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return registrationPage(model);
        }
        try {
            memberRegistration.register(newMember);
        } catch (DuplicateEmailException e) {
            bindingResult.rejectValue("email", "email.taken", "Email taken");
            return registrationPage(model);
        }
        redirectAttributes.addFlashAttribute("registered", true);
        return "redirect:/";
    }

    private String registrationPage(Model model) {
        model.addAttribute("members", memberRegistration.findAllOrderedByName());
        return VIEW;
    }
}
