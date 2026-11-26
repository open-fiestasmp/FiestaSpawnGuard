# Configuration and protection

The generated `plugins/FiestaSpawnGuard/config.yml` contains:

```yaml
world: world
center-x: 0
center-z: 0
radius-chunks: 32
```

Replace the example center with your spawn coordinates. `world` is the exact Bukkit world name. X and Z are block coordinates, converted to chunks with floor division by 16. For each candidate location, the plugin compares the squared distance between its chunk and the center chunk against `radius-chunks²`. The region is a **circle of chunks**, including its boundary, not a square. It covers the full build height in that world. Supported radii are 16 through 64 chunks. An invalid radius or missing world disables the plugin; monitor startup logs.

Changing the server's spawn does not automatically move this region. Restart after editing the configuration and test boundaries, including negative coordinates. The region does not extend into Nether or End worlds unless configured as the one world.

## Trust and permissions

Trust is stored as builder UUID to helper UUID sets in `plugins/FiestaSpawnGuard/trust.yml`. The file is written through a temporary file and atomic move where the filesystem supports it. Player names are resolved against online or cached known players; helpers do not need to be online to remain trusted. A helper is authorized only while at least one granting owner has the builder permission, and the helper is authenticated with AuthMe. The plugin loads owner permissions at startup; access fails closed until they are available. Trust can survive an owner logging out. Removing a builder tag through this plugin removes that builder's grants. Independent grants from other builders continue to apply.

If `trust.yml` is malformed or unreadable, the protection plugin still starts. Direct builders retain access, helpers are denied, and trust-list commands and the GUI stay disabled. The original file is left untouched. Restore a valid copy and restart the server before managing helpers again.

Operators receive builder permission by default. OP status also opens the staff role menu and grants staff trust-management access even when LuckPerms returns false for the plugin's `default: op` staff nodes. Staff can use `/trust` to select an online player and choose builder access or a helper list. Revoking the builder tag writes an explicit LuckPerms denial, overriding the OP default; granting it again removes that denial and writes a positive permission. Staff can perform these operations on themselves. Use `/trust gui` to manage your own helper list. A builder can add themselves to their own helper list to test the interface, but this is redundant and provides no extra access.

Do not hand-edit `trust.yml` while the server is running: memory may overwrite changes. Preserve both LuckPerms data and the plugin's data when migrating. On save failure the operation reports an error; inspect server logs and verify actual state before retrying. User-managed admin permissions are `fiesta.spawntag.manage` and `fiesta.spawntrust.admin`.

## Guarded events

Inside the region the implementation checks block placement and breaking, bucket use, modification of blocks and signs, interaction with protected containers and non-player entities, damage to protected non-hostile entities by players/projectiles, and container inventory access. Players can use doors, trapdoors, buttons and levers, eat, use their own inventory and fight hostile mobs. Chests and other protected containers remain inaccessible to untrusted players. The guard filters explosion block damage, handles certain non-player explosion damage, prevents some hanging-entity destruction, burn and selected ignition, blocks fluid entering from outside, and restricts pistons and hopper transfers across the boundary. Player-vs-player damage is not handled by this plugin. The exact code and Bukkit event behavior are authoritative.

This list is not a guarantee of complete protection: plugins can alter blocks directly without triggering these events, console commands and privileged world-edit tools can bypass event protections, and other mechanics or plugins may require additional protection. Existing vanilla spawn protection, claim plugins, and other permission systems operate independently and can still deny actions to builders. Test your own plugin stack, entities, redstone, farms, containers, entrances, and boundary interactions before opening a server to players. Do not assume this plugin protects every mechanic or replaces backups and moderation.
