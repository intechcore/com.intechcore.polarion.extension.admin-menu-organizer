package com.intechcore.polarion.extension.admin_menu_organizer.rest.controller;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This class carries no code, only the annotation the swagger plugin turns into
 * {@code docs/openapi.json}. CI fails when the build changes that file, so the values are pinned
 * here as well: a change has to be deliberate.
 */
class OpenAPIInfoTest {

    @Test
    void describesTheExtensionApi() {
        OpenAPIDefinition definition = OpenAPIInfo.class.getAnnotation(OpenAPIDefinition.class);

        assertNotNull(definition);
        assertEquals("Admin Menu Organizer REST API", definition.info().title());
        assertEquals("v1", definition.info().version());
        assertEquals("polarion@intechcore.com", definition.info().contact().email());
        assertEquals("Apache License, Version 2.0", definition.info().license().name());
        assertNotNull(new OpenAPIInfo());
    }
}
