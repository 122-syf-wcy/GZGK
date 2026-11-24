#!/usr/bin/env bash
# Project-local runtime helper: pins Java 21 LTS & Python 3.12 without altering global macOS defaults.
if [ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [ -d "$HOME/.local/bin" ]; then
  export PATH="$HOME/.local/bin:$PATH"
fi

echo "[env] JAVA_HOME=${JAVA_HOME:-unset}"
java -version 2>&1 | head -n 1
python3.12 --version 2>&1 || python3 --version 2>&1
pnpm --version 2>&1 | sed 's/^/pnpm /'
