#!/usr/bin/env bash
# Builds launcher/Accueil.apk with the plain Android SDK tools: no Gradle, no libraries, no downloads.
# Needs a JDK (17 or newer) and an Android SDK with any platform and build-tools.
#   ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk sh launcher/build.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
PLATFORM="$(ls -d "$SDK"/platforms/android-* 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] && [ -n "$PLATFORM" ] || { echo "Android SDK not found: set ANDROID_HOME" >&2; exit 1; }
JAR="$PLATFORM/android.jar"
# Git Bash: JAVA_HOME is usually C:/..., which can't go in PATH as is.
if [ -n "${JAVA_HOME:-}" ]; then
  JH="$JAVA_HOME"; command -v cygpath >/dev/null && JH="$(cygpath -u "$JAVA_HOME")"
  export PATH="$JH/bin:$PATH"
fi

# build-tools are .exe/.bat on Windows (Git Bash), plain scripts elsewhere.
tool() { for f in "$BT/$1.exe" "$BT/$1.bat" "$BT/$1"; do [ -e "$f" ] && { echo "$f"; return; }; done; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; *) SEP=':' ;; esac

# One version for the whole project (../VERSION, SemVer). Android needs a growing integer too:
# MAJOR*10000 + MINOR*100 + PATCH, so 1.2.3 -> 10203.
VERSION="$(tr -d ' \r\n' < ../VERSION)"
IFS=. read -r MAJ MIN PAT <<< "$VERSION"
CODE=$((MAJ * 10000 + MIN * 100 + PAT))

rm -rf build && mkdir -p build/classes build/gen build/dex

"$(tool aapt2)" compile --dir res -o build/res.zip
"$(tool aapt2)" link -I "$JAR" --manifest AndroidManifest.xml -o build/unsigned.apk \
  --java build/gen --min-sdk-version 23 --target-sdk-version 28 \
  --version-code "$CODE" --version-name "$VERSION" build/res.zip

javac -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options \
  -bootclasspath "$JAR$SEP$BT/core-lambda-stubs.jar" -classpath "$JAR" \
  -d build/classes $(find src build/gen -name '*.java')

"$(tool d8)" --release --min-api 23 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

(cd build/dex && jar uf ../unsigned.apk classes.dex)
"$(tool zipalign)" -f 4 build/unsigned.apk build/aligned.apk

# Your own signing key, made on the first build and kept out of git. Keep it: updates must use it.
if [ ! -f accueil.keystore ]; then
  keytool -genkeypair -keystore accueil.keystore -storepass accueil -keypass accueil -alias accueil \
    -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=Accueil" >/dev/null
fi
"$(tool apksigner)" sign --ks accueil.keystore --ks-pass pass:accueil --out Accueil.apk build/aligned.apk
echo "built $(pwd)/Accueil.apk"
