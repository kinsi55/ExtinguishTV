FROM eclipse-temurin:17-jdk

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates unzip zip \
 && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
 && curl -fsSL -o /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
 && unzip -q /tmp/tools.zip -d "$ANDROID_HOME/cmdline-tools" \
 && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
 && rm /tmp/tools.zip \
 && yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null \
 && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-34" "build-tools;34.0.0" > /dev/null

WORKDIR /src
CMD ["sh", "build.sh"]
