#!/usr/bin/env python3
"""Starts only a new disposable test server; refuses any existing world directory."""
import json
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
run = root / 'forge/run-world-acceptance'
run.mkdir(exist_ok=False)
(run / 'eula.txt').write_text('eula=true\n')
(run / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\nlevel-name=acceptance-disposable\nlevel-type=minecraft:flat\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-players=1\n')
with (root / 'world-acceptance.log').open('w') as log:
    result = subprocess.run(['./gradlew', ':forge:runAcceptanceServer', '-PworldAcceptance', '--no-daemon'],
                            cwd=root, stdout=log, stderr=subprocess.STDOUT, timeout=600)
result_path = run / 'world-acceptance-result.json'
if result.returncode or not result_path.exists():
    print((root / 'world-acceptance.log').read_text()[-24000:])
    raise SystemExit('Disposable world failed to start or finish; see log above')
evidence = json.loads(result_path.read_text())
print(json.dumps(evidence))
if result.returncode or not evidence.get('passed'):
    print((root / 'world-acceptance.log').read_text()[-16000:])
    raise SystemExit('World acceptance failed; inspect world-acceptance.log and result JSON')

# Restart only the freshly-created fixture above, never a user-supplied world.
(run / 'restart-phase').write_text('created-by-this-invocation\n')
with (root / 'world-acceptance-restart.log').open('w') as log:
    restart = subprocess.run(['./gradlew', ':forge:runAcceptanceServer', '-PworldAcceptance', '--no-daemon'], cwd=root, stdout=log, stderr=subprocess.STDOUT, timeout=600)
restart_path = run / 'world-acceptance-restart-result.json'
if restart.returncode or not restart_path.exists():
    print((root / 'world-acceptance-restart.log').read_text()[-16000:])
    raise SystemExit('Disposable restart did not finish')
restart_evidence = json.loads(restart_path.read_text())
print(json.dumps(restart_evidence))
if not restart_evidence.get('passed'):
    print((root / 'world-acceptance-restart.log').read_text()[-16000:])
    raise SystemExit('Disposable restart assertions failed')
