#!/bin/sh
# Builds build/ExtinguishTV.apk with plain SDK tools (no Gradle). Runs inside the Docker image from ./Dockerfile.
set -eu

BT="$ANDROID_HOME/build-tools/34.0.0"
JAR="$ANDROID_HOME/platforms/android-34/android.jar"
OUT=build

# Release signing key. Never commit or publish it: anyone holding it can ship "updates" to existing installs.
KEYSTORE="${KEYSTORE:-keystore.jks}"
if [ -z "${KEYSTORE_PASSWORD:-}" ] && [ -f keystore.password ]; then
    KEYSTORE_PASSWORD=$(cat keystore.password)
fi
if [ ! -f "$KEYSTORE" ] || [ -z "${KEYSTORE_PASSWORD:-}" ]; then
    echo "Missing $KEYSTORE or its password (KEYSTORE_PASSWORD / keystore.password)." >&2
    echo "Restore your backup, or create a new key with: sh tools/new-keystore.sh" >&2
    echo "A new key can't update installs signed with an old one." >&2
    exit 1
fi
export KEYSTORE_PASSWORD

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir app/res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest app/AndroidManifest.xml --java "$OUT/gen" \
    --min-sdk-version 24 --target-sdk-version 29 --version-code 4 --version-name 1.3 \
    -o "$OUT/unaligned.apk" "$OUT/res.zip"

# core-lambda-stubs provides LambdaMetafactory, which android.jar lacks; d8 desugars the lambdas.
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d "$OUT/classes" \
    $(find app/src "$OUT/gen" -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../unaligned.apk classes.dex)

"$BT/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

# Sign with every scheme (v1 included) so older or picky package installers accept it.
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:KEYSTORE_PASSWORD --ks-key-alias extinguishtv \
    --min-sdk-version 24 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT/ExtinguishTV.apk" "$OUT/aligned.apk"

echo "Built $OUT/ExtinguishTV.apk"
