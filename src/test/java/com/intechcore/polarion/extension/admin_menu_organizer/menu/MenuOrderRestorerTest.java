package com.intechcore.polarion.extension.admin_menu_organizer.menu;

import com.polarion.alm.administration.web.server.AdministrationPageExtender;
import com.polarion.alm.administration.web.server.AdministrationPageExtenderProvider;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.hivemind.IHiveMindPlatform;
import org.apache.hivemind.Registry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuOrderRestorerTest {

    private static AdministrationPageExtender realExtender(String id) {
        return realExtender("ext", "Ext", id);
    }

    private static AdministrationPageExtender realExtender(String parentNodeId, String parentNodeName, String id) {
        AdministrationPageExtender extender = new AdministrationPageExtender();
        extender.setId(id);
        extender.setParentNodeId(parentNodeId);
        extender.setParentNodeName(parentNodeName);
        return extender;
    }

    /**
     * Reads the entries the provider holds. This class writes them through the provider's own setter and
     * reads nothing back, so the only reflection left in the extension is here, in the test that checks
     * the effect.
     */
    @SuppressWarnings({"unchecked", "java:S3011"})
    private static List<AdministrationPageExtender> liveOrderOf(AdministrationPageExtenderProvider provider) throws Exception {
        Field field = AdministrationPageExtenderProvider.class.getDeclaredField("extenders");
        field.setAccessible(true);
        return (List<AdministrationPageExtender>) field.get(provider);
    }

    /** Fills the provider the way Polarion 2606 does: the declared entries through Set.copyOf. */
    private static AdministrationPageExtenderProvider providerFilledByPolarion(List<AdministrationPageExtender> declaredOrder) {
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(Set.copyOf(declaredOrder));
        return provider;
    }

    /** A platform carrying a HiveMind registry, which is where the declared order lives. */
    private static IHiveMindPlatform platformWith(Registry registry) {
        IHiveMindPlatform platform = mock(IHiveMindPlatform.class);
        when(platform.getRegistry()).thenReturn(registry);
        return platform;
    }

    /** A registry answering the administration menu configuration point with the given entries. */
    private static Registry registryHolding(List<?> configuration) {
        Registry registry = mock(Registry.class);
        when(registry.getConfiguration(MenuOrderRestorer.CONFIG_ID)).thenReturn(configuration);
        return registry;
    }

    // --- The one Polarion behavior the whole class rests on ---

    /**
     * Pins the contract of the setter this class writes through: the entries are kept in the iteration
     * order of the given set. A Polarion upgrade that sorts them, or copies them into a set of its own,
     * fails here at build time instead of degrading to the randomized menu this class exists to fix.
     */
    @Test
    void providerKeepsTheOrderOfTheGivenSet() throws Exception {
        List<AdministrationPageExtender> entries = List.of(realExtender("css"), realExtender("about"), realExtender("webhooks"));
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();

        provider.setAdministrationPageExtenders(new LinkedHashSet<>(entries));

        assertEquals(entries, liveOrderOf(provider));
    }

    /**
     * Switching the modes off relies on recomputing Polarion's order instead of storing it, so the
     * recomputation must match what the provider received.
     */
    @Test
    void reproducesTheOrderPolarionBuilds() throws Exception {
        List<AdministrationPageExtender> declaredOrder = List.of(realExtender("a"), realExtender("b"), realExtender("c"), realExtender("d"), realExtender("e"));

        AdministrationPageExtenderProvider provider = providerFilledByPolarion(declaredOrder);

        assertEquals(MenuOrderPlanner.polarionOrder(declaredOrder), liveOrderOf(provider));
    }

    // --- Reading the declared order ---

    @Test
    void readsTheDeclaredOrderFromTheRegistry() {
        List<Object> configuration = List.of(realExtender("about"), realExtender("css"));
        Registry registry = registryHolding(configuration);

        List<?> declaredOrder = MenuOrderRestorer.readDeclaredOrder(platformWith(registry));

        assertSame(configuration, declaredOrder);
        // Asking for the wrong configuration point would silently return someone else's entries.
        verify(registry).getConfiguration(MenuOrderRestorer.CONFIG_ID);
    }

    /** A platform that is not a HiveMind one stands in for a future Polarion without that registry. */
    @Test
    void readsNoDeclaredOrderWithoutAHiveMindRegistry() {
        assertNull(MenuOrderRestorer.readDeclaredOrder(mock(IPlatform.class)));
        assertNull(MenuOrderRestorer.readDeclaredOrder(platformWith(null)));
    }

    @Test
    void readsNoDeclaredOrderWhenTheConfigurationIsEmpty() {
        // An empty configuration means the entries are not there to reorder, not that the order is empty.
        assertNull(MenuOrderRestorer.readDeclaredOrder(platformWith(registryHolding(List.of()))));
    }

    /** Only extenders may reach the provider, so a configuration holding anything else is not used. */
    @Test
    void readsNoDeclaredOrderWhenTheConfigurationHoldsOtherTypes() {
        List<Object> mixed = List.of(realExtender("about"), "not an extender");

        assertNull(MenuOrderRestorer.readDeclaredOrder(platformWith(registryHolding(mixed))));
    }

    /**
     * Without Polarion's Guice injector there is nothing to inject the provider from, and the class must
     * say so rather than fail.
     */
    @Test
    void looksUpNoProviderWithoutAGuicePlatform() {
        assertNull(MenuOrderRestorer.lookupProvider());
    }

    /**
     * The entry points are called from the startup listener and from a settings save, where an exception
     * would fail the webapp start or the request. Outside a running Polarion there is no platform at all,
     * and they still must not throw.
     */
    @Test
    void neverThrowsOutsideARunningPolarion() {
        Executable atStartup = () -> MenuOrderRestorer.applyAtStartup(true, true);
        Executable afterSave = () -> MenuOrderRestorer.apply(true, true);

        assertDoesNotThrow(atStartup);
        assertDoesNotThrow(afterSave);
    }

    // --- The whole pipeline, driven with Polarion's real provider and a stand-in platform ---

    @Test
    void ordersTheProvidersEntriesByDeclaration() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        AdministrationPageExtender css = realExtender("css");
        AdministrationPageExtender webhooks = realExtender("webhooks");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(css, webhooks, about)));

        MenuOrderRestorer.apply(provider, platformWith(registryHolding(List.of(about, css, webhooks))), false, false, true);

        assertEquals("ext/about, ext/css, ext/webhooks", MenuOrderRestorer.describe(liveOrderOf(provider)));
    }

    @Test
    void returnsToPolarionsOrderWhenTheModesAreSwitchedOff() throws Exception {
        List<AdministrationPageExtender> declaredOrder = List.of(realExtender("a"), realExtender("b"), realExtender("c"), realExtender("d"), realExtender("e"));
        AdministrationPageExtenderProvider provider = providerFilledByPolarion(declaredOrder);
        List<AdministrationPageExtender> polarionOrder = new ArrayList<>(liveOrderOf(provider));
        IPlatform platform = platformWith(registryHolding(declaredOrder));

        MenuOrderRestorer.apply(provider, platform, false, false, true);
        assertEquals(declaredOrder, liveOrderOf(provider));
        MenuOrderRestorer.apply(provider, platform, false, false, false);

        assertEquals(polarionOrder, liveOrderOf(provider));
    }

    @Test
    void changesNothingAtStartupWithBothModesOff() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        AdministrationPageExtender css = realExtender("css");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(css, about)));
        List<AdministrationPageExtender> before = new ArrayList<>(liveOrderOf(provider));

        MenuOrderRestorer.apply(provider, platformWith(registryHolding(List.of(about, css))), true, false, false);

        assertEquals(before, liveOrderOf(provider));
    }

    @Test
    void appliesTheModesAtStartup() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        AdministrationPageExtender css = realExtender("css");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(css, about)));

        MenuOrderRestorer.apply(provider, platformWith(registryHolding(List.of(about, css))), true, false, true);

        assertEquals("ext/about, ext/css", MenuOrderRestorer.describe(liveOrderOf(provider)));
    }

    @Test
    void appliesTheSameOrderOnASecondRun() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        AdministrationPageExtender css = realExtender("css");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(css, about)));
        IPlatform platform = platformWith(registryHolding(List.of(about, css)));

        MenuOrderRestorer.apply(provider, platform, false, true, true);
        List<AdministrationPageExtender> afterFirstRun = new ArrayList<>(liveOrderOf(provider));
        MenuOrderRestorer.apply(provider, platform, false, true, true);

        assertEquals(afterFirstRun, liveOrderOf(provider));
    }

    /** Every entry of every extension reaches the provider, whatever the order the modes ask for. */
    @Test
    void keepsEveryEntryOfEveryExtension() throws Exception {
        List<AdministrationPageExtender> declaredOrder = List.of(
                realExtender("ext-a", "Extension A", "about"),
                realExtender("ext-a", "Extension A", "css"),
                realExtender("ext-b", "Extension B", "about"),
                realExtender("ext-b", "Extension B", "settings"),
                realExtender("core", "Core", "properties"));
        AdministrationPageExtenderProvider provider = providerFilledByPolarion(declaredOrder);

        MenuOrderRestorer.apply(provider, platformWith(registryHolding(declaredOrder)), false, true, true);

        List<AdministrationPageExtender> liveOrder = liveOrderOf(provider);
        assertEquals(declaredOrder.size(), liveOrder.size());
        assertEquals(Set.copyOf(declaredOrder), Set.copyOf(liveOrder));
    }

    /**
     * The registry's entries are written over whatever the provider holds. Reading the provider's own
     * list back, to apply a permutation of it instead, is what the reflective version did; the entries
     * written are the configuration Polarion fills the provider from.
     */
    @Test
    void writesTheRegistrysEntriesOverTheProvidersOwn() throws Exception {
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(realExtender("css"), realExtender("about"))));
        // Same ids, different instances.
        List<AdministrationPageExtender> registryOrder = List.of(realExtender("about"), realExtender("css"));

        MenuOrderRestorer.apply(provider, platformWith(registryHolding(registryOrder)), false, true, true);

        assertEquals(registryOrder, liveOrderOf(provider));
    }

    @Test
    void leavesTheProvidersEntriesAloneWhenTheConfigurationIsUnreadable() throws Exception {
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(realExtender("css"), realExtender("about"))));

        MenuOrderRestorer.apply(provider, mock(IPlatform.class), false, true, true);

        assertEquals("ext/css, ext/about", MenuOrderRestorer.describe(liveOrderOf(provider)));
    }

    // --- Logging the result ---

    @Test
    void describesRealEntriesByParentAndId() {
        AdministrationPageExtender extender = new AdministrationPageExtender();
        extender.setId("style-package");
        extender.setParentNodeId("pdf-export");

        assertEquals("pdf-export/style-package", MenuOrderRestorer.describe(List.of(extender)));
    }

    @Test
    void describesEntriesInOrder() {
        assertEquals("ext/a, ext/b", MenuOrderRestorer.describe(List.of(realExtender("a"), realExtender("b"))));
    }
}
