package com.intechcore.polarion.extension.admin_menu_organizer.rest;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettings;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.core.PlatformContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * The extension has no REST controller of its own: its only contribution to the API is the
 * menu-order setting, registered here. Without the registration every settings endpoint answers
 * 404 for it, which is a runtime symptom of a constructor that is easy to drop.
 */
@ExtendWith(CurrentContextExtension.class)
@CurrentContextConfig("admin-menu-organizer")
class AdminMenuOrganizerRestApplicationTest {

    @Test
    void registersTheMenuOrderSetting() {
        // The settings object builds generic's SettingsService, which asks for the platform.
        try (MockedStatic<PlatformContext> platform = mockStatic(PlatformContext.class)) {
            platform.when(PlatformContext::getPlatform).thenReturn(mock(IPlatform.class, RETURNS_DEEP_STUBS));
            new AdminMenuOrganizerRestApplication();
        }

        assertNotNull(NamedSettingsRegistry.INSTANCE.getByFeatureName(MenuOrderSettings.FEATURE_NAME));
        assertInstanceOf(MenuOrderSettings.class, NamedSettingsRegistry.INSTANCE.getByFeatureName(MenuOrderSettings.FEATURE_NAME));
    }
}
