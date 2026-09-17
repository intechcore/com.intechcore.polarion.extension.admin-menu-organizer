package com.intechcore.polarion.extension.admin_menu_organizer.settings;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderModes;
import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderRestorer;
import com.polarion.subterra.base.location.ILocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    /** The inherited equals compared only the name and the bundle timestamp, so different modes counted as equal. */
    @Test
    void comparesTheModes() {
        MenuOrderSettingsModel first = new MenuOrderSettingsModel();
        first.setFoldersAlphabetically(true);
        MenuOrderSettingsModel same = new MenuOrderSettingsModel();
        same.setFoldersAlphabetically(true);
        MenuOrderSettingsModel other = new MenuOrderSettingsModel();
        other.setFoldersAlphabetically(true);
        other.setEntriesInDeclarationOrder(true);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
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

    /**
     * Saving and renaming both reach save() with the target name set. Server start reads only Default, so
     * another name would change the menu now and silently lose the change at the next restart.
     */
    @Test
    void rejectsAnotherSettingName() {
        SettingsService settingsService = mock(SettingsService.class);
        MenuOrderSettings settings = new MenuOrderSettings(settingsService);
        SettingId id = SettingId.fromId("0b6a1c4e-other");
        MenuOrderSettingsModel modes = new MenuOrderSettingsModel();
        modes.setName("Other");
        modes.setFoldersAlphabetically(true);

        try (MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> settings.save("", id, modes));

            assertEquals("The administration menu order can only be stored in the Default setting", thrown.getMessage());
            verifyNoInteractions(settingsService);
            restorer.verifyNoInteractions();
        }
    }

    @Test
    void savesAndAppliesTheDefaultSetting() {
        SettingsService settingsService = mock(SettingsService.class);
        MenuOrderSettings settings = new MenuOrderSettings(settingsService);
        MenuOrderSettingsModel modes = new MenuOrderSettingsModel();
        modes.setName(NamedSettings.DEFAULT_NAME);
        modes.setEntriesInDeclarationOrder(true);

        try (MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            settings.save("", SettingId.fromId(NamedSettings.DEFAULT_NAME), modes);

            verify(settingsService).save(any(ILocation.class), anyString());
            restorer.verify(() -> MenuOrderRestorer.apply(new MenuOrderModes(false, true)));
        }
    }

    /** A deleted Default reads as the defaults, so the live menu must return to Polarion's own order at once. */
    @Test
    void returnsToPolarionsOrderWhenTheDefaultSettingIsDeleted() {
        SettingsService settingsService = mock(SettingsService.class);
        MenuOrderSettings settings = new MenuOrderSettings(settingsService);

        try (MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            settings.delete("", SettingId.fromId(NamedSettings.DEFAULT_NAME));

            verify(settingsService).delete(any(ILocation.class));
            restorer.verify(() -> MenuOrderRestorer.apply(new MenuOrderModes(false, false)));
        }
    }

    /** Deleting a setting other than Default, such as one stored before names were checked, keeps what Default holds. */
    @Test
    void keepsTheStoredDefaultWhenAnotherSettingIsDeleted() {
        SettingsService settingsService = mock(SettingsService.class);
        MenuOrderSettingsModel stored = new MenuOrderSettingsModel();
        stored.setName(NamedSettings.DEFAULT_NAME);
        stored.setFoldersAlphabetically(true);
        when(settingsService.read(any(ILocation.class), any())).thenReturn(stored.serialize());
        MenuOrderSettings settings = new MenuOrderSettings(settingsService);

        try (MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            settings.delete("", SettingId.fromId("0b6a1c4e-other"));

            verify(settingsService).delete(any(ILocation.class));
            restorer.verify(() -> MenuOrderRestorer.apply(new MenuOrderModes(true, false)));
        }
    }

    @Test
    void appliesTheModesAsSoonAsTheyAreSaved() {
        MenuOrderSettingsModel modes = new MenuOrderSettingsModel();
        modes.setFoldersAlphabetically(true);

        try (MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            new MenuOrderSettings(mock(SettingsService.class)).afterSave(modes);

            restorer.verify(() -> MenuOrderRestorer.apply(new MenuOrderModes(true, false)));
        }
    }
}
