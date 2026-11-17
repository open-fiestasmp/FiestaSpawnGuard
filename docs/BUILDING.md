# Building and testing

## Build

The provided build script deliberately consumes dependencies from a compatible local test server; it does not download or redistribute Minecraft/Purpur or plugin binaries. It expects Java 21+ (`java`, `javac`, `jar`), `find`, and `paste`. Prepare a Purpur 1.21.11 server directory with these exact files:

```text
server.jar
libraries/org/purpurmc/purpur/purpur-api/1.21.11-R0.1-SNAPSHOT/purpur-api-1.21.11-R0.1-SNAPSHOT.jar
plugins/AuthMe-6.0.0-Paper.jar
plugins/LuckPerms-Bukkit-5.5.71.jar
```

Run:

```sh
FIESTA_SERVER_DIR=/path/to/test-server ./scripts/build.sh
```

The output is `build/FiestaSpawnGuard.jar`. The script compiles with `--release 21`. Use dependencies you are entitled to obtain and run under their own licenses. This is an environment-dependent build, not a published Maven or Gradle project; snapshots or different dependency versions may require adapting the script and retesting.

## Isolated integration fixture

The fixture makes a temporary loopback-only offline-mode server with disposable worlds, fake accounts and a test password; it does not use or copy your production plugin data. It copies the specified server's `server.jar`, `libraries`, `versions`, and `cache`, plus the two plugin binaries. Run it against a **dedicated test installation**, never a production server directory whose files may be changing. It needs sufficient disk space, bash, Java 21+, Node.js, `minecraft-protocol` 1.66.2, `rg`, Python 3, `timeout`, `nice`, and `ionice`. Install Node dependencies outside this repository if desired and set `NODE_PATH` to their `node_modules` directory.

```sh
FIESTA_SERVER_DIR=/path/to/test-server ./scripts/build.sh
ACCEPT_EULA=yes FIESTA_SERVER_DIR=/path/to/test-server NODE_PATH=/path/to/node_modules ./test/run-fixture.sh
```

Set `ACCEPT_EULA=yes` only if you accept the Minecraft EULA and are authorized to run the test server. The fixture prints the temporary directory and leaves it for inspection; remove that directory after the test if no longer needed. It covers protected and unprotected breaking, interaction, explosion and piston boundaries, builder grant, helper grant/revocation, forbidden delegation, GUI adding/removal confirmation and offline-owner behavior. A passing fixture is not a substitute for testing your actual plugin stack or a formal security review.
