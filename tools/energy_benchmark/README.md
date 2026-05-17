# Ресурсный блок энергопотребления

Вспомогательная утилита для ресурсного блока эксперимента. Android-приложение выполняет нагрузку фиксированной длительности, а компьютер через ADB сохраняет системный отчет `batterystats` для каждого блока.

## Запуск

```bash
tools/energy_benchmark/run_energy_benchmark.sh \
  --host 192.168.1.140 \
  --duration-seconds 300 \
  --idle-seconds 600 \
  --output-dir energy_reports
```

Если подключено несколько устройств:

```bash
tools/energy_benchmark/run_energy_benchmark.sh \
  --device <adb-serial> \
  --host 192.168.1.140
```

## Что выполняется

- холостой блок без сетевых запросов;
- рабочие блоки по поддерживаемым сочетаниям протоколов, сценариев и режимов `h_req`/`h_series`;
- сохранение `logcat`, полного `batterystats` и checkin-формата системного отчета.

Матрица содержит 76 рабочих блоков: REST и SOAP для `S1-S6`, GraphQL для `S1-S8`, gRPC и WebSocket для `S1-S9`, каждый в двух режимах соединения.

## Режим батареи

Перед рабочим блоком утилита сбрасывает статистику:

```bash
adb shell dumpsys batterystats --reset
```

После блока сохраняется отчет:

```bash
adb shell dumpsys batterystats --charged
```

Полученные значения являются системной оценкой Android по UID приложения, а не прямым физическим измерением внешним прибором.
