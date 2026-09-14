package com.intechcore.polarion.extension.admin_menu_organizer;

import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderRestorer;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettings;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettingsModel;
import com.polarion.core.util.logging.Logger;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the stored administration menu order when the app webapp starts. {@code web.xml} declares this
 * listener, so the order is applied once per server start, apart from the servlet that serves the UI.
 */
public class MenuOrderStartupListener implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(MenuOrderStartupListener.class);

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            MenuOrderSettingsModel modes = readModes();
            MenuOrderRestorer.applyAtStartup(modes.isFoldersAlphabetically(), modes.isEntriesInDeclarationOrder());
        } catch (Exception | LinkageError e) {
            // The restorer guards its own body, but loading it can fail before that body runs.
            logger.warn("Could not apply the administration menu order", e);
        }
    }

    private static @NotNull MenuOrderSettingsModel readModes() {
        try {
            return new MenuOrderSettings().readAsSystemUser();
        } catch (Exception e) {
            logger.warn("Could not read the administration menu order settings, both modes are treated as off", e);
            return new MenuOrderSettingsModel();
        }
    }
}
