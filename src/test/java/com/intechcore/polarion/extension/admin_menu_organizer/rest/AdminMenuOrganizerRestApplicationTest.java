package com.intechcore.polarion.extension.admin_menu_organizer.rest;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.rest.controller.info.ExtensionInfoApiController;
import ch.sbb.polarion.extension.generic.rest.controller.info.ExtensionInfoInternalController;
import ch.sbb.polarion.extension.generic.rest.controller.openapi.OpenApiController;
import ch.sbb.polarion.extension.generic.rest.controller.settings.NamedSettingsApiController;
import ch.sbb.polarion.extension.generic.rest.controller.settings.NamedSettingsInternalController;
import ch.sbb.polarion.extension.generic.rest.controller.swagger.SwaggerController;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.test_extensions.PlatformContextMockExtension;
import com.intechcore.polarion.extension.admin_menu_organizer.rest.controller.VersionApiController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({CurrentContextExtension.class, PlatformContextMockExtension.class})
@CurrentContextConfig("admin-menu-organizer")
class AdminMenuOrganizerRestApplicationTest {

    @BeforeEach
    @AfterEach
    void clearRegistry() {
        NamedSettingsRegistry.INSTANCE.getAll().clear();
    }

    @Test
    void servesTheInternalEndpointsOfTheUi() {
        Set<Class<?>> classes = new AdminMenuOrganizerRestApplication().getClasses();

        assertTrue(classes.contains(ExtensionInfoInternalController.class));
        assertTrue(classes.contains(NamedSettingsInternalController.class));
    }

    @Test
    void keepsOnlyTheVersionAndTheSpecificationInThePublicApi() {
        Set<Class<?>> classes = new AdminMenuOrganizerRestApplication().getClasses();

        assertTrue(classes.contains(VersionApiController.class));
        assertTrue(classes.contains(OpenApiController.class));
        assertTrue(classes.contains(SwaggerController.class));
        assertFalse(classes.contains(ExtensionInfoApiController.class));
        assertFalse(classes.contains(NamedSettingsApiController.class));
    }
}
