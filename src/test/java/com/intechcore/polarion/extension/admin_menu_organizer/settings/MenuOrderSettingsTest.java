package com.intechcore.polarion.extension.admin_menu_organizer.settings;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderRestorer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(CurrentContextExtension.class)
@CurrentContextConfig("admin-menu-organizer")
class MenuOrderSettingsTest {

    @Test
    void keepsPolarionsOrderByDefault() {
        MenuOrderSettingsModel defaults = new MenuOrderSettings(mock(SettingsService.class)).defaultValues();

        assertFalse(defaults.isFoldersAlphabetically());
        assertFalse(defaults.isEntriesInDeclarationOrder());
    }

    @Test
    void storesBothModes() {
        MenuOrderSettingsModel stored = new MenuOrderSettingsModel();
        stored.setFoldersAlphabetically(true);
        stored.setEntriesInDeclarationOrder(false);

        MenuOrderSettingsModel restored = new MenuOrderSettingsModel();
        restored.deserialize(stored.serialize());

        assertTrue(restored.isFoldersAlphabetically());
        assertFalse(restored.isEntriesInDeclarationOrder());
    }

    @Test
    void readsAMissingModeAsOff() {
        MenuOrderSettingsModel restored = new MenuOrderSettingsModel();
        restored.deserialize("-----BEGIN ENTRIES IN DECLARATION ORDER-----\ntrue\n-----END ENTRIES IN DECLARATION ORDER-----\n");

        assertFalse(restored.isFoldersAlphabetically());
        assertTrue(restored.isEntriesInDeclarationOrder());
    }

    @Test
    void rejectsAProjectScope() {
        SettingsService settingsService = mock(SettingsService.class);
        MenuOrderSettings settings = new MenuOrderSettings(settingsService);
        SettingId id = SettingId.fromName(NamedSettings.DEFAULT_NAME);
        MenuOrderSettingsModel modes = new MenuOrderSettingsModel();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> settings.save("project/elibrary/", id, modes));

        assertEquals("The administration menu order can only be set in the global scope", thrown.getMessage());
        verifyNoInteractions(settingsService);
    }

    @Test
    void appliesTheModesAsSoonAsTheyAreSaved() {
        MenuOrderSettingsModel modes = new MenuOrderSettingsModel();
        modes.setFoldersAlphabetically(true);

        try (MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            new MenuOrderSettings(mock(SettingsService.class)).afterSave(modes);

            restorer.verify(() -> MenuOrderRestorer.apply(true, false));
        }
    }
}
