# Admin Menu Organizer for Polarion ALM

> [!IMPORTANT]
> Read the [Disclaimer](DISCLAIMER.md) before you use this extension. Use it in production at your own risk.

This Polarion extension keeps the entries of the Administration menu in a stable order.

Polarion 2606 puts the administration menu entries of all extensions in a different order after each restart. This extension restores the order in which the extensions declare their entries.

## Build

This extension can be produced using Maven:

```bash
mvn clean package
```

The build also compiles the React user interface in `ui/` and bundles it into the extension under `webapp/admin-menu-organizer-app`. No separate Node installation is required.

## Installation to Polarion

To install this extension, copy `com.intechcore.polarion.extension.admin-menu-organizer-<version>.jar` to `<polarion_home>/polarion/extensions/com.intechcore.polarion.extension.admin-menu-organizer/eclipse/plugins`. The Maven build can do it for you:

```bash
mvn clean install -P local-install-into-polarion
```

The `POLARION_HOME` environment variable must point to the Polarion installation folder.

Changes take effect only after a restart of Polarion.

## Polarion configuration

The extension adds the **Admin Menu Organizer** section to the Administration menu of the global scope. The section is not available in projects, because the menu it organizes is one list for the whole server.

By default, the extension changes nothing. Open **Administration > Admin Menu Organizer > Menu Order** to turn on one or both options:

* **Sort the sections of extensions alphabetically.**
* **Show the pages of each section in the order the extension declares them.**

Saving applies the order at once. Reload the Polarion page to see it. When both options are off again, the menu returns to the order that Polarion built at startup. The extension also applies the saved options each time Polarion starts.

## REST API

This extension provides a REST API. Its OpenAPI specification can be obtained [here](docs/openapi.json).
