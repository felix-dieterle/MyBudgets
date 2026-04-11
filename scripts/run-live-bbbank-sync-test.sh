#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# ─────────────────────────────────────────────────────────────────────────────
# ITERATIVE DEBUGGING LOOP SETUP
# ─────────────────────────────────────────────────────────────────────────────

LOOP_DIR=".test-loop"
mkdir -p "$LOOP_DIR"

# Marker files for coordination between test runs and analysis
RUN_STARTED="$LOOP_DIR/.run-started"
RUN_FINISHED="$LOOP_DIR/.run-finished"
CHANGES_MADE="$LOOP_DIR/.changes-made"
LOG_FILE="$LOOP_DIR/test-run.log"
ANALYSIS_FILE="$LOOP_DIR/analysis.md"

# Helper to log both to console and file
log_both() {
  echo "$@"
  echo "$@" >> "$LOG_FILE"
}

cleanup_markers() {
  rm -f "$RUN_STARTED" "$RUN_FINISHED" "$CHANGES_MADE"
}

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

# ─────────────────────────────────────────────────────────────────────────────
# INITIAL SETUP
# ─────────────────────────────────────────────────────────────────────────────

echo "====== Live BBBank Sync Test (Iterative Debug Loop) ======"
echo "This test performs end-to-end FinTS/HBCI sync including real authentication."
echo "Output will be saved to: $LOG_FILE"
echo ""

IBAN="$(trim "$(prompt "IBAN (z.B. DE89 3704 0044 0532 0130 00): ")")"
USER_ID="$(trim "$(prompt "Nutzerkennung (Online-Banking Login): ")")"
PIN="$(trim "$(prompt_secret "PIN: ")")"
BANK_CODE="$(trim "$(prompt "BLZ (optional, leer=aus IBAN ableiten): ")")"
TAN_METHOD="$(trim "$(prompt "TAN-Methode (optional, z.B. '900' für Secure Go): ")")"
DECOUPLED_WAIT="$(trim "$(prompt "Decoupled-Wartezeit Sekunden (Default 30): ")")"
DECOUPLED_RETRY_WAIT_MS="$(trim "$(prompt "Decoupled-Retry-Wartezeit Millisekunden (Default 2000): ")")"

# For iterative loops, extend timeout a bit
OVERALL_TIMEOUT="$(trim "$(prompt "Gesamt-Test-Timeout Sekunden (Default 420=7min): ")")"

# DEFAULT: Run in loop mode for iterative debugging
echo ""
echo "=== Debug-Loop Konfiguration ==="
LOOP_ITERATIONS="$(trim "$(prompt "Anzahl Durchläufe (Default 3): ")")"
if [[ -z "$LOOP_ITERATIONS" ]]; then
  LOOP_ITERATIONS="3"
fi

# Debug level default to 4 (can be modified before each run)
DEFAULT_HBCI_LOG_LEVEL="$(trim "$(prompt "HBCI Debug-Level 1-5 (Default 4): ")")"
if [[ -z "$DEFAULT_HBCI_LOG_LEVEL" ]]; then
  DEFAULT_HBCI_LOG_LEVEL="4"
fi

if [[ -z "$DECOUPLED_WAIT" ]]; then
  DECOUPLED_WAIT="30"
fi
if [[ -z "$DECOUPLED_RETRY_WAIT_MS" ]]; then
  DECOUPLED_RETRY_WAIT_MS="2000"
fi
if [[ -z "$OVERALL_TIMEOUT" ]]; then
  OVERALL_TIMEOUT="420"
fi

echo ""
echo "=== TAN-Eingabe ==="
echo "Falls die Bank TAN+ verlangt: Gib hier die mTAN/pushTAN-Nummer ein (optional)."
echo "Falls auf Secure Go/BestSign-Bestätigung gewartet wird: Enter drücken + im Secure Go bestätigen."
TAN="$(prompt "TAN (optional): ")"

# Validation
IBAN_COMPACT="${IBAN// /}"
if [[ -z "$IBAN_COMPACT" ]]; then
  echo "❌ Fehler: IBAN darf nicht leer sein."
  exit 2
fi
if [[ ${#IBAN_COMPACT} -ne 22 || ${IBAN_COMPACT:0:2} != "DE" ]]; then
  echo "⚠️  Warnung: IBAN sieht nicht wie eine deutsche IBAN aus (erwartet DE + 20 Zeichen)."
fi
if [[ -z "$USER_ID" ]]; then
  echo "❌ Fehler: Nutzerkennung darf nicht leer sein."
  exit 2
fi

export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.9-ms
export PATH="$JAVA_HOME/bin:$HOME/android-sdk/platform-tools:$PATH"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"

# ─────────────────────────────────────────────────────────────────────────────
# ITERATIVE LOOP
# ─────────────────────────────────────────────────────────────────────────────

for ((run=1; run<=LOOP_ITERATIONS; run++)); do
  # Clear old markers
  cleanup_markers

  # APPEND to log (don't clear previous runs)
  {
    echo ""
    echo "════════════════════════════════════════════"
    echo "🔄 RUN $run / $LOOP_ITERATIONS"
    echo "════════════════════════════════════════════"
    
    # For subsequent runs, ask if we should change debug level
    HBCI_LOG_LEVEL="$DEFAULT_HBCI_LOG_LEVEL"
    if [[ $run -gt 1 ]]; then
      echo ""
      echo "Debug-Level für diesen Run (aktuell: $HBCI_LOG_LEVEL, Enter= gleich wie vorher):"
      NEW_LEVEL="$(trim "$(prompt "Debug-Level (1-5): ")")"
      if [[ -n "$NEW_LEVEL" ]]; then
        HBCI_LOG_LEVEL="$NEW_LEVEL"
      fi
      echo ""
    fi

    echo "[RUN $run START] $(date)"
    echo "IBAN: ***${IBAN_COMPACT: -4}"
    echo "User: $USER_ID"
    echo "Debug Level: $HBCI_LOG_LEVEL"
  } >> "$LOG_FILE"
  
  # Also on console
  echo ""
  echo "════════════════════════════════════════════"
  echo "🔄 RUN $run / $LOOP_ITERATIONS (Debug Level: $HBCI_LOG_LEVEL)"
  echo "════════════════════════════════════════════"
  
  # Mark run as started
  touch "$RUN_STARTED"

  log_both ""
  log_both "Starting Live Sync Test..."
  log_both "  IBAN: ***${IBAN_COMPACT: -4}"
  log_both "  User: $USER_ID"
  log_both "  BLZ:  ${BANK_CODE:-(auto)}"
  log_both "  TAN Method: ${TAN_METHOD:-(auto)}"
  log_both "  Log Level: $HBCI_LOG_LEVEL"
  log_both ""

  # Credentials werden als JVM System Properties übergeben (-D)
  EXTRA_PROPS=(
    "-Dmybudgets.live.test=true"
    "-Dmybudgets.hbci.logLevel=${HBCI_LOG_LEVEL}"
    "-Dmybudgets.decoupled.retry.wait.millis=${DECOUPLED_RETRY_WAIT_MS}"
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

  # Run test and capture exit code
  TEST_EXIT_CODE=0
  ./gradlew :app:testDebugUnitTest \
    --tests de.mybudgets.app.viewmodel.AccountViewModelLiveBankSyncTest \
    --console=plain --no-daemon \
    "${EXTRA_PROPS[@]}" \
    2>&1 | tee -a "$LOG_FILE" \
    || TEST_EXIT_CODE=$?

  log_both ""
  log_both "[RUN $run END] $(date) - Exit Code: $TEST_EXIT_CODE"

  rm -f "$RUN_STARTED"
  touch "$RUN_FINISHED"

  if [[ $TEST_EXIT_CODE -eq 0 ]]; then
    log_both "✓ Test SUCCESS!"
    echo "✓ Run $run succeeded!"
  else
    log_both "✗ Test FAILED (Exit Code: $TEST_EXIT_CODE)"
    echo "✗ Run $run failed (Exit Code: $TEST_EXIT_CODE)"
  fi

  log_both "════════════════════════════════════════════"

  # If not the last run, wait for analysis/changes
  if [[ $run -lt $LOOP_ITERATIONS ]]; then
    echo ""
    echo "════════════════════════════════════════════"
    echo "⏸️  AWAITING ANALYSIS & CHANGES (Run $run/$LOOP_ITERATIONS)"
    echo "════════════════════════════════════════════"
    echo ""
    echo "📋 Log saved to: $LOG_FILE"
    echo ""
    echo "Next: Use the logs to debug, make code changes as needed."
    echo "Once ready for the next run, either:"
    echo "  1. Touch $CHANGES_MADE (marker file)"
    echo "  2. Or just press Enter here"
    read -r -p "Press Enter to start Run $((run+1))... (or Ctrl+C to abort)" 
    
    # Cleanup marker for next run
    rm -f "$CHANGES_MADE"
  else
    echo ""
    echo "════════════════════════════════════════════"
    echo "🎯 ALL RUNS COMPLETED"
    echo "════════════════════════════════════════════"
    echo ""
    echo "📋 Full log: $LOG_FILE"
    echo ""
  fi
done

echo ""
echo "Cleanup marker files..."
cleanup_markers

exit $TEST_EXIT_CODE
