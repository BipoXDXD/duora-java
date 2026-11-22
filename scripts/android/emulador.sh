#!/usr/bin/env bash
# Emulador Android isolado para testar a instalação da PWA no Chrome.
#
# Uso: scripts/android/emulador.sh <instalar|subir|descer|status>
#
#   instalar  instala platform-tools, emulator e a imagem de sistema (com Play
#             Store e Chrome) pelo sdkmanager e cria o AVD. Idempotente.
#   subir     liga o emulador sem janela e espera o boot terminar.
#   descer    desliga o emulador.
#   status    mostra SDK, AVD e se o emulador está ligado.
#
# Variáveis opcionais:
#   ANDROID_SDK_ROOT       raiz do SDK (padrão: <brew --prefix>/share/android-commandlinetools)
#   DUORA_AVD              nome do AVD (padrão: duora-pixel)
#   DUORA_AVD_PORTA        porta de console do emulador, par entre 5554 e 5682 (padrão: 5580)
#   DUORA_AVD_RAM_MB       RAM do emulador (padrão: 2048)
#   DUORA_AVD_IMAGEM       pacote da imagem (padrão: system-images;android-35;google_apis_playstore;arm64-v8a)
#   DUORA_AVD_TIMEOUT_S    tempo máximo de boot em segundos (padrão: 300)
set -euo pipefail

RAIZ_REPO="$(cd "$(dirname "$0")/../.." && pwd)"
SAIDA="$RAIZ_REPO/target/android"

AVD="${DUORA_AVD:-duora-pixel}"
PORTA="${DUORA_AVD_PORTA:-5580}"
SERIAL="emulator-$PORTA"
RAM_MB="${DUORA_AVD_RAM_MB:-2048}"
TIMEOUT_S="${DUORA_AVD_TIMEOUT_S:-300}"
IMAGEM="${DUORA_AVD_IMAGEM:-system-images;android-35;google_apis_playstore;arm64-v8a}"
DISPOSITIVO="pixel_7"

erro() { echo "erro: $*" >&2; exit 1; }
info() { echo "==> $*"; }

resolver_sdk() {
  if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    if [[ -n "${ANDROID_HOME:-}" ]]; then
      ANDROID_SDK_ROOT="$ANDROID_HOME"
    elif command -v brew >/dev/null 2>&1; then
      ANDROID_SDK_ROOT="$(brew --prefix)/share/android-commandlinetools"
    else
      erro "defina ANDROID_SDK_ROOT (Homebrew não encontrado para descobrir o SDK)"
    fi
  fi
  export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"
  ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
  EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
}

exigir_ferramentas() {
  [[ -x "$ADB" ]] || erro "adb ausente em $ADB; rode: $0 instalar"
  [[ -x "$EMULATOR" ]] || erro "emulator ausente em $EMULATOR; rode: $0 instalar"
  avd_existe || erro "AVD '$AVD' não existe; rode: $0 instalar"
}

avd_existe() { "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD"; }

ligado() {
  "$ADB" devices 2>/dev/null | grep -q "^${SERIAL}[[:space:]]*device" || return 1
  [[ "$("$ADB" -s "$SERIAL" emu avd name 2>/dev/null | head -1 | tr -d '\r')" == "$AVD" ]]
}

boot_completo() {
  [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]
}

cmd_instalar() {
  command -v sdkmanager >/dev/null 2>&1 || erro "sdkmanager ausente; instale: brew install --cask android-commandlinetools"
  command -v avdmanager >/dev/null 2>&1 || erro "avdmanager ausente; instale: brew install --cask android-commandlinetools"

  info "aceitando licenças do SDK em $ANDROID_SDK_ROOT"
  yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true

  info "instalando platform-tools, emulator e $IMAGEM (pula o que já existe)"
  local log_sdk
  log_sdk="$(mktemp)"
  sdkmanager --sdk_root="$ANDROID_SDK_ROOT" "platform-tools" "emulator" "$IMAGEM" >"$log_sdk" 2>&1 \
    || { tail -20 "$log_sdk" >&2; rm -f "$log_sdk"; erro "sdkmanager falhou"; }
  rm -f "$log_sdk"

  if avd_existe; then
    info "AVD '$AVD' já existe"
  else
    info "criando AVD '$AVD' ($DISPOSITIVO, ${RAM_MB} MB)"
    echo no | avdmanager create avd -n "$AVD" -k "$IMAGEM" -d "$DISPOSITIVO" >/dev/null \
      || erro "avdmanager não criou o AVD"
  fi

  local config="${ANDROID_AVD_HOME:-$HOME/.android/avd}/$AVD.avd/config.ini"
  [[ -f "$config" ]] || erro "config do AVD não encontrada: $config"
  ajustar_config "$config" hw.ramSize "${RAM_MB}M"
  ajustar_config "$config" hw.keyboard yes
  ajustar_config "$config" hw.gpu.enabled yes
  ajustar_config "$config" hw.gpu.mode swiftshader_indirect
  ajustar_config "$config" fastboot.forceColdBoot yes
  if [[ "$IMAGEM" == *playstore* ]]; then
    ajustar_config "$config" PlayStore.enabled yes
  fi
  info "pronto: SDK em $ANDROID_SDK_ROOT, AVD em $(dirname "$config")"
}

ajustar_config() {
  local arquivo="$1" chave="$2" valor="$3"
  if grep -q "^${chave}[[:space:]]*=" "$arquivo"; then
    sed -i '' "s|^${chave}[[:space:]]*=.*|$chave=$valor|" "$arquivo"
  else
    echo "$chave=$valor" >>"$arquivo"
  fi
}

cmd_subir() {
  exigir_ferramentas
  if ligado; then
    info "emulador '$AVD' já ligado ($SERIAL)"
    return 0
  fi
  if "$ADB" devices 2>/dev/null | grep -q "^$SERIAL"; then
    erro "a porta $PORTA já está em uso por outro emulador; defina DUORA_AVD_PORTA"
  fi

  mkdir -p "$SAIDA"
  local log="$SAIDA/emulador.log" inicio=$SECONDS
  info "ligando '$AVD' sem janela na porta $PORTA (log: $log)"
  nohup "$EMULATOR" -avd "$AVD" -port "$PORTA" -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -memory "$RAM_MB" -no-snapshot \
    >"$log" 2>&1 &
  local pid=$!

  "$ADB" start-server >/dev/null 2>&1 || true
  until boot_completo; do
    kill -0 "$pid" 2>/dev/null || { tail -20 "$log" >&2; erro "o emulador encerrou durante o boot"; }
    if (( SECONDS - inicio > TIMEOUT_S )); then
      tail -20 "$log" >&2
      "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || kill "$pid" 2>/dev/null || true
      erro "boot não terminou em ${TIMEOUT_S}s"
    fi
    sleep 3
  done

  # Animações desligadas deixam toques por uiautomator determinísticos.
  for chave in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$ADB" -s "$SERIAL" shell settings put global "$chave" 0 >/dev/null 2>&1 || true
  done
  "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  info "boot completo em $((SECONDS - inicio))s ($SERIAL)"
}

cmd_descer() {
  [[ -x "$ADB" ]] || erro "adb ausente em $ADB"
  if ! "$ADB" devices 2>/dev/null | grep -q "^$SERIAL"; then
    info "emulador não está ligado"
    return 0
  fi
  "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
  local inicio=$SECONDS
  while "$ADB" devices 2>/dev/null | grep -q "^$SERIAL"; do
    (( SECONDS - inicio > 60 )) && erro "o emulador não desligou em 60s"
    sleep 1
  done
  info "emulador desligado"
}

cmd_status() {
  echo "SDK:     $ANDROID_SDK_ROOT"
  echo "adb:     $([[ -x "$ADB" ]] && echo ok || echo ausente)"
  echo "emulator:$([[ -x "$EMULATOR" ]] && echo " ok" || echo " ausente")"
  echo "AVD:     $AVD ($([[ -x "$EMULATOR" ]] && avd_existe && echo existe || echo ausente))"
  if [[ -x "$ADB" ]] && ligado; then
    echo "estado:  ligado ($SERIAL, boot_completed=$(boot_completo && echo 1 || echo 0))"
  else
    echo "estado:  desligado"
  fi
}

resolver_sdk
case "${1:-}" in
  instalar) cmd_instalar ;;
  subir) cmd_subir ;;
  descer) cmd_descer ;;
  status) cmd_status ;;
  *) echo "uso: $0 <instalar|subir|descer|status>" >&2; exit 2 ;;
esac
