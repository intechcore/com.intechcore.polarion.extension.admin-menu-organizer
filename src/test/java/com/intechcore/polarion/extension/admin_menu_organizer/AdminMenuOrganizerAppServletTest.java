package com.intechcore.polarion.extension.admin_menu_organizer;

import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderRestorer;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettings;
import com.intechcore.polarion.extension.admin_menu_organizer.settings.MenuOrderSettingsModel;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the webapp context this servlet passes to {@code GenericUiServlet}.
 * <p>
 * That string is the whole behaviour of the class, and it has to stay identical to the resource
 * directory {@code src/main/resources/webapp/admin-menu-organizer-app/} and to every URL in
 * {@code META-INF/hivemodule.xml}: the base class serves a request only when its URI starts with
 * {@code /polarion/<webAppName>/ui/} and strips exactly that prefix to find the resource. A typo
 * there breaks resource serving at runtime, which is why this asserts the routing rather than the
 * class hierarchy the compiler already guarantees.
 */
class AdminMenuOrganizerAppServletTest {

    private AdminMenuOrganizerAppServlet servlet;
    private ServletContext servletContext;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        servletContext = mock(ServletContext.class);
        ServletConfig servletConfig = mock(ServletConfig.class);
        when(servletConfig.getServletContext()).thenReturn(servletContext);

        servlet = new AdminMenuOrganizerAppServlet();
        servlet.init(servletConfig);
        response = mock(HttpServletResponse.class);
    }

    private HttpServletRequest requestFor(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void servesFromTheAppWebappContext() throws Exception {
        when(servletContext.getResourceAsStream(anyString())).thenReturn(null);

        servlet.service(requestFor("/polarion/admin-menu-organizer-app/ui/app/index.html"), response);

        // The context prefix is stripped, so what is looked up is the path inside the webapp.
        verify(servletContext).getResourceAsStream("/app/index.html");
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void rejectsAnotherWebappContext() {
        // The extension's other context: a request meant for it must not be served from this app.
        HttpServletRequest request = requestFor("/polarion/admin-menu-organizer/ui/app/index.html");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> servlet.service(request, response));

        assertEquals("Unsupported resource path", thrown.getMessage());
    }

    /**
     * Outside a running Polarion there is no provider to reorder, and the servlet must still load.
     */
    @Test
    void initAppliesTheMenuOrderWithoutThrowing() {
        AdminMenuOrganizerAppServlet freshServlet = new AdminMenuOrganizerAppServlet();

        Executable init = freshServlet::init;

        assertDoesNotThrow(init);
    }

    /**
     * The restorer references Polarion's administration classes, so it can fail to link on a future
     * Polarion. That must never stop the extension's pages from loading.
     */
    @Test
    void initSurvivesAFailingMenuOrderApply() {
        AdminMenuOrganizerAppServlet freshServlet = new AdminMenuOrganizerAppServlet();

        try (MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            restorer.when(() -> MenuOrderRestorer.applyAtStartup(false, false))
                    .thenThrow(new NoClassDefFoundError("com/polarion/alm/administration/web/server/AdministrationPageExtenderProvider"));

            Executable init = freshServlet::init;

            assertDoesNotThrow(init);
        }
    }

    @Test
    void initAppliesTheStoredModes() {
        MenuOrderSettingsModel stored = new MenuOrderSettingsModel();
        stored.setFoldersAlphabetically(true);
        stored.setEntriesInDeclarationOrder(true);
        AdminMenuOrganizerAppServlet freshServlet = new AdminMenuOrganizerAppServlet();

        try (MockedConstruction<MenuOrderSettings> settings = mockConstruction(MenuOrderSettings.class,
                (mock, context) -> when(mock.readAsSystemUser()).thenReturn(stored));
             MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            Executable init = freshServlet::init;

            assertDoesNotThrow(init);
            restorer.verify(() -> MenuOrderRestorer.applyAtStartup(true, true));
        }
    }

    /** At server start the repository may not answer yet; the menu must still be checked, with both modes off. */
    @Test
    void initTreatsUnreadableSettingsAsBothModesOff() {
        AdminMenuOrganizerAppServlet freshServlet = new AdminMenuOrganizerAppServlet();

        try (MockedConstruction<MenuOrderSettings> settings = mockConstruction(MenuOrderSettings.class,
                (mock, context) -> when(mock.readAsSystemUser()).thenThrow(new IllegalStateException("repository is not ready")));
             MockedStatic<MenuOrderRestorer> restorer = mockStatic(MenuOrderRestorer.class)) {
            Executable init = freshServlet::init;

            assertDoesNotThrow(init);
            restorer.verify(() -> MenuOrderRestorer.applyAtStartup(false, false));
        }
    }
}
