package com.intechcore.polarion.extension.admin_menu_organizer;

import ch.sbb.polarion.extension.generic.GenericUiServlet;

import java.io.Serial;

/**
 * Serves the React single-page app from its own webapp context ({@code admin-menu-organizer-app}). The admin
 * extenders in hivemodule.xml open it as
 * {@code /polarion/admin-menu-organizer-app/ui/app/index.html?feature=<id>}; everything else about the
 * request handling comes from the generic servlet.
 */
public class AdminMenuOrganizerAppServlet extends GenericUiServlet {

    @Serial
    private static final long serialVersionUID = 2741699502747533530L;

    public AdminMenuOrganizerAppServlet() {
        super("admin-menu-organizer-app");
    }
}
