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
package org.spring.quickstarts.kitchensink.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps the JSF-era page URLs working: the old entry points redirect permanently to the registration page.
 */
@Configuration
public class LegacyUrlConfig implements WebMvcConfigurer {

    /** {@code index.jsf} was the JSF page; {@code index.html} was a meta refresh to it. */
    static final String[] LEGACY_PAGES = { "/index.jsf", "/index.html" };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String legacyPage : LEGACY_PAGES) {
            registry.addRedirectViewController(legacyPage, "/").setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        }
    }
}
