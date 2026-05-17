#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="ru.meeweel.network_protocols_app"
ACTIVITY_NAME="ru.meeweel.network_protocols_app.MainActivity"
ACTION_NAME="ru.meeweel.network_protocols_app.ENERGY_BLOCK"

# Вспомогательная утилита ресурсного блока.
# Она через ADB запускает фиксированный блок нагрузки, ждет служебный маркер
# в logcat и сохраняет системный отчет batterystats.
DEFAULT_OUTPUT_DIR="energy_reports"
DEFAULT_DURATION_SECONDS="300"
DEFAULT_IDLE_SECONDS="600"
DEFAULT_HTTP_PORT="8080"
DEFAULT_GRPC_PORT="9090"
DEFAULT_TIMEOUT_EXTRA_SECONDS="180"

DEVICE_SERIAL=""
BACKEND_HOST=""
HTTP_PORT="$DEFAULT_HTTP_PORT"
GRPC_PORT="$DEFAULT_GRPC_PORT"
DURATION_SECONDS="$DEFAULT_DURATION_SECONDS"
IDLE_SECONDS="$DEFAULT_IDLE_SECONDS"
OUTPUT_DIR="$DEFAULT_OUTPUT_DIR"
SEED="$(date +%s)"
MODES="both"
STOP_ON_ERROR="false"
RESUME_FROM_DIR=""
RESUME_START_INDEX=""
RESUME_EFFECTIVE_START_INDEX=""
STOP_AFTER_INDEX=""
SEED_WAS_SET="false"

usage() {
    cat <<USAGE
Использование:
  tools/energy_benchmark/run_energy_benchmark.sh --host <backend-host> [options]

Параметры:
  --device <serial>              Серийный номер ADB-устройства. Нужен только при нескольких подключенных устройствах.
  --host <host>                  Адрес серверного сервиса, доступный с Android-устройства.
  --http-port <port>             HTTP-порт серверного сервиса. По умолчанию: 8080.
  --grpc-port <port>             gRPC-порт серверного сервиса. По умолчанию: 9090.
  --duration-seconds <seconds>   Длительность рабочего блока. По умолчанию: 300.
  --idle-seconds <seconds>       Длительность холостого блока. По умолчанию: 600.
  --output-dir <path>            Каталог для отчетов. По умолчанию: energy_reports.
  --seed <number>                Начальное значение для перемешивания плана. По умолчанию: текущее время Unix.
  --modes <both|h_req|h_series>  Режимы соединения. По умолчанию: both.
  --resume-from <run-dir>        Продолжить существующее измерение по его plan_shuffled.tsv.
  --resume-start-index <number>  Первый рабочий блок для запуска. По умолчанию: первый отсутствующий успешный блок.
  --stop-after-index <number>    Остановиться после указанного рабочего блока.
  --stop-on-error                Остановиться после первого блока с ошибкой.
  --help                         Показать эту справку.

Утилита выполняет один холостой блок, затем поддерживаемую матрицу
протоколов, сценариев и режимов соединения. При продолжении измерения
создается новый холостой блок, после чего выполняются только оставшиеся
строки существующего перемешанного плана. Для каждого блока сбрасывается
статистика batterystats, Android-приложение запускается через ADB, затем
утилита ожидает ENERGY_BLOCK_DONE в logcat и сохраняет batterystats в файл.
USAGE
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --device)
            DEVICE_SERIAL="${2:-}"
            shift 2
            ;;
        --host)
            BACKEND_HOST="${2:-}"
            shift 2
            ;;
        --http-port)
            HTTP_PORT="${2:-}"
            shift 2
            ;;
        --grpc-port)
            GRPC_PORT="${2:-}"
            shift 2
            ;;
        --duration-seconds)
            DURATION_SECONDS="${2:-}"
            shift 2
            ;;
        --idle-seconds)
            IDLE_SECONDS="${2:-}"
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="${2:-}"
            shift 2
            ;;
        --seed)
            SEED="${2:-}"
            SEED_WAS_SET="true"
            shift 2
            ;;
        --modes)
            MODES="${2:-}"
            shift 2
            ;;
        --resume-from)
            RESUME_FROM_DIR="${2:-}"
            shift 2
            ;;
        --resume-start-index)
            RESUME_START_INDEX="${2:-}"
            shift 2
            ;;
        --stop-after-index)
            STOP_AFTER_INDEX="${2:-}"
            shift 2
            ;;
        --stop-on-error)
            STOP_ON_ERROR="true"
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo "Неизвестный аргумент: $1" >&2
            usage
            exit 2
            ;;
    esac
done

if [ -z "$BACKEND_HOST" ]; then
    echo "Не указан адрес серверного сервиса. Передайте --host <backend-host>." >&2
    exit 2
fi

case "$DURATION_SECONDS" in
    ''|*[!0-9]*)
        echo "--duration-seconds must be a positive integer." >&2
        exit 2
        ;;
esac

case "$IDLE_SECONDS" in
    ''|*[!0-9]*)
        echo "--idle-seconds must be a positive integer." >&2
        exit 2
        ;;
esac

if [ "$DURATION_SECONDS" -lt 30 ] || [ "$IDLE_SECONDS" -lt 30 ]; then
    echo "Block duration must be at least 30 seconds." >&2
    exit 2
fi

case "$MODES" in
    both|h_req|h_series) ;;
    *)
        echo "--modes must be one of: both, h_req, h_series." >&2
        exit 2
        ;;
esac

case "$RESUME_START_INDEX" in
    ""|*[!0-9]*)
        if [ -n "$RESUME_START_INDEX" ]; then
            echo "--resume-start-index must be a positive integer." >&2
            exit 2
        fi
        ;;
esac

case "$STOP_AFTER_INDEX" in
    ""|*[!0-9]*)
        if [ -n "$STOP_AFTER_INDEX" ]; then
            echo "--stop-after-index must be a positive integer." >&2
            exit 2
        fi
        ;;
esac

if [ -n "$RESUME_START_INDEX" ] && [ "$RESUME_START_INDEX" -lt 1 ]; then
    echo "--resume-start-index must be at least 1." >&2
    exit 2
fi

if [ -n "$STOP_AFTER_INDEX" ] && [ "$STOP_AFTER_INDEX" -lt 1 ]; then
    echo "--stop-after-index must be at least 1." >&2
    exit 2
fi

if [ -n "$RESUME_START_INDEX" ] && [ -z "$RESUME_FROM_DIR" ]; then
    echo "--resume-start-index requires --resume-from." >&2
    exit 2
fi

if [ -n "$RESUME_FROM_DIR" ]; then
    RESUME_FROM_DIR="${RESUME_FROM_DIR%/}"
    if [ ! -d "$RESUME_FROM_DIR" ]; then
        echo "--resume-from directory does not exist: $RESUME_FROM_DIR" >&2
        exit 2
    fi
    if [ ! -f "$RESUME_FROM_DIR/plan.tsv" ] || [ ! -f "$RESUME_FROM_DIR/plan_shuffled.tsv" ]; then
        echo "--resume-from must contain plan.tsv and plan_shuffled.tsv." >&2
        exit 2
    fi
    if [ ! -f "$RESUME_FROM_DIR/manifest.tsv" ]; then
        echo "--resume-from must contain manifest.tsv for automatic resume detection." >&2
        exit 2
    fi
    if [ "$SEED_WAS_SET" = "false" ] && [ -f "$RESUME_FROM_DIR/device.txt" ]; then
        resume_seed="$(awk -F '\t' '$1 == "seed" { print $2; exit }' "$RESUME_FROM_DIR/device.txt")"
        if [ -n "$resume_seed" ]; then
            SEED="$resume_seed"
        fi
    fi
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "adb is not available in PATH." >&2
    exit 2
fi

adb_cmd() {
    if [ -n "$DEVICE_SERIAL" ]; then
        adb -s "$DEVICE_SERIAL" "$@"
    else
        adb "$@"
    fi
}

device_count="$(adb devices | awk 'NR > 1 && $2 == "device" { count += 1 } END { print count + 0 }')"
if [ -z "$DEVICE_SERIAL" ] && [ "$device_count" -ne 1 ]; then
    echo "Exactly one ADB device must be connected or pass --device <serial>. Connected: $device_count." >&2
    adb devices >&2
    exit 2
fi

RUN_ID="$(date +%Y%m%d_%H%M%S)_seed_${SEED}"
RUN_DIR="${OUTPUT_DIR%/}/${RUN_ID}"
PLAN_FILE="$RUN_DIR/plan.tsv"
SHUFFLED_PLAN_FILE="$RUN_DIR/plan_shuffled.tsv"
MANIFEST_FILE="$RUN_DIR/manifest.tsv"
DEVICE_INFO_FILE="$RUN_DIR/device.txt"
APP_UID=""
mkdir -p "$RUN_DIR"

cleanup() {
    adb_cmd shell dumpsys battery reset >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

write_device_info() {
    # Паспорт запуска нужен, чтобы потом не гадать, на каком устройстве,
    # с каким seed и каким UID приложения был получен файл batterystats.
    APP_UID="$(adb_cmd shell cmd package list packages -U "$PACKAGE_NAME" | awk -F 'uid:' '{ print $2; exit }' | tr -d '\r')"
    {
        echo "run_id	$RUN_ID"
        echo "seed	$SEED"
        echo "backend_host	$BACKEND_HOST"
        echo "http_port	$HTTP_PORT"
        echo "grpc_port	$GRPC_PORT"
        echo "duration_seconds	$DURATION_SECONDS"
        echo "idle_seconds	$IDLE_SECONDS"
        echo "modes	$MODES"
        if [ -n "$RESUME_FROM_DIR" ]; then
            echo "resume_from_dir	$RESUME_FROM_DIR"
            echo "resume_start_index	$RESUME_EFFECTIVE_START_INDEX"
            echo "stop_after_index	${STOP_AFTER_INDEX:-}"
        fi
        echo "device_serial	${DEVICE_SERIAL:-auto}"
        echo "device_model	$(adb_cmd shell getprop ro.product.model | tr -d '\r')"
        echo "device_manufacturer	$(adb_cmd shell getprop ro.product.manufacturer | tr -d '\r')"
        echo "android_release	$(adb_cmd shell getprop ro.build.version.release | tr -d '\r')"
        echo "android_sdk	$(adb_cmd shell getprop ro.build.version.sdk | tr -d '\r')"
        echo "package_name	$PACKAGE_NAME"
        echo "package_uid	${APP_UID:-unknown}"
    } > "$DEVICE_INFO_FILE"
}

append_supported_pairs() {
    local protocol="$1"
    shift
    local scenario
    for scenario in "$@"; do
        if [ "$MODES" = "both" ] || [ "$MODES" = "h_req" ]; then
            printf '%s\t%s\t%s\t%s\n' "workload" "$protocol" "$scenario" "h_req" >> "$PLAN_FILE"
        fi
        if [ "$MODES" = "both" ] || [ "$MODES" = "h_series" ]; then
            printf '%s\t%s\t%s\t%s\n' "workload" "$protocol" "$scenario" "h_series" >> "$PLAN_FILE"
        fi
    done
}

write_plan() {
    if [ -n "$RESUME_FROM_DIR" ]; then
        cp "$RESUME_FROM_DIR/plan.tsv" "$PLAN_FILE"
        cp "$RESUME_FROM_DIR/plan_shuffled.tsv" "$SHUFFLED_PLAN_FILE"
        return
    fi

    # План повторяет матрицу допустимости методики: REST/SOAP только S1-S6,
    # GraphQL до S8, а gRPC и WebSocket еще и S9.
    : > "$PLAN_FILE"
    append_supported_pairs "REST" "S1" "S2" "S3" "S4" "S5" "S6"
    append_supported_pairs "SOAP" "S1" "S2" "S3" "S4" "S5" "S6"
    append_supported_pairs "GRAPHQL" "S1" "S2" "S3" "S4" "S5" "S6" "S7" "S8"
    append_supported_pairs "GRPC" "S1" "S2" "S3" "S4" "S5" "S6" "S7" "S8" "S9"
    append_supported_pairs "WEBSOCKET" "S1" "S2" "S3" "S4" "S5" "S6" "S7" "S8" "S9"
    awk -v seed="$SEED" 'BEGIN { srand(seed) } { printf "%.12f\t%s\n", rand(), $0 }' "$PLAN_FILE" |
        sort -n |
        cut -f2- > "$SHUFFLED_PLAN_FILE"
}

detect_resume_start_index() {
    if [ -n "$RESUME_START_INDEX" ]; then
        echo "$RESUME_START_INDEX"
        return
    fi
    awk -F '\t' '
        NR > 1 && $3 == "workload" && $11 == "success" && ($1 + 0) > max {
            max = $1 + 0
        }
        END {
            print max + 1
        }
    ' "$RESUME_FROM_DIR/manifest.tsv"
}

wait_for_marker() {
    local block_id="$1"
    local logcat_file="$2"
    local timeout_seconds="$3"
    local started_at
    local now
    started_at="$(date +%s)"
    # Приложение само пишет в logcat, что блок завершен или упал.
    # Скрипт просто ждет эту фразу, чтобы не обрезать измерение раньше времени.
    while true; do
        if grep -F "ENERGY_BLOCK_DONE blockId=$block_id" "$logcat_file" >/dev/null 2>&1; then
            return 0
        fi
        if grep -F "ENERGY_BLOCK_FAILED blockId=$block_id" "$logcat_file" >/dev/null 2>&1; then
            return 1
        fi
        now="$(date +%s)"
        if [ $((now - started_at)) -ge "$timeout_seconds" ]; then
            return 2
        fi
        sleep 2
    done
}

uid_label() {
    local uid="$1"
    if [ -z "$uid" ]; then
        echo ""
        return
    fi
    if [ "$uid" -ge 10000 ] 2>/dev/null; then
        echo "u0a$((uid - 10000))"
    else
        echo "$uid"
    fi
}

extract_power_line() {
    local batterystats_file="$1"
    local uid="$2"
    local label
    label="$(uid_label "$uid")"
    # batterystats огромный. Для таблицы нам нужна только строка Estimated power
    # use по UID нашего приложения, поэтому вытаскиваем ее отдельно.
    awk -v uid="$uid" -v label="$label" '
        /Estimated power use/ { in_power = 1 }
        {
            line = tolower($0)
            label_lower = tolower(label)
        }
        in_power && uid != "" && index(line, "uid " uid) > 0 {
            gsub(/^[ \t]+/, "", $0)
            print $0
            exit
        }
        in_power && label != "" && index(line, "uid " label_lower) > 0 {
            gsub(/^[ \t]+/, "", $0)
            print $0
            exit
        }
    ' "$batterystats_file" | tr '\t' ' '
}

run_block() {
    local index="$1"
    local kind="$2"
    local protocol="$3"
    local scenario="$4"
    local mode="$5"
    local duration="$6"
    local protocol_token
    local scenario_token
    local mode_token
    local block_id
    local logcat_file
    local batterystats_file
    local checkin_file
    local power_line
    local status
    local timeout_seconds
    protocol_token="$(echo "${protocol:-idle}" | tr '[:upper:]' '[:lower:]')"
    scenario_token="$(echo "${scenario:-idle}" | tr '[:upper:]' '[:lower:]')"
    mode_token="$(echo "${mode:-idle}" | tr '[:upper:]' '[:lower:]')"
    block_id="$(printf '%03d_%s_%s_%s' "$index" "$protocol_token" "$scenario_token" "$mode_token")"
    logcat_file="$RUN_DIR/${block_id}_logcat.txt"
    batterystats_file="$RUN_DIR/${block_id}_batterystats.txt"
    checkin_file="$RUN_DIR/${block_id}_batterystats_checkin.csv"
    timeout_seconds=$((duration + DEFAULT_TIMEOUT_EXTRA_SECONDS))

    echo "[$index] $kind $protocol $scenario $mode ${duration}s"
    # Каждый блок начинается с чистого состояния Android-статистики.
    # Иначе расход заряда от прошлого блока смешался бы с текущим.
    adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
    adb_cmd shell dumpsys battery reset >/dev/null 2>&1 || true
    adb_cmd shell dumpsys battery unplug >/dev/null
    adb_cmd shell dumpsys batterystats --reset >/dev/null
    adb_cmd logcat -c
    if [ -n "$DEVICE_SERIAL" ]; then
        adb -s "$DEVICE_SERIAL" logcat -v time EnergyBenchmark:I AndroidRuntime:E '*:S' > "$logcat_file" &
    else
        adb logcat -v time EnergyBenchmark:I AndroidRuntime:E '*:S' > "$logcat_file" &
    fi
    local logcat_pid=$!

    if [ "$kind" = "idle" ]; then
        # Idle-блок ничего не гоняет по сети. Это фон устройства и приложения.
        adb_cmd shell am start -W \
            -n "$PACKAGE_NAME/$ACTIVITY_NAME" \
            -a "$ACTION_NAME" \
            --es runMode energy \
            --es energyKind idle \
            --es blockId "$block_id" \
            --ei durationSeconds "$duration" >/dev/null
    else
        # Рабочий блок запускает одну пару протокол-сценарий-режим соединения
        # на фиксированное время, чтобы потом сравнить расход по одинаковому окну.
        adb_cmd shell am start -W \
            -n "$PACKAGE_NAME/$ACTIVITY_NAME" \
            -a "$ACTION_NAME" \
            --es runMode energy \
            --es energyKind workload \
            --es blockId "$block_id" \
            --es protocol "$protocol" \
            --es scenario "$scenario" \
            --es connectionMode "$mode" \
            --ei durationSeconds "$duration" \
            --es backendHost "$BACKEND_HOST" \
            --ei httpPort "$HTTP_PORT" \
            --ei grpcPort "$GRPC_PORT" >/dev/null
    fi

    status="success"
    if wait_for_marker "$block_id" "$logcat_file" "$timeout_seconds"; then
        status="success"
    else
        local marker_status="$?"
        if [ "$marker_status" -eq 1 ]; then
            status="app_failed"
        else
            status="timeout"
        fi
    fi

    kill "$logcat_pid" >/dev/null 2>&1 || true
    wait "$logcat_pid" >/dev/null 2>&1 || true
    # Сохраняем полный отчет и checkin-формат: первый удобен для просмотра,
    # второй - для последующей табличной обработки.
    adb_cmd shell dumpsys batterystats --charged > "$batterystats_file"
    adb_cmd shell dumpsys batterystats --checkin > "$checkin_file"
    power_line="$(extract_power_line "$batterystats_file" "$APP_UID")"
    adb_cmd shell dumpsys battery reset >/dev/null 2>&1 || true

    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$index" "$block_id" "$kind" "${protocol:-}" "${scenario:-}" "${mode:-}" "$duration" \
        "$batterystats_file" "$checkin_file" "$logcat_file" "$status" "${power_line:-}" >> "$MANIFEST_FILE"

    if [ "$status" != "success" ]; then
        echo "Блок $block_id завершен со статусом $status." >&2
        if [ "$STOP_ON_ERROR" = "true" ]; then
            exit 1
        fi
    fi
}

if [ -n "$RESUME_FROM_DIR" ]; then
    RESUME_EFFECTIVE_START_INDEX="$(detect_resume_start_index)"
    if [ -z "$RESUME_EFFECTIVE_START_INDEX" ] || [ "$RESUME_EFFECTIVE_START_INDEX" -lt 1 ]; then
        echo "Failed to determine resume start index." >&2
        exit 2
    fi
    if [ -n "$STOP_AFTER_INDEX" ] && [ "$STOP_AFTER_INDEX" -lt "$RESUME_EFFECTIVE_START_INDEX" ]; then
        echo "--stop-after-index must be greater than or equal to resume start index." >&2
        exit 2
    fi
fi

write_plan
write_device_info
printf 'index\tblock_id\tkind\tprotocol\tscenario\tmode\tduration_seconds\tbatterystats_file\tbatterystats_checkin_file\tlogcat_file\tstatus\tapp_power_line\n' > "$MANIFEST_FILE"

echo "Каталог отчетов ресурсного блока: $RUN_DIR"
echo "Plan seed: $SEED"
run_block 0 "idle" "" "" "" "$IDLE_SECONDS"

block_index=1
while IFS=$'\t' read -r kind protocol scenario mode <&3; do
    if [ -n "$RESUME_FROM_DIR" ] && [ "$block_index" -lt "$RESUME_EFFECTIVE_START_INDEX" ]; then
        block_index=$((block_index + 1))
        continue
    fi
    if [ -n "$STOP_AFTER_INDEX" ] && [ "$block_index" -gt "$STOP_AFTER_INDEX" ]; then
        break
    fi
    run_block "$block_index" "$kind" "$protocol" "$scenario" "$mode" "$DURATION_SECONDS"
    block_index=$((block_index + 1))
done 3< "$SHUFFLED_PLAN_FILE"

echo "Ресурсный блок завершен: $RUN_DIR"
