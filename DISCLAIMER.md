**Use this extension in production at your own risk.** We made it to show a problem in Polarion 2606 and ways to solve it. Test it on a non-production server first.

## The problem

Polarion 2606 shows the Administration menu sections of extensions in a different order after each restart. The pages inside each section change their order too.

Up to Polarion 2512, the menu followed the order in which each extension declares its pages. Polarion 2606 collects the menu entries in an unordered set, and the order of that set changes each time the server starts.

## What this extension does

The extension changes the order of the menu entries that Polarion already loaded. It does not add entries and does not remove entries.

By default, the extension changes nothing. An administrator can turn on two options:

* Sort the sections of extensions alphabetically.
* Show the pages of each section in the order the extension declares them.

When the administrator turns both options off, the menu returns to the order that Polarion built at startup.

## Why it is risky

* A future Polarion release can change how it builds the Administration menu. The extension then writes a warning to the log and leaves the menu as Polarion built it.
* The extension changes the menu while Polarion is running. For a very short time, a user can see one menu page twice. The next page load shows the correct menu.
