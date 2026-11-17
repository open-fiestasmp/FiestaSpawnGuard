#!/usr/bin/env bash
set -euo pipefail
root=${FIESTA_SERVER_DIR:?Set FIESTA_SERVER_DIR to a compatible test server installation}
repo=$(cd "$(dirname "$0")/.." && pwd)
[[ ${ACCEPT_EULA:-} == yes ]] || { echo "Set ACCEPT_EULA=yes only if you accept the Minecraft EULA." >&2; exit 1; }
work=$(mktemp -d /tmp/fiesta-guard-fixture.XXXXXXXX)
echo "Fixture directory: $work"
mkdir -p "$work/server/plugins/AuthMe" "$work/server/plugins/FiestaSpawnGuard" "$work/classes"
classpath=$(find "$root/libraries" -name '*.jar' -print | paste -sd:)
cp --reflink=auto "$root/server.jar" "$work/server/server.jar"
cp -a --reflink=auto "$root/versions" "$root/libraries" "$root/cache" "$work/server/"
for f in AuthMe-6.0.0-Paper.jar LuckPerms-Bukkit-5.5.71.jar; do cp --reflink=auto "$root/plugins/$f" "$work/server/plugins/"; done
cp "$repo/build/FiestaSpawnGuard.jar" "$work/server/plugins/FiestaSpawnGuard.jar"
cat > "$work/server/plugins/FiestaSpawnGuard/config.yml" <<'CONFIG'
world: world
center-x: 0
center-z: 0
radius-chunks: 32
CONFIG
cat > "$work/server/plugins/AuthMe/config.yml" <<'AUTH'
settings:
    restrictions:
        maxRegPerIp: 0
    registration:
        dialog:
            preJoin:
                enable: false
AUTH
printf 'eula=true\n' > "$work/server/eula.txt"
port=$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')
cat > "$work/server/server.properties" <<PROPS
server-ip=127.0.0.1
server-port=$port
online-mode=false
max-players=5
view-distance=2
simulation-distance=2
generate-structures=false
spawn-protection=0
level-type=minecraft:flat
PROPS
javac -proc:none -cp "$root/plugins/AuthMe-6.0.0-Paper.jar:$root/libraries/org/purpurmc/purpur/purpur-api/1.21.11-R0.1-SNAPSHOT/purpur-api-1.21.11-R0.1-SNAPSHOT.jar:$classpath" -d "$work/classes" "$repo/test/GuardFixture.java"
cat > "$work/classes/plugin.yml" <<'PLUGIN'
name: FiestaGuardFixture
version: 1.0
main: com.fiesta.guardfixture.GuardFixture
api-version: '1.21'
depend: [FiestaSpawnGuard, AuthMe, LuckPerms]
PLUGIN
jar cf "$work/server/plugins/fixture.jar" -C "$work/classes" .
(cd "$work/server"; exec nice -n 15 ionice -c3 timeout 240s java -Xms256M -Xmx1200M -XX:ActiveProcessorCount=2 -jar server.jar --nogui) > "$work/fixture.log" 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true' EXIT
ready=false
for _ in {1..150};do if rg -q GUARD_FIXTURE_READY "$work/fixture.log";then ready=true;break;fi;if ! kill -0 "$pid" 2>/dev/null;then break;fi;sleep 1;done
if [[ "$ready" != true ]];then tail -35 "$work/fixture.log";exit 1;fi
FIXTURE_PORT="$port" node <<'NODE'
const mc=require('minecraft-protocol');
['GuardBuilder','GuardFriend','GuardVisitor'].forEach((username,index)=>setTimeout(()=>{
 const c=mc.createClient({host:'127.0.0.1',port:Number(process.env.FIXTURE_PORT),username,version:'1.21.11',auth:'offline'});
 c.on('error',e=>console.error(username,e.message));
},index*4000));
setTimeout(()=>process.exit(0),55000);
NODE
wait "$pid" || true
rg 'GUARD_FIXTURE_|FiestaSpawnGuard.*(Enabled|failed)|Exception|ERROR' "$work/fixture.log" | tail -25
rg -q GUARD_FIXTURE_PASS "$work/fixture.log"
echo 'Guard fixture passed.'
