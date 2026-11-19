#!/bin/sh
# Creates the release signing key (keystore.jks) with a random password stored in keystore.password.
# Run once, then back up BOTH files somewhere safe: losing them means you can't ship updates,
# leaking them lets anyone ship "updates" to your users. Both are git-ignored.
set -eu

if [ -e keystore.jks ] || [ -e keystore.password ]; then
    echo "keystore.jks or keystore.password already exists; refusing to overwrite." >&2
    exit 1
fi

umask 077
head -c 24 /dev/urandom | base64 | tr -d '\n' > keystore.password
KEYSTORE_PASSWORD=$(cat keystore.password)
export KEYSTORE_PASSWORD

keytool -genkeypair -keystore keystore.jks -storetype PKCS12 \
    -storepass:env KEYSTORE_PASSWORD -keypass:env KEYSTORE_PASSWORD \
    -alias extinguishtv -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=ExtinguishTV"

echo "Created keystore.jks and keystore.password. Back them up now."
