package com.intechcore.polarion.extension.admin_menu_organizer;

import ch.sbb.polarion.extension.generic.GenericUiServlet;
import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderRestorer;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettings;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettingsModel;
import com.polarion.core.util.logging.Logger;
import jakarta.servlet.ServletException;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/**
 * Serves the React single-page app from its own webapp context ({@code admin-menu-organizer-app}). The admin
 * extenders in hivemodule.xml open it as
 * {@code /polarion/admin-menu-organizer-app/ui/app/index.html?feature=<id>}; everything else about the
 * request handling comes from the generic servlet.
 * <p>
 * Its {@code init()} is also where the administration menu order is applied at server start, which is why
 * {@code web.xml} declares this servlet with {@code load-on-startup}.
 */
public class AdminMenuOrganizerAppServlet extends GenericUiServlet {

    @Serial
    private static final long serialVersionUID = 2741699502747533530L;

    private static final Logger logger = Logger.getLogger(AdminMenuOrganizerAppServlet.class);

    public AdminMenuOrganizerAppServlet() {
        super("admin-menu-organizer-app");
    }

    @Override
    public void init() throws ServletException {
        super.init();
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
