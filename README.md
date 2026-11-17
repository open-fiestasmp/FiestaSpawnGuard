# FiestaSpawnGuard

A Purpur plugin for a protected spawn area with invisible builder tags and builder-managed helper lists. Builders can use an in-game menu to manage access, while staff can manage builder tags and another builder's list. Helpers cannot delegate access. The protection does not change rules outside the configured area.

## Features

- A configurable circular region in the Overworld, measured in chunks.
- Hidden, extensible `fiesta.tag.<name>` LuckPerms permissions, including `builder`.
- A separate persisted list for each builder. A helper can have grants from multiple builders.
- `/trust` inventory menu with pagination, online-player selection and confirmation before removal. Commands also support offline players who have joined before.
- Authentication checks through AuthMe; protections against selected player actions, explosions, and boundary-crossing mechanisms.
- Staff commands to assign multiple tags and manage a particular builder's helpers.

## Requirements and compatibility

This version was developed and tested with **Purpur 1.21.11**, LuckPerms **5.5.71**, AuthMe **6.0.0-Paper**, and Java **21 or newer**. Those versions and filenames are required by the provided build and integration-test scripts. Other versions, Bukkit derivatives, proxies, and Bedrock/Floodgate authentication have not been verified. LuckPerms and AuthMe are required at runtime; Floodgate is an optional soft dependency, not a guarantee of Bedrock compatibility.

This plugin is designed for servers using AuthMe, including offline-mode servers. Offline-mode servers need a carefully configured authentication and network security setup; installing this plugin does not provide account authentication or secure a proxy on its own.

## Installation

1. Build the plugin as described in [Building and testing](docs/BUILDING.md), or obtain a trusted build for the exact version you reviewed.
2. Install compatible LuckPerms and AuthMe plugins on the server.
3. Put `FiestaSpawnGuard.jar` in the server's `plugins/` directory and start the server once to generate its configuration.
4. Stop the server and set `world`, `center-x`, `center-z`, and `radius-chunks` in `plugins/FiestaSpawnGuard/config.yml`. **The bundled center is an example at 0, 0**, not the server's spawn. Confirm the correct world and coordinates before relying on protection.
5. Start the server and check its log for `Enabled spawn guard`. Authenticate in game and test both a builder and an ordinary player. Grant the first builder tag as staff, then test the trust menu and actual protection.

Changing configuration or replacing the JAR requires a restart. Avoid using plugin reload tools as a substitute. Keep current, tested server backups through your normal backup process.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/trust` or `/trust gui` | Authenticated builder | Open the builder's helper menu. |
| `/trust gui <builder>` | Staff | Open a builder's helper menu. |
| `/trust <player> player` | Builder | Add a previously known player as a helper. `player` can be omitted. |
| `/trust remove <player> player` | Builder | Remove a helper from the builder's own list. |
| `/trust list` | Builder | List helpers as text. |
| `/trust <player> player <builder>` | Staff | Add a helper to a named builder's list. |
| `/trust remove <player> player <builder>` | Staff | Remove a helper from a named builder's list. |
| `/trust list <builder>` | Staff | List a named builder's helpers. |
| `/trust <player> builder` | Tag staff | Give the builder tag. |
| `/trust remove <player> builder` | Tag staff | Remove the builder tag and its helper grants. |
| `/spawntrust add\|remove <player> [builder]` | Builder or staff | Manage a list using the original command. |
| `/spawntrust list [builder]` | Builder or staff | List helpers. |
| `/spawntag grant\|revoke <player> <tag>` | Tag staff | Manage a hidden tag. |
| `/spawntag list <player>` | Tag staff | List directly assigned Fiesta tags. |

In the menu, use the green dye to choose an **online** player. Click an existing helper and confirm to remove them. Use commands for offline players. Players must be known to the server; an unknown name is rejected. Helper access is not a builder tag, so helpers cannot open their own trust menu or grant access. The separate commands `/spawntrust` and `/spawntag` remain available. If another plugin owns `/trust`, resolve the conflict in your server's command configuration; this plugin also exposes Bukkit's namespaced command. A claim plugin's `/claim trust` is a different command but may have independent rules.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `fiesta.tag.builder` | false | Build in protected spawn and manage the holder's own helpers. |
| `fiesta.spawntag.manage` | op | Grant, revoke, or inspect Fiesta tags. |
| `fiesta.spawntrust.admin` | op | Manage another builder's trust list. |

OP status alone does **not** bypass protected building: an authenticated operator must also be a builder or trusted helper. Avoid granting builder access by broad wildcard unless intended. Tags are permissions, not chat prefixes or suffixes. Use `/spawntag` for additional tag names; only the `builder` tag has behavior in this plugin.

## Configuration and behavior

See [Configuration and protection](docs/CONFIGURATION.md) for the chunk-radius calculation, trust persistence, protected events, and important limits. See [Operations](docs/OPERATIONS.md) for deployment, troubleshooting, and recovery. Read the limitations before relying on it to prevent griefing.

## Project status

This is a server-specific plugin extracted into a standalone source repository. The included fixture exercises protection, trust delegation and GUI behavior on an isolated local server, but it is not a complete security audit. No production player data, server configuration, credentials, server JARs, or trust lists are included. The repository does not grant a license; contact the authors before redistribution or reuse.
