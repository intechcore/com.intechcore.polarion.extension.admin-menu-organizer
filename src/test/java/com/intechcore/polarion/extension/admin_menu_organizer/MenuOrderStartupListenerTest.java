package com.intechcore.polarion.extension.admin_menu_organizer;

import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderRestorer;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettings;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettingsModel;
import jakarta.servlet.ServletContextEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MenuOrderStartupListenerTest {

    private final MenuOrderStartupListener listener = new MenuOrderStartupListener();
    private final ServletContextEvent event = mock(ServletContextEvent.class);

    /** Outside a running Polarion there is no provider to reorder, and the webapp must still start. */
    @Test
    void appliesTheMenuOrderWithoutThrowing() {
        Executable start = () -> listener.contextInitialized(event);

        assertDoesNotThrow(start);
    }

    /**
     * The restorer references Polarion's administration classes, so it can fail to link on a future
     * Polarion. That must never stop the extension's webapp from starting.
     */
    @Test
    void survivesAFailingMenuOrderApply() {
        try (MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            restorer.when(() -> MenuOrderRestorer.applyAtStartup(false, false))
                    .thenThrow(new NoClassDefFoundError("com/polarion/alm/administration/web/server/AdministrationPageExtenderProvider"));

            Executable start = () -> listener.contextInitialized(event);

            assertDoesNotThrow(start);
        }
    }

    @Test
    void appliesTheStoredModes() {
        MenuOrderSettingsModel stored = new MenuOrderSettingsModel();
        stored.setFoldersAlphabetically(true);
        stored.setEntriesInDeclarationOrder(true);

        try (MockedConstruction<MenuOrderSettings> settings = mockConstruction(MenuOrderSettings.class,
                (mock, context) -> when(mock.readAsSystemUser()).thenReturn(stored));
             MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            Executable start = () -> listener.contextInitialized(event);

            assertDoesNotThrow(start);
            restorer.verify(() -> MenuOrderRestorer.applyAtStartup(true, true));
        }
    }

    /** At server start the repository may not answer yet; the menu must still be checked, with both modes off. */
    @Test
    void treatsUnreadableSettingsAsBothModesOff() {
        try (MockedConstruction<MenuOrderSettings> settings = mockConstruction(MenuOrderSettings.class,
                (mock, context) -> when(mock.readAsSystemUser()).thenThrow(new IllegalStateException("repository is not ready")));
             MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            Executable start = () -> listener.contextInitialized(event);

            assertDoesNotThrow(start);
            restorer.verify(() -> MenuOrderRestorer.applyAtStartup(false, false));
        }
    }
}
