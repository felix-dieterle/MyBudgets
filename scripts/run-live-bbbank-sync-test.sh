#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

prompt() {
  local text="$1"
  local var
  read -r -p "$text" var
  printf '%s' "$var"
}

prompt_secret() {
  local text="$1"
  local var
  read -r -s -p "$text" var
  echo
  printf '%s' "$var"
}

trim() {
  local s="$1"
  # shellcheck disable=SC2001
  s="$(printf '%s' "$s" | sed -e 's/^[[:space:]]\+//' -e 's/[[:space:]]\+$//')"
  printf '%s' "$s"
}

echo "Live BBBank Sync Test (ohne App-UI)"
echo "Die Eingaben werden nur als Umgebungsvariablen für diesen Prozess genutzt."

IBAN="$(trim "$(prompt "IBAN: ")")"
USER_ID="$(trim "$(prompt "Nutzerkennung (Online-Banking Login): ")")"
PIN="$(trim "$(prompt_secret "PIN: ")")"
BANK_CODE="$(trim "$(prompt "BLZ (optional, Enter=aus IBAN ableiten): ")")"
TAN_METHOD="$(trim "$(prompt "TAN-Methode (optional, z.B. 900): ")")"
DECOUPLED_WAIT="$(trim "$(prompt "Decoupled-Wartezeit in Sekunden (Default 30): ")")"
OVERALL_TIMEOUT="$(trim "$(prompt "Gesamt-Test-Timeout in Sekunden (Default 420): ")")"

if [[ -z "$DECOUPLED_WAIT" ]]; then
  DECOUPLED_WAIT="30"
fi
if [[ -z "$OVERALL_TIMEOUT" ]]; then
  OVERALL_TIMEOUT="420"
fi

echo
echo "Wenn deine Bank eine klassische TAN statt Decoupled verlangt, kannst du optional eine TAN setzen."
TAN="$(prompt "TAN (optional, Enter=leer): ")"

IBAN_COMPACT="${IBAN// /}"
if [[ -z "$IBAN_COMPACT" ]]; then
  echo "Fehler: IBAN darf nicht leer sein."
  exit 2
fi
if [[ ${#IBAN_COMPACT} -ne 22 || ${IBAN_COMPACT:0:2} != "DE" ]]; then
  echo "Warnung: IBAN sieht nicht wie eine deutsche IBAN aus (erwartet DE + 20 Zeichen)."
fi
if [[ -z "$USER_ID" ]]; then
  echo "Fehler: Nutzerkennung darf nicht leer sein."
  exit 2
fi

export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.9-ms
export PATH="$JAVA_HOME/bin:$HOME/android-sdk/platform-tools:$PATH"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"

# Credentials werden als JVM System Properties übergeben (-D), damit sie zuverlässig
# im geforkten Test-JVM ankommen (Gradle-Env-Var-Vererbung ist nicht garantiert).
EXTRA_PROPS=(
  "-Dmybudgets.live.test=true"
  "-Dmybudgets.hbci.logLevel=4"
  "-Dmybudgets.test.iban=${IBAN}"
  "-Dmybudgets.test.userId=${USER_ID}"
  "-Dmybudgets.test.pin=${PIN}"
  "-Dmybudgets.test.bankCode=${BANK_CODE}"
  "-Dmybudgets.test.tanMethod=${TAN_METHOD}"
  "-Dmybudgets.test.decoupledWaitSeconds=${DECOUPLED_WAIT}"
  "-Dmybudgets.test.overallTimeoutSeconds=${OVERALL_TIMEOUT}"
)
if [[ -n "$TAN" ]]; then
  EXTRA_PROPS+=( "-Dmybudgets.test.tan=${TAN}" )
fi

./gradlew :app:testDebugUnitTest \
  --tests de.mybudgets.app.viewmodel.AccountViewModelLiveBankSyncTest \
  --console=plain --no-daemon \
  "${EXTRA_PROPS[@]}"
