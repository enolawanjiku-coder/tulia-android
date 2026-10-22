#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

python3 - <<'PY'
from pathlib import Path

path = Path("version.properties")
values = {}
for line in path.read_text().splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()

version_code = int(values.get("versionCode", "1")) + 1
version_name = values.get("versionName", "0.0.0")
parts = version_name.split(".")
if len(parts) == 3 and parts[-1].isdigit():
    parts[-1] = str(int(parts[-1]) + 1)
    version_name = ".".join(parts)
else:
    version_name = version_name + ".1"

path.write_text(f"versionCode={version_code}\nversionName={version_name}\n")
print(f"Releasing Tulia {version_name} (build {version_code})")
PY

export JAVA_HOME=${JAVA_HOME:-$HOME/.local/opt/jdk-17}
./gradlew assembleDebug assembleRelease --console=plain

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git add version.properties
  git commit -m "Release $(grep '^versionName=' version.properties | cut -d= -f2) (build $(grep '^versionCode=' version.properties | cut -d= -f2))"
  git push origin HEAD
  echo "Pushed. GitHub Actions will publish the release."
else
  echo "Not a git repository. The APK is at app/build/outputs/apk/release/app-release.apk"
fi
