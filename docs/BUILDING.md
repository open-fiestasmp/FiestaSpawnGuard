# Building and testing

## Build

The provided build script deliberately consumes dependencies from a compatible local test server; it does not download or redistribute Minecraft/Purpur or plugin binaries. It expects Java 21+ (`java`, `javac`, `jar`), `find`, and `paste`. Prepare a Purpur 1.21.11 server directory with these exact files:

```text
server.jar
libraries/org/purpurmc/purpur/purpur-api/1.21.11-R0.1-SNAPSHOT/purpur-api-1.21.11-R0.1-SNAPSHOT.jar
plugins/AuthMe-*-Paper.jar (exactly one enabled AuthMe JAR)
plugins/LuckPerms-Bukkit-5.5.71.jar
```

Run:

```sh
FIESTA_SERVER_DIR=/path/to/test-server ./scripts/build.sh
```

The scripts select the only `AuthMe-*-Paper.jar` in the plugins directory. If a test directory contains more than one enabled version, set `AUTHME_JAR=/path/to/AuthMe-Paper.jar` explicitly.

The output is `build/FiestaSpawnGuard.jar`. The script compiles with `--release 21`. Use dependencies you are entitled to obtain and run under their own licenses. This is an environment-dependent build, not a published Maven or Gradle project; snapshots or different dependency versions may require adapting the script and retesting.

## Isolated integration fixture

The fixture copies the server JAR, libraries, versions, cache, and two plugin JARs into a temporary installation. It creates fresh plugin data, disposable worlds, fake accounts, and a random password. Run it against a **dedicated test installation**, never a production server directory whose files may be changing. It needs sufficient disk space, bash, Java 21+, Node.js, `minecraft-protocol` 1.66.2, the server's `org.jetbrains:annotations` JAR, `rg`, Python 3, `timeout`, `nice`, and `ionice`. Install Node dependencies outside this repository if desired and set `NODE_PATH` to their `node_modules` directory.

```sh
FIESTA_SERVER_DIR=/path/to/test-server ./scripts/build.sh
ACCEPT_EULA=yes FIESTA_SERVER_DIR=/path/to/test-server NODE_PATH=/path/to/node_modules ./test/run-fixture.sh
```

Set `ACCEPT_EULA=yes` only if you accept the Minecraft EULA and are authorized to run the test server. The fixture prints the temporary directory and leaves it for inspection; remove that directory after the test if no longer needed. It covers protected and unprotected breaking, interaction, explosion and piston boundaries, operator defaults and explicit self-revocation, helper grant/revocation, forbidden delegation, GUI adding/removal confirmation including self-testing, offline-owner behavior, and fail-closed handling of a malformed trust file. A passing fixture is not a substitute for testing your actual plugin stack or a formal security review.
