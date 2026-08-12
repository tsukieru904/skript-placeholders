# skript-placeholders
Placeholder integration for Skript.

> [!IMPORTANT]
> This build requires Skript 2.16.0 or newer.
> While this addon does not frequently receive updates, it is still compatible with the latest Skript versions and should function without issue.
> If you encounter errors of any kind, please report them using the [issues tab](https://github.com/APickledWalrus/skript-placeholders/issues).

This addon allows you to use and register placeholders with PlaceholderAPI and/or MVdWPlaceholderAPI.
For this addon to work, you must have PlaceholderAPI or MVdWPlaceholderAPI installed.
Please note that you must have an MVdW plugin for MVdWPlaceholderAPI to function.

This addon is a fork of [Ersatz (by Pikachu)](https://github.com/Pikachu920/Ersatz) which has been updated to work with PlaceholderAPI's new PlaceholderExpansion system and adds new features.

# Links

**Skript:** https://github.com/SkriptLang/Skript

**PlaceholderAPI:** https://www.spigotmc.org/resources/placeholderapi.6245/

**PlaceholderAPI Placeholders:** https://github.com/PlaceholderAPI/PlaceholderAPI/wiki/Placeholders

**MVdWPlaceholderAPI:** https://www.spigotmc.org/resources/mvdwplaceholderapi.11182/

**MVdWPlaceholderAPI Placeholders:** https://www.spigotmc.org/wiki/mvdw-placeholders/

**SkUnity Resource Listing:** https://forums.skunity.com/resources/skript-placeholders.909/

# Examples and Documentation

Documentation is available on the following platforms:

**GitHub Wiki:** https://github.com/APickledWalrus/skript-placeholders/wiki

[![Get on skUnity](https://docs.skunity.com/skunity/library/Docs/Assets/assets/images/buttons/v2/get-the-syntax-square.png)](https://docs.skunity.com/syntax/search/addon:skript-placeholders)

[![SkriptHubViewTheDocs](http://skripthub.net/static/addon/ViewTheDocsButton.png)](http://skripthub.net/docs/?addon=skript-placeholders)
## Compatibility

This build officially targets **Paper 1.21.4–26.2** and produces **Java 21 bytecode**. It is marked as **Folia-supported**. It uses Paper/Folia region and global schedulers instead of the legacy Bukkit scheduler.

Requires **Skript 2.16.0+**. PlaceholderAPI support is built against **2.12.3+**.

For relational placeholders on Folia, both players should ideally be in the same region when a Skript trigger directly accesses both player objects; Folia does not provide a scheduler that owns entities in separate regions.

## Folia / Paper 1.21.4–26.2

This build officially targets Paper 1.21.4–26.2 and supports Folia. It uses the Folia global and entity schedulers instead of the legacy Bukkit scheduler. Placeholder callbacks must not block a region while waiting for another region.

For custom Skript placeholders, requests that arrive outside the player-owning region are scheduled on the player entity scheduler and return the most recently evaluated value. Relational placeholders are evaluated only when the current region owns both players; when the players are in separate regions, the last cached value is returned instead of performing an unsafe cross-region access.

Build with Java 21. The resulting plugin JAR can run on Java 21 servers (Paper 1.21.x) and Java 25 servers (Paper 26.1+).
