"""CI-only production-jar boot checks, without and with the pinned Flowing Fluids."""
import hashlib
import os
from pathlib import Path
import queue
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request


def boot(root):
    process = subprocess.Popen(
        ["bash", "run.sh", "nogui"], cwd=root, stdin=subprocess.PIPE,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True,
    )
    lines = queue.Queue()

    def read_output():
        for line in process.stdout:
            print(line, end="", flush=True)
            lines.put(line)
        lines.put(None)

    threading.Thread(target=read_output, daemon=True).start()
    try:
        deadline = time.monotonic() + 180
        while True:
            line = lines.get(timeout=max(0.01, deadline - time.monotonic()))
            if line is None:
                raise RuntimeError("Server exited before reaching ready")
            if 'Done (' in line and 'For help, type "help"' in line:
                break
        process.stdin.write("stop\n")
        process.stdin.flush()
        if process.wait(timeout=45) != 0:
            raise RuntimeError("Server did not shut down cleanly")
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()


if __name__ == "__main__":
    jar = Path(sys.argv[1]).resolve()
    with tempfile.TemporaryDirectory(prefix="atmosphere-ci-") as directory:
        root = Path(directory)
        installer = root / "installer.jar"
        urllib.request.urlretrieve(
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-installer.jar",
            installer,
        )
        subprocess.run(["java", "-jar", str(installer), "--installServer"], cwd=root, check=True, timeout=180)
        (root / "eula.txt").write_text("eula=true\n")
        (root / "user_jvm_args.txt").write_text("-Xmx2G\n")
        (root / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\n"
            "level-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\n"
        )
        (root / "mods").mkdir(exist_ok=True)
        shutil.copy2(jar, root / "mods" / jar.name)
        print("Booting without Flowing Fluids", flush=True)
        boot(root)
        fluid = urllib.request.urlopen(
            "https://cdn.modrinth.com/data/s1I3BT95/versions/k37oVEnG/flowing_fluids-1.0.6-1.21-neoforge.jar",
            timeout=60,
        ).read()
        assert hashlib.sha512(fluid).hexdigest() == (
            "c903f5a9d67e860ef641869e54fe6d6a6ee0ae5285e754ac465160e63b15512453d374ead3d288fa9bb67c9050a6d6b8f2db7c1b7bc06e398a9a3969e9409c29"
        )
        (root / "mods" / "flowing_fluids.jar").write_bytes(fluid)
        print("Booting with Flowing Fluids 1.0.6", flush=True)
        boot(root)
        print("Both production-jar startup checks passed", flush=True)
