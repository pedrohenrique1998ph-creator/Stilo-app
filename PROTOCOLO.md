# Protocolo inicial ESP32 ↔ Android (Bluetooth Classic SPP)

**Esta é uma proposta de protocolo para integração futura**, não uma confirmação de que o firmware gravado atualmente no ESP32 já o implemente.

- Nome Bluetooth sugerido: `STILO_OVERBOOST`
- Transporte: Bluetooth Classic SPP / UUID `00001101-0000-1000-8000-00805F9B34FB`.
- Mensagens em UTF-8, **uma linha JSON por mensagem**, terminada por `\n`.
- O Android não controla diretamente nenhuma saída: envia um pedido e apresenta o estado medido/publicado pelo ESP32.

## ESP32 → Android

```json
{"type":"telemetry","rpm":820,"speed":0,"voltage":13.9,"ac":{"requested":true,"allowed":false,"compressor":false,"pressure_ok":false,"engine_on":true,"fan_on":true,"reason":"Pressostato não liberou"}}
```

O Android também entende chaves diretas: `ac_requested`, `ac_allowed`, `compressor`, `pressure_ok`, `engine_on`, `fan_on`, `reason`.

Frame de CAN, enviado **somente como observação**:

```json
{"type":"can","id":"0x280","data":"01 02 03 04 05 06 07 08"}
```

## Android → ESP32

Solicitar ligar/desligar ar-condicionado:

```json
{"type":"cmd","name":"ac_request","value":true}
```

Pedir snapshot da telemetria:

```json
{"type":"cmd","name":"telemetry_request"}
```

**Regra de segurança:** `ac_request:true` **não significa ativar compressor**. O ESP32 deve verificar pressostatos, proteção anti-ciclo, estado do motor, demais permissivos e falhas. Em perda de comunicação ou anomalia, a solicitação não pode burlar o circuito de proteção. Testar primeiro em bancada, sem compressor conectado. Protocolo não inclui firmware nem atualização OTA nesta versão.
