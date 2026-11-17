# Operations and troubleshooting

- **Plugin did not enable:** Check the server log for the exact error, world name and radius. Install LuckPerms and AuthMe first and use the tested platform versions. A disabled plugin cannot provide protection.
- **Builder cannot build:** Authenticate with AuthMe, check `/spawntag list <player>`, LuckPerms permissions and the configured region. Vanilla spawn protection or another protection plugin can still deny an action.
- **Helper cannot build:** Confirm the name and trust list, that the owner still has builder permission, and that the helper is authenticated. Owner permissions load asynchronously; check LuckPerms and the log.
- **Cannot add a player:** They need to have joined before. The menu lists online candidates; use a command for a cached offline player. Helpers cannot delegate access.
- **A GUI is missing or another `/trust` runs:** Check your server's registered commands and other plugins. Use the plugin's namespaced command or `/spawntrust` if needed; check your server's command conflict policy.
- **Trust save failed:** Check filesystem ownership, permissions, free space and logs. The plugin reports failure rather than reporting success. Do not edit the file live.
- **Changes not taking effect:** Configuration and plugin JAR changes require a normal server restart. Verify the startup log and repeat permission and protection checks.

For recovery, restore a consistent set of plugin data and LuckPerms data from your own verified backups while the server is stopped. Do not publish those files: they contain player identifiers and access relationships. This project does not automatically perform backups or restore griefed builds.

For a suspected vulnerability, contact the repository maintainers privately through GitHub's security reporting feature if enabled; do not post a public issue containing exploit details, credentials, addresses, or private player data. The maintainers must enable private reporting in repository settings for that feature to be available.
