# Admin Menu Organizer for Polarion ALM

[![CI](https://github.com/intechcore/com.intechcore.polarion.extension.admin-menu-organizer/actions/workflows/ci.yml/badge.svg)](https://github.com/intechcore/com.intechcore.polarion.extension.admin-menu-organizer/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/intechcore/com.intechcore.polarion.extension.admin-menu-organizer/badge)](https://scorecard.dev/viewer/?uri=github.com/intechcore/com.intechcore.polarion.extension.admin-menu-organizer)
[![Release](https://img.shields.io/github/v/release/intechcore/com.intechcore.polarion.extension.admin-menu-organizer)](https://github.com/intechcore/com.intechcore.polarion.extension.admin-menu-organizer/releases)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.admin-menu-organizer&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.admin-menu-organizer)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.admin-menu-organizer&metric=coverage)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.admin-menu-organizer)

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

In a Polarion cluster, saving applies the order only on the node that handles the request. The other nodes apply it at their next restart.

## REST API

This extension provides a REST API. Its OpenAPI specification can be obtained [here](docs/openapi.json).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Report vulnerabilities privately, see [SECURITY.md](SECURITY.md).

## Disclaimer

This extension is provided "as is", without warranty of any kind, as the [LICENSE](LICENSE) states.
Use it at your own risk. Intechcore GmbH is not liable for damage from its use, as far as the law
allows. It is published free of charge, outside of any commercial offering, with no obligation to
support it. Security reports are welcome, see [SECURITY.md](SECURITY.md).

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
