#!/usr/bin/env bash
# Packs the extension into dist/mailtice-extension-<version>.zip, ready for "Load unpacked" (after
# unzipping) or a store upload.
#   ./build.sh [version]           version defaults to the one in manifest.json; a leading "v" is dropped
#   GOOGLE_WEB_CLIENT_ID=...       fills the OAuth client in; without it the setup box asks for one
set -euo pipefail
cd "$(dirname "$0")"

version="${1:-}"
version="${version#v}"
out="dist/mailtice-extension"
rm -rf dist
mkdir -p "$out"
cp -r manifest.json popup.html export.html icons _locales src "$out/"

python3 - "$out" "$version" "${GOOGLE_WEB_CLIENT_ID:-}" <<'PY'
import json, re, sys
out, version, client = sys.argv[1], sys.argv[2], sys.argv[3]
path = f"{out}/manifest.json"
manifest = json.load(open(path))
if version:
    # Chrome takes up to four dot-separated numbers; "1.4.0-beta.2" shows as its version_name.
    numbers = re.match(r"^\d+(\.\d+){0,3}", version)
    if not numbers:
        sys.exit(f"Not a version: {version}")
    manifest["version"] = numbers.group(0)
    if numbers.group(0) != version:
        manifest["version_name"] = version
json.dump(manifest, open(path, "w"), indent=2, ensure_ascii=False)
if client:
    if not re.fullmatch(r"[\w.-]+\.apps\.googleusercontent\.com", client):
        sys.exit("GOOGLE_WEB_CLIENT_ID does not look like a Google OAuth client id")
    open(f"{out}/src/config.js", "w").write(f'export const DEFAULT_CLIENT_ID = "{client}";\n')
print(f"manifest version {manifest['version']}, client {'set' if client else 'not set'}")
PY

name="mailtice-extension-$(python3 -c "import json;print(json.load(open('$out/manifest.json'))['version'])")"
(cd "$out" && zip -qr "../$name.zip" .)
echo "dist/$name.zip"
