package com.intechcore.polarion.extension.admin_menu_organizer.menu;

import com.intechcore.polarion.extension.admin_menu_organizer.menu.MenuOrderRestorer.Outcome;
import com.polarion.alm.administration.web.server.AdministrationPageExtender;
import com.polarion.alm.administration.web.server.AdministrationPageExtenderProvider;
import com.polarion.platform.core.IPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOrderRestorerTest {

    /**
     * Stands in for {@code AdministrationPageExtender}, which is what makes the reordering necessary:
     * it implements neither {@code equals} nor {@code hashCode}, so identity is all there is.
     */
    @SuppressWarnings("ClassCanBeRecord")
    private static final class Entry {
        private final String name;

        Entry(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static List<Object> entries(String... names) {
        List<Object> result = new ArrayList<>();
        for (String name : names) {
            result.add(new Entry(name));
        }
        return result;
    }

    private static List<String> names(List<?> entries) {
        return entries.stream().map(String::valueOf).toList();
    }

    private static List<Object> shuffled(List<Object> source, int... indices) {
        List<Object> result = new ArrayList<>();
        for (int index : indices) {
            result.add(source.get(index));
        }
        return result;
    }

    @Test
    void appliesTheDeclaredOrder() {
        List<Object> declared = entries("about", "user-guide", "css", "webhooks");
        List<Object> live = shuffled(declared, 2, 0, 3, 1);

        assertEquals(Outcome.APPLIED, MenuOrderRestorer.restore(live, declared));
        assertEquals(List.of("about", "user-guide", "css", "webhooks"), names(live));
    }

    @Test
    void keepsTheSameInstances() {
        List<Object> declared = entries("about", "css");
        List<Object> live = shuffled(declared, 1, 0);

        MenuOrderRestorer.restore(live, declared);

        assertSame(declared.get(0), live.get(0));
        assertSame(declared.get(1), live.get(1));
    }

    @Test
    void neverChangesTheSize() {
        List<Object> declared = entries("a", "b", "c");
        List<Object> live = shuffled(declared, 2, 1, 0);

        MenuOrderRestorer.restore(live, declared);

        assertEquals(3, live.size());
    }

    /**
     * A second run, at startup or after a save, finds nothing to change and must report so rather than
     * rewrite the list.
     */
    @Test
    void reportsAlreadyOrderedOnASecondCall() {
        List<Object> declared = entries("about", "user-guide", "css");
        List<Object> live = shuffled(declared, 1, 2, 0);

        assertEquals(Outcome.APPLIED, MenuOrderRestorer.restore(live, declared));
        List<String> once = names(live);
        assertEquals(Outcome.ALREADY_ORDERED, MenuOrderRestorer.restore(live, declared));

        assertEquals(once, names(live));
    }

    @Test
    void leavesAnAlreadyOrderedListUntouched() {
        List<Object> declared = entries("about", "css");
        List<Object> live = new ArrayList<>(declared);

        assertEquals(Outcome.ALREADY_ORDERED, MenuOrderRestorer.restore(live, declared));
        assertEquals(names(declared), names(live));
    }

    @Test
    void rejectsListsOfDifferentSize() {
        List<Object> declared = entries("about", "css", "webhooks");
        List<Object> live = shuffled(declared, 1, 0);

        assertEquals(Outcome.MISMATCH, MenuOrderRestorer.restore(live, declared));
        assertEquals(List.of("css", "about"), names(live));
    }

    @Test
    void rejectsListsHoldingDifferentEntries() {
        List<Object> declared = entries("about", "css");
        List<Object> live = entries("css", "about");
        Object firstBefore = live.getFirst();

        // Equal-looking but distinct instances: nothing may be replaced.
        assertEquals(Outcome.MISMATCH, MenuOrderRestorer.restore(live, declared));
        assertSame(firstBefore, live.getFirst());
        assertEquals(List.of("css", "about"), names(live));
    }

    @Test
    void rejectsARepeatedInstance() {
        List<Object> declared = entries("about", "css");
        List<Object> live = new ArrayList<>(List.of(declared.getFirst(), declared.getFirst()));

        assertEquals(Outcome.MISMATCH, MenuOrderRestorer.restore(live, declared));
    }

    /**
     * The shape the guard exists for: identity sets collapse repeats, so equal sets over equal-sized
     * lists would otherwise let one entry be dropped and another written twice.
     */
    @Test
    void rejectsARepeatedInstanceOnBothSides() {
        List<Object> pair = entries("about", "css");
        Object about = pair.getFirst();
        Object css = pair.get(1);
        List<Object> live = new ArrayList<>(List.of(about, about, css));
        List<Object> declared = new ArrayList<>(List.of(about, css, css));

        assertEquals(Outcome.MISMATCH, MenuOrderRestorer.restore(live, declared));
        assertEquals(List.of("about", "about", "css"), names(live));
    }

    @Test
    void handlesEmptyLists() {
        List<Object> live = new ArrayList<>();

        assertEquals(Outcome.ALREADY_ORDERED, MenuOrderRestorer.restore(live, List.of()));
        assertTrue(live.isEmpty());
    }

    @Test
    void reordersEntriesOfEveryExtensionAtOnce() {
        // Two extensions plus a Polarion-provided entry, declared in this order.
        List<Object> declared = entries("ext-a/about", "ext-a/css", "ext-b/about", "ext-b/settings", "core/properties");
        List<Object> live = shuffled(declared, 3, 0, 4, 2, 1);

        assertEquals(Outcome.APPLIED, MenuOrderRestorer.restore(live, declared));
        assertEquals(names(declared), names(live));
    }

    @Test
    void describesRealEntriesByParentAndId() {
        AdministrationPageExtender extender = new AdministrationPageExtender();
        extender.setId("style-package");
        extender.setParentNodeId("pdf-export");

        assertEquals("pdf-export/style-package", MenuOrderRestorer.describe(List.of(extender)));
    }

    @Test
    void describesEntriesInOrder() {
        assertEquals("a, b", MenuOrderRestorer.describe(entries("a", "b")));
    }

    /**
     * Pins the one reflective name that can silently break this class against Polarion's real type. If a
     * Polarion upgrade renames or retypes the field, this fails at build time instead of degrading to a
     * WARN on a production server, where the symptom is the randomized menu this class exists to fix.
     */
    @Test
    @SuppressWarnings("java:S3011")
    void providerStillDeclaresTheExtendersListField() throws Exception {
        Field field = AdministrationPageExtenderProvider.class.getDeclaredField("extenders");
        field.setAccessible(true);

        assertInstanceOf(List.class, field.get(new AdministrationPageExtenderProvider()));
    }

    /**
     * The provider must keep taking entries through this setter and keep them in a mutable list, which is
     * what lets the order be corrected in place.
     */
    @Test
    @SuppressWarnings("unchecked")
    void providerKeepsItsEntriesInAMutableList() throws Exception {
        AdministrationPageExtender extender = new AdministrationPageExtender();
        extender.setId("about");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(extender)));

        Field field = AdministrationPageExtenderProvider.class.getDeclaredField("extenders");
        field.setAccessible(true);
        List<Object> entries = (List<Object>) field.get(provider);

        assertEquals(1, entries.size());
        assertSame(extender, entries.get(0));
        assertDoesNotThrow(() -> entries.set(0, extender), "the entry list must be writable in place");
    }

    // --- The reflective half: the paths that break on a Polarion upgrade ---

    /** Stands in for the platform: {@code IPlatform} declares one method, the rest is found by name. */
    static final class FakePlatform implements IPlatform {
        private final Object registry;

        FakePlatform(Object registry) {
            this.registry = registry;
        }

        @Override
        public <T> T lookupService(Class<T> serviceClass) {
            return null;
        }

        public Object getRegistry() {
            return registry;
        }
    }

    /** Stands in for the HiveMind registry, recording which configuration point was asked for. */
    static final class FakeRegistry {
        private final Object configuration;
        private String requestedId;

        FakeRegistry(Object configuration) {
            this.configuration = configuration;
        }

        public Object getConfiguration(String configurationPointId) {
            this.requestedId = configurationPointId;
            return configuration;
        }
    }

    /** A platform without the registry accessor, standing in for a future Polarion that drops it. */
    static final class PlatformWithoutRegistry implements IPlatform {
        @Override
        public <T> T lookupService(Class<T> serviceClass) {
            return null;
        }
    }

    @Test
    void readsTheProvidersOwnEntryList() throws Exception {
        AdministrationPageExtender extender = new AdministrationPageExtender();
        extender.setId("about");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(extender)));

        List<Object> liveOrder = MenuOrderRestorer.readLiveOrder(provider);

        assertNotNull(liveOrder);
        assertSame(extender, liveOrder.getFirst());
        // Must be the provider's own list, not a copy: the order is corrected in place.
        liveOrder.set(0, extender);
        assertSame(liveOrder, MenuOrderRestorer.readLiveOrder(provider));
    }

    @Test
    void readsTheDeclaredOrderFromTheRegistry() throws Exception {
        List<Object> configuration = entries("about", "css");
        FakeRegistry registry = new FakeRegistry(configuration);

        List<?> declaredOrder = MenuOrderRestorer.readDeclaredOrder(new FakePlatform(registry));

        assertSame(configuration, declaredOrder);
        // Asking for the wrong configuration point would silently return someone else's entries.
        assertEquals(MenuOrderRestorer.CONFIG_ID, registry.requestedId);
    }

    @Test
    void readsNoDeclaredOrderWhenThePlatformHasNoRegistry() throws Exception {
        assertNull(MenuOrderRestorer.readDeclaredOrder(new PlatformWithoutRegistry()));
    }

    @Test
    void readsNoDeclaredOrderWhenTheRegistryIsMissingOrUnusable() throws Exception {
        assertNull(MenuOrderRestorer.readDeclaredOrder(new FakePlatform(null)));
        // An empty configuration means the entries are not there to reorder, not that the order is empty.
        assertNull(MenuOrderRestorer.readDeclaredOrder(new FakePlatform(new FakeRegistry(List.of()))));
        assertNull(MenuOrderRestorer.readDeclaredOrder(new FakePlatform(new FakeRegistry("not a list"))));
        // A registry that no longer answers getConfiguration(String) at all.
        assertNull(MenuOrderRestorer.readDeclaredOrder(new FakePlatform(new Object())));
    }

    @Test
    void findsAMethodAndReportsAMissingOneAsNull() {
        Method found = MenuOrderRestorer.findMethod(FakeRegistry.class, "getConfiguration", String.class);

        assertNotNull(found);
        assertEquals("getConfiguration", found.getName());
        assertNull(MenuOrderRestorer.findMethod(FakeRegistry.class, "getConfiguration"));
        assertNull(MenuOrderRestorer.findMethod(FakeRegistry.class, "noSuchMethod"));
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
     * would fail the webapp start or the request. Outside a running Polarion every reflective step fails, and they
     * still must not throw.
     */
    @Test
    void neverThrowsOutsideARunningPolarion() {
        Executable atStartup = () -> MenuOrderRestorer.applyAtStartup(true, true);
        Executable afterSave = () -> MenuOrderRestorer.apply(true, true);

        assertDoesNotThrow(atStartup);
        assertDoesNotThrow(afterSave);
    }

    // --- The whole pipeline, driven with Polarion's real provider and a stand-in platform ---

    private static AdministrationPageExtender realExtender(String id) {
        AdministrationPageExtender extender = new AdministrationPageExtender();
        extender.setId(id);
        extender.setParentNodeId("ext");
        extender.setParentNodeName("Ext");
        return extender;
    }

    @SuppressWarnings({"unchecked", "java:S3011"})
    private static List<Object> liveOrderOf(AdministrationPageExtenderProvider provider) throws Exception {
        Field field = AdministrationPageExtenderProvider.class.getDeclaredField("extenders");
        field.setAccessible(true);
        return (List<Object>) field.get(provider);
    }

    /** Fills the provider the way Polarion 2606 does: the declared entries through Set.copyOf. */
    private static AdministrationPageExtenderProvider providerFilledByPolarion(List<AdministrationPageExtender> declaredOrder) {
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(Set.copyOf(declaredOrder));
        return provider;
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

    @Test
    void ordersTheProvidersEntriesByDeclaration() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        AdministrationPageExtender css = realExtender("css");
        AdministrationPageExtender webhooks = realExtender("webhooks");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(css, webhooks, about)));

        MenuOrderRestorer.apply(provider, new FakePlatform(new FakeRegistry(List.of(about, css, webhooks))), false, false, true);

        assertEquals("ext/about, ext/css, ext/webhooks", MenuOrderRestorer.describe(liveOrderOf(provider)));
    }

    @Test
    void returnsToPolarionsOrderWhenTheModesAreSwitchedOff() throws Exception {
        List<AdministrationPageExtender> declaredOrder = List.of(realExtender("a"), realExtender("b"), realExtender("c"), realExtender("d"), realExtender("e"));
        AdministrationPageExtenderProvider provider = providerFilledByPolarion(declaredOrder);
        List<Object> polarionOrder = new ArrayList<>(liveOrderOf(provider));
        IPlatform platform = new FakePlatform(new FakeRegistry(declaredOrder));

        MenuOrderRestorer.apply(provider, platform, false, false, true);
        assertEquals(List.copyOf(declaredOrder), liveOrderOf(provider));
        MenuOrderRestorer.apply(provider, platform, false, false, false);

        assertEquals(polarionOrder, liveOrderOf(provider));
    }

    @Test
    void changesNothingAtStartupWithBothModesOff() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        AdministrationPageExtender css = realExtender("css");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(css, about)));
        List<Object> before = new ArrayList<>(liveOrderOf(provider));

        MenuOrderRestorer.apply(provider, new FakePlatform(new FakeRegistry(List.of(about, css))), true, false, false);

        assertEquals(before, liveOrderOf(provider));
    }

    @Test
    void appliesTheModesAtStartup() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        AdministrationPageExtender css = realExtender("css");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(css, about)));

        MenuOrderRestorer.apply(provider, new FakePlatform(new FakeRegistry(List.of(about, css))), true, false, true);

        assertEquals("ext/about, ext/css", MenuOrderRestorer.describe(liveOrderOf(provider)));
    }

    @Test
    void leavesTheProvidersEntriesAloneOnASecondRun() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        AdministrationPageExtender css = realExtender("css");
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(css, about)));
        IPlatform platform = new FakePlatform(new FakeRegistry(List.of(about, css)));

        MenuOrderRestorer.apply(provider, platform, false, true, true);
        List<Object> afterFirstRun = new ArrayList<>(liveOrderOf(provider));
        MenuOrderRestorer.apply(provider, platform, false, true, true);

        assertEquals(afterFirstRun, liveOrderOf(provider));
    }

    @Test
    void leavesTheProvidersEntriesAloneWhenTheRegistryHoldsOtherEntries() throws Exception {
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(realExtender("css"), realExtender("about"))));
        // Same ids, different instances: this is not Polarion's own list and must not be applied.
        List<Object> foreignOrder = List.of(realExtender("about"), realExtender("css"));

        MenuOrderRestorer.apply(provider, new FakePlatform(new FakeRegistry(foreignOrder)), false, true, true);

        assertEquals("ext/css, ext/about", MenuOrderRestorer.describe(liveOrderOf(provider)));
    }

    @Test
    void leavesTheProvidersEntriesAloneWhenTheConfigurationIsUnreadable() throws Exception {
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(realExtender("css"), realExtender("about"))));

        MenuOrderRestorer.apply(provider, new PlatformWithoutRegistry(), false, true, true);

        assertEquals("ext/css, ext/about", MenuOrderRestorer.describe(liveOrderOf(provider)));
    }

    /** A future Polarion that subclasses the provider: the field then sits on the superclass. */
    static class SubclassedProvider extends AdministrationPageExtenderProvider {
    }

    @Test
    @SuppressWarnings("java:S3011")
    void findsTheEntryListOnASuperclass() throws Exception {
        AdministrationPageExtender about = realExtender("about");
        SubclassedProvider provider = new SubclassedProvider();
        provider.setAdministrationPageExtenders(new LinkedHashSet<>(List.of(about)));

        List<Object> liveOrder = MenuOrderRestorer.readLiveOrder(provider);

        assertNotNull(liveOrder);
        assertSame(about, liveOrder.getFirst());
    }

    /**
     * A provider left holding no list must degrade to a warning, not to a NullPointerException on a
     * startup thread. The field is declared as a List, so this is the only shape the read can reject.
     */
    @Test
    @SuppressWarnings("java:S3011")
    void readsNoEntryListWhenTheProviderHoldsNone() throws Exception {
        AdministrationPageExtenderProvider provider = new AdministrationPageExtenderProvider();
        Field field = AdministrationPageExtenderProvider.class.getDeclaredField("extenders");
        field.setAccessible(true);
        field.set(provider, null);

        assertNull(MenuOrderRestorer.readLiveOrder(provider));
        assertDoesNotThrow(() -> MenuOrderRestorer.apply(
                provider, new FakePlatform(new FakeRegistry(List.of(realExtender("about")))), false, true, true));
    }
}
