package br.com.stilooverboost.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * STILO OVERBOOST — interface Android de telemetria e solicitações.
 * O ESP32/hardware é o único responsável pela validação dos permissivos do compressor.
 */
@SuppressLint("MissingPermission")
class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("overboost", MODE_PRIVATE) }
    private val ui = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var socket: BluetoothSocket? = null
    private var connectedTo = ""
    private var activePage = "HUB"
    private var scanning = false
    private var lastRefresh = 0L
    private var demo = false
    private var selectedTheme = "CLARO"
    private var screen: LinearLayout? = null
    private val discovered = linkedMapOf<String, BluetoothDevice>()
    private val telemetry = mutableMapOf<String, String>()
    private val logs = mutableListOf<String>()
    private val canFrames = mutableListOf<String>()
    private val services = listOf("HUB", "AR-COND.", "BLUETOOTH", "CAN", "AJUSTES")
    private val widgetNames = listOf("RPM", "VELOCIDADE", "TENSÃO", "AR", "CONEXÃO", "PRESSOSTATO", "CAN")
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        discovered[device.address] = device
                        if (activePage == "BLUETOOTH") render()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanning = false
                    if (activePage == "BLUETOOTH") render()
                }
            }
        }
    }

    data class Palette(
        val background: Int, val surface: Int, val text: Int,
        val subtle: Int, val accent: Int, val accentSoft: Int, val border: Int,
    )

    private val colors: Palette
        get() = when (selectedTheme) {
            "ESCURO" -> Palette(0xFF0F1521.toInt(), 0xFF1C2636.toInt(), Color.WHITE,
                0xFFADB9CC.toInt(), 0xFF51BEF5.toInt(), 0xFF243E55.toInt(), 0xFF354154.toInt())
            "AZUL RACING" -> Palette(0xFF0A172D.toInt(), 0xFF13284A.toInt(), Color.WHITE,
                0xFF9DB4D2.toInt(), 0xFF4AD7FF.toInt(), 0xFF214B73.toInt(), 0xFF28517B.toInt())
            else -> Palette(0xFFF3F6FC.toInt(), Color.WHITE, 0xFF172339.toInt(),
                0xFF66768C.toInt(), 0xFF1867D9.toInt(), 0xFFE8F1FF.toInt(), 0xFFE2E9F2.toInt())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTheme = preferences.getString("theme", "CLARO") ?: "CLARO"
        demo = preferences.getBoolean("demo", false)
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        writeLog("Aplicativo iniciado. Conexão Bluetooth aguardando.")
        render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 18, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
    }

    private fun text(message: String, size: Float = 15f, color: Int = colors.text, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = message
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = true
        }
    }

    private fun gap(container: LinearLayout, height: Int = 12) {
        container.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun panel(): LinearLayout = column().apply {
        background = shape(colors.surface, 20, colors.border)
        setPadding(dp(17), dp(16), dp(17), dp(16))
    }

    private fun action(caption: String, outlined: Boolean = false, click: () -> Unit): TextView {
        return text(caption, 14f, if (outlined) colors.accent else Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = shape(if (outlined) colors.accentSoft else colors.accent, 13)
            setOnClickListener { click() }
        }
    }

    private fun bodyTitle(eyebrow: String, title: String, subtitle: String? = null) {
        val target = screen ?: return
        target.addView(text(eyebrow, 11f, colors.accent, true))
        gap(target, 5)
        target.addView(text(title, 25f, colors.text, true))
        if (subtitle != null) {
            gap(target, 5)
            target.addView(text(subtitle, 13f, colors.subtle))
        }
        gap(target, 18)
    }

    private fun render() {
        if (isFinishing || isDestroyed) return
        val p = colors
        window.statusBarColor = p.background
        window.navigationBarColor = p.background
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (selectedTheme == "CLARO") View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
        val base = column().apply {
            setBackgroundColor(p.background)
        }
        setContentView(base)

        val header = row().apply { setPadding(dp(18), dp(22), dp(18), dp(12)) }
        val mark = text("S", 24f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = shape(p.accent, 14)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))
        val caption = column().apply { setPadding(dp(12), 0, 0, 0) }
        caption.addView(text("STILO OVERBOOST", 18f, p.text, true))
        caption.addView(text("CENTRAL INTELIGENTE • V0.1", 10f, p.subtle, true))
        header.addView(caption, LinearLayout.LayoutParams(0, -2, 1f))
        val status = when {
            demo -> "DEMO"
            connectedTo.isNotEmpty() -> "● ON"
            else -> "○ OFF"
        }
        val chip = text(status, 12f, p.accent, true).apply {
            background = shape(p.accentSoft, 24)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { activePage = "BLUETOOTH"; render() }
        }
        header.addView(chip)
        base.addView(header)

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = row().apply { setPadding(dp(14), dp(3), dp(14), dp(7)) }
        services.forEach { tab ->
            val active = tab == activePage
            val tabText = text(tab, 12f, if (active) Color.WHITE else p.subtle, true).apply {
                background = shape(if (active) p.accent else p.surface, 12,
                    if (active) null else p.border)
                setPadding(dp(14), dp(11), dp(14), dp(11))
                setOnClickListener { activePage = tab; render() }
            }
            val params = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(7) }
            tabs.addView(tabText, params)
        }
        tabScroll.addView(tabs)
        base.addView(tabScroll)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        screen = column().apply { setPadding(dp(17), dp(16), dp(17), dp(28)) }
        scroll.addView(screen)
        base.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        when (activePage) {
            "HUB" -> renderHub()
            "AR-COND." -> renderAc()
            "BLUETOOTH" -> renderBluetooth()
            "CAN" -> renderCan()
            else -> renderSettings()
        }
        val footer = text("STILO OVERBOOST  •  INTERFACE DE TESTES", 10f, p.subtle).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(11))
        }
        base.addView(footer)
    }

    private fun currentWidgets(): List<String> = (preferences.getString("widgets", "RPM,AR,TENSÃO,CONEXÃO")
        ?: "RPM,AR,TENSÃO,CONEXÃO").split(",").filter { it in widgetNames }.distinct()

    private fun live(key: String, fallback: String = "—"): String {
        if (demo) return when (key) {
            "rpm" -> "850"; "speed" -> "0"; "voltage" -> "13.8"
            "ac_requested" -> "true"; "ac_allowed" -> "false"; "compressor" -> "false"
            "pressure_ok" -> "false"; "engine_on" -> "true"; "fan_on" -> "true"
            "reason" -> "Pressostato sem liberação (simulado)"
            else -> fallback
        }
        return telemetry[key] ?: fallback
    }

    private fun yes(key: String) = when (live(key, "unknown").lowercase(Locale.ROOT)) {
        "true", "1", "yes", "on" -> "LIBERADO"
        "false", "0", "no", "off" -> "BLOQUEADO"
        else -> "SEM LEITURA"
    }

    private fun widgetValue(name: String): Pair<String, String> = when (name) {
        "RPM" -> live("rpm") + " rpm" to "Rotação do motor"
        "VELOCIDADE" -> live("speed") + " km/h" to "Velocidade informada"
        "TENSÃO" -> live("voltage") + " V" to "Rede elétrica"
        "AR" -> yes("compressor") to "Compressor do A/C"
        "CONEXÃO" -> (if (demo) "DEMO" else if (connectedTo.isNotEmpty()) "CONECTADO" else "OFFLINE") to "Bluetooth SPP"
        "PRESSOSTATO" -> yes("pressure_ok") to "Permissivo de pressão"
        else -> "${canFrames.size}" to "Frames CAN recebidos"
    }

    private fun renderHub() {
        bodyTitle("MEU PAINEL", "Seu carro. Seu controle.", "Widgets organizados do seu jeito, com leitura ao vivo pelo ESP32.")
        val destination = screen ?: return
        destination.addView(action("✦  PERSONALIZAR WIDGETS", true) { customizeWidgets() })
        gap(destination, 16)
        val selected = currentWidgets()
        if (selected.isEmpty()) {
            val empty = panel()
            empty.addView(text("Nenhum widget selecionado. Toque em Personalizar para adicionar.", 14f))
            destination.addView(empty)
        }
        selected.chunked(2).forEach { pair ->
            val metrics = row()
            pair.forEach { item ->
                val card = panel()
                card.addView(text(item, 11f, colors.subtle, true))
                gap(card, 14)
                val (value, detail) = widgetValue(item)
                card.addView(text(value, if (value.length > 11) 18f else 21f, colors.text, true))
                gap(card, 6)
                card.addView(text(detail, 11f, colors.subtle))
                val params = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(9) }
                metrics.addView(card, params)
            }
            if (pair.size == 1) metrics.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            destination.addView(metrics)
            gap(destination, 10)
        }
        gap(destination, 7)
        val connection = panel()
        connection.addView(text("LINK COM O ESP32", 12f, colors.accent, true))
        gap(connection, 8)
        connection.addView(text(when {
            demo -> "Modo demonstração ativo — dados fictícios."
            connectedTo.isNotEmpty() -> "Conectado a $connectedTo"
            else -> "Bluetooth desconectado. O painel funciona normalmente sem conexão."
        }, 14f, colors.text))
        gap(connection, 14)
        connection.addView(action("CONFIGURAR BLUETOOTH") { activePage = "BLUETOOTH"; render() })
        destination.addView(connection)
    }

    private fun customizeWidgets() {
        val chosen = currentWidgets().toSet()
        val checked = widgetNames.map { it in chosen }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Escolher widgets do Hub")
            .setMultiChoiceItems(widgetNames.toTypedArray(), checked) { _, index, enabled -> checked[index] = enabled }
            .setPositiveButton("Salvar") { _, _ ->
                val newSelection = widgetNames.filterIndexed { index, _ -> checked[index] }
                preferences.edit().putString("widgets", newSelection.joinToString(",")).apply()
                render()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun statusLine(container: LinearLayout, name: String, status: String) {
        val line = row()
        line.addView(text(name, 14f, colors.text), LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(text(status, 11f, colors.accent, true))
        container.addView(line)
        gap(container, 15)
    }

    private fun renderAc() {
        bodyTitle("GERENCIAMENTO DO A/C", "Ar-condicionado", "Solicitação remota com intertravamentos no ESP32.")
        val destination = screen ?: return
        val summary = panel()
        summary.addView(text("ESTADO DO COMPRESSOR", 11f, colors.subtle, true))
        gap(summary, 10)
        summary.addView(text(yes("compressor"), 27f, colors.accent, true))
        gap(summary, 8)
        summary.addView(text(live("reason", "Aguardando telemetria do módulo"), 13f, colors.subtle))
        destination.addView(summary)
        gap(destination, 14)
        val permissions = panel()
        permissions.addView(text("PERMISSIVOS", 12f, colors.accent, true))
        gap(permissions, 15)
        statusLine(permissions, "Pressostatos", yes("pressure_ok"))
        statusLine(permissions, "Motor em funcionamento", yes("engine_on"))
        statusLine(permissions, "Ventoinha", yes("fan_on"))
        statusLine(permissions, "Liberação geral", yes("ac_allowed"))
        statusLine(permissions, "Pedido de A/C", yes("ac_requested"))
        destination.addView(permissions)
        gap(destination, 14)
        val commands = panel()
        commands.addView(text("COMANDOS", 12f, colors.accent, true))
        gap(commands, 10)
        commands.addView(text("O telefone envia somente uma solicitação. Não força a saída de compressor.", 13f, colors.subtle))
        gap(commands, 14)
        commands.addView(action("SOLICITAR A/C LIGADO") { sendAcRequest(true) })
        gap(commands, 9)
        commands.addView(action("SOLICITAR A/C DESLIGADO", true) { sendAcRequest(false) })
        destination.addView(commands)
        gap(destination, 12)
        destination.addView(text("Segurança: o firmware deve impedir energização sem pressostatos e proteções automotivas validados. Primeiro teste em bancada.", 12f, colors.subtle))
    }

    private fun sendAcRequest(enabled: Boolean) {
        if (demo) {
            notice("Modo DEMO", "Dados simulados: nenhum comando foi transmitido.")
            return
        }
        if (socket?.isConnected != true) {
            notice("Sem conexão", "Conecte o ESP32 na aba Bluetooth antes de enviar uma solicitação.")
            return
        }
        val msg = JSONObject().put("type", "cmd").put("name", "ac_request").put("value", enabled).toString()
        AlertDialog.Builder(this).setTitle("Enviar solicitação?")
            .setMessage("Enviar pedido de A/C ${if (enabled) "LIGADO" else "DESLIGADO"} ao ESP32? O módulo deverá confirmar seus próprios permissivos. Não é confirmação de acionamento.")
            .setPositiveButton("Enviar") { _, _ -> send(msg) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun ensurePermission(vararg permissions: String): Boolean {
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return true
        requestPermissions(missing.toTypedArray(), 501)
        return false
    }

    private fun canConnect(): Boolean = if (Build.VERSION.SDK_INT >= 31)
        ensurePermission(Manifest.permission.BLUETOOTH_CONNECT) else true
    private fun canScan(): Boolean = if (Build.VERSION.SDK_INT >= 31)
        ensurePermission(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else ensurePermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun paired(): List<BluetoothDevice> = try {
        if (!canConnect()) emptyList() else adapter?.bondedDevices?.toList()?.sortedBy { it.name ?: it.address } ?: emptyList()
    } catch (exception: SecurityException) {
        writeLog("Permissão Bluetooth insuficiente: ${exception.message}")
        emptyList()
    }

    private fun renderBluetooth() {
        bodyTitle("CONECTIVIDADE", "Bluetooth & ESP32", "Bluetooth Classic SPP • recomendação: STILO_OVERBOOST")
        val destination = screen ?: return
        val link = panel()
        link.addView(text("ESTADO DA CONEXÃO", 11f, colors.subtle, true))
        gap(link, 9)
        link.addView(text(if (connectedTo.isEmpty()) "DESCONECTADO" else connectedTo, 19f, colors.accent, true))
        gap(link, 11)
        link.addView(action(if (connectedTo.isEmpty()) "CONECTAR A DISPOSITIVO" else "DESCONECTAR") {
            if (connectedTo.isEmpty()) chooseDevice() else disconnect()
        })
        gap(link, 9)
        link.addView(action("PEDIR TELEMETRIA", true) {
            send(JSONObject().put("type", "cmd").put("name", "telemetry_request").toString())
        })
        destination.addView(link)
        gap(destination, 14)
        val found = panel()
        found.addView(text("DISPOSITIVOS DISPONÍVEIS", 12f, colors.accent, true))
        gap(found, 10)
        found.addView(action(if (scanning) "BUSCANDO DISPOSITIVOS..." else "BUSCAR POR BLUETOOTH", true) { scanDevices() })
        gap(found, 12)
        val devices = linkedMapOf<String, BluetoothDevice>()
        if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            paired().forEach { devices[it.address] = it }
        }
        discovered.forEach { (address, device) -> devices[address] = device }
        if (devices.isEmpty()) found.addView(text("Nenhum dispositivo listado. Pareie o ESP32 nas configurações do Android ou faça uma busca.", 13f, colors.subtle))
        devices.values.forEach { device ->
            val label = try { device.name ?: "ESP32 / Dispositivo" } catch (_: SecurityException) { "Dispositivo" }
            val item = text("$label\n${device.address}", 13f, colors.text).apply {
                setPadding(dp(8), dp(11), dp(8), dp(11))
                setOnClickListener { connect(device) }
            }
            found.addView(item)
        }
        gap(found, 10)
        found.addView(action("CONFIGURAÇÕES DO ANDROID", true) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        })
        destination.addView(found)
        gap(destination, 14)
        val diagnostic = panel()
        diagnostic.addView(text("ÚLTIMOS EVENTOS", 12f, colors.accent, true))
        gap(diagnostic, 9)
        diagnostic.addView(text(logs.takeLast(8).reversed().joinToString("\n\n"), 12f, colors.subtle))
        destination.addView(diagnostic)
    }

    private fun chooseDevice() {
        if (!canConnect()) return
        val devices = linkedMapOf<String, BluetoothDevice>()
        paired().forEach { devices[it.address] = it }
        discovered.forEach { (key, value) -> devices[key] = value }
        if (devices.isEmpty()) {
            notice("Nenhum dispositivo encontrado", "Primeiro pareie o ESP32 no Bluetooth do Android ou use Buscar por Bluetooth.")
            return
        }
        val values = devices.values.toList()
        AlertDialog.Builder(this).setTitle("Conectar via SPP")
            .setItems(values.map { (it.name ?: "Sem nome") + " • " + it.address }.toTypedArray()) { _, index -> connect(values[index]) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun scanDevices() {
        if (!canScan()) return
        val bt = adapter ?: run { notice("Bluetooth indisponível", "Este telefone não possui adaptador Bluetooth disponível."); return }
        if (!bt.isEnabled) {
            notice("Bluetooth desligado", "Ative o Bluetooth nas configurações do Android.")
            return
        }
        try {
            discovered.clear()
            bt.cancelDiscovery()
            scanning = bt.startDiscovery()
            writeLog(if (scanning) "Busca Bluetooth iniciada" else "Falha ao iniciar busca")
            render()
        } catch (e: Exception) { notice("Erro de busca", e.message ?: "Bluetooth indisponível") }
    }

    private fun connect(device: BluetoothDevice) {
        if (!canConnect()) return
        if (adapter?.isEnabled != true) { notice("Bluetooth desligado", "Ative o Bluetooth."); return }
        disconnect(redraw = false)
        writeLog("Conectando a ${device.address}...")
        render()
        Thread {
            var incoming: BluetoothSocket? = null
            try {
                try { adapter?.cancelDiscovery() } catch (_: SecurityException) { /* sem permissão de busca */ }
                incoming = device.createRfcommSocketToServiceRecord(sppUuid)
                incoming.connect()
                socket = incoming
                connectedTo = device.name ?: device.address
                ui.post { writeLog("Conectado a $connectedTo"); render() }
                val reader = incoming.inputStream.bufferedReader(Charsets.UTF_8)
                reader.forEachLine { line -> ui.post { consume(line) } }
            } catch (error: Exception) {
                ui.post { writeLog("Bluetooth: ${error.message ?: "conexão encerrada"}") }
            } finally {
                try { incoming?.close() } catch (_: Exception) { }
                if (socket === incoming) {
                    socket = null
                    connectedTo = ""
                    ui.post { writeLog("Conexão encerrada"); render() }
                }
            }
        }.start()
    }

    private fun disconnect(redraw: Boolean = true) {
        val old = socket
        socket = null
        connectedTo = ""
        try { old?.close() } catch (_: Exception) { }
        if (redraw) { writeLog("Desconectado"); render() }
    }

    private fun send(json: String) {
        val connection = socket
        if (connection?.isConnected != true) {
            notice("Sem conexão", "Conecte-se ao ESP32 para enviar comandos.")
            return
        }
        Thread {
            try {
                synchronized(connection) {
                    connection.outputStream.write((json + "\n").toByteArray(Charsets.UTF_8))
                    connection.outputStream.flush()
                }
                ui.post { writeLog("Pedido enviado: $json"); render() }
            } catch (e: Exception) {
                ui.post { writeLog("Erro ao enviar: ${e.message}"); render() }
            }
        }.start()
    }

    private fun consume(line: String) {
        if (line.isBlank()) return
        try {
            val json = JSONObject(line)
            if (json.optString("type") == "can" || json.has("can")) {
                val frame = json.optJSONObject("can") ?: json
                val id = frame.optString("id", "?")
                val data = frame.optString("data", "")
                canFrames.add("$id  |  $data")
                while (canFrames.size > 80) canFrames.removeAt(0)
            }
            json.keys().forEach { key ->
                if (key !in listOf("ac", "can", "type")) telemetry[key] = json.opt(key)?.toString() ?: "—"
            }
            json.optJSONObject("ac")?.let { ac ->
                val mapping = mapOf(
                    "requested" to "ac_requested", "allowed" to "ac_allowed",
                    "compressor" to "compressor", "pressure_ok" to "pressure_ok",
                    "engine_on" to "engine_on", "fan_on" to "fan_on", "reason" to "reason"
                )
                mapping.forEach { (field, target) -> if (ac.has(field)) telemetry[target] = ac.opt(field)?.toString() ?: "—" }
            }
            if (json.optString("type") == "log") writeLog(json.optString("message", line))
        } catch (_: Exception) {
            if (line.startsWith("CAN;")) {
                canFrames.add(line.removePrefix("CAN;"))
                while (canFrames.size > 80) canFrames.removeAt(0)
            } else writeLog("Recebido: ${line.take(120)}")
        }
        if (SystemClock.elapsedRealtime() - lastRefresh > 350) {
            lastRefresh = SystemClock.elapsedRealtime()
            render()
        }
    }

    private fun renderCan() {
        bodyTitle("CAN EXPLORER", "Observação da rede", "Os frames aparecem após envio pelo ESP32. Esta tela não transmite CAN.")
        val destination = screen ?: return
        val board = panel()
        board.addView(text("FRAMES RECEBIDOS  •  ${canFrames.size}", 12f, colors.accent, true))
        gap(board, 10)
        if (canFrames.isEmpty()) board.addView(text("Sem dados CAN até o momento.\nExemplo: 0x280  |  01 02 03 04 05 06 07 08", 13f, colors.subtle))
        else board.addView(text(canFrames.takeLast(25).reversed().joinToString("\n\n"), 13f, colors.text))
        gap(board, 14)
        board.addView(action("LIMPAR LISTA", true) { canFrames.clear(); render() })
        destination.addView(board)
        gap(destination, 14)
        destination.addView(text("Para leitura real é necessário transceiver CAN e firmware de captura no ESP32. Nenhum ID CAN do Stilo foi presumido como confirmado.", 12f, colors.subtle))
    }

    private fun renderSettings() {
        bodyTitle("PREFERÊNCIAS", "Personalize seu app", "As escolhas ficam salvas no Samsung A22.")
        val destination = screen ?: return
        val theme = panel()
        theme.addView(text("APARÊNCIA", 12f, colors.accent, true))
        gap(theme, 9)
        theme.addView(text("Tema atual: $selectedTheme", 14f, colors.text))
        gap(theme, 12)
        theme.addView(action("TROCAR TEMA") {
            val themes = arrayOf("CLARO", "ESCURO", "AZUL RACING")
            AlertDialog.Builder(this).setTitle("Escolher tema")
                .setSingleChoiceItems(themes, themes.indexOf(selectedTheme)) { dialog, position ->
                    selectedTheme = themes[position]
                    preferences.edit().putString("theme", selectedTheme).apply()
                    dialog.dismiss()
                    render()
                }.setNegativeButton("Cancelar", null).show()
        })
        destination.addView(theme)
        gap(destination, 14)
        val test = panel()
        test.addView(text("MODO DEMONSTRAÇÃO", 12f, colors.accent, true))
        gap(test, 9)
        test.addView(text(if (demo) "ATIVO — valores simulados" else "INATIVO — aguardando valores reais", 14f, colors.text))
        gap(test, 11)
        test.addView(action(if (demo) "DESLIGAR DEMONSTRAÇÃO" else "ATIVAR DEMONSTRAÇÃO", true) {
            demo = !demo
            preferences.edit().putBoolean("demo", demo).apply()
            render()
        })
        destination.addView(test)
        gap(destination, 14)
        val information = panel()
        information.addView(text("SOBRE O PROJETO", 12f, colors.accent, true))
        gap(information, 10)
        information.addView(text("STILO OVERBOOST • V0.1.0\nAndroid nativo Kotlin\nBluetooth Classic SPP\n\nComandos e telemetria exigem firmware ESP32 com o protocolo compatível. OTA e escrita CAN não são implementados nesta versão.", 13f, colors.subtle))
        destination.addView(information)
    }

    private fun writeLog(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(Date())
        logs.add("$stamp  $message")
        while (logs.size > 80) logs.removeAt(0)
    }

    private fun notice(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 501) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED })
                notice("Permissão necessária", "Autorize Dispositivos próximos nas permissões do aplicativo e tente novamente.")
            render()
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        try { adapter?.cancelDiscovery() } catch (_: Exception) { }
        disconnect(redraw = false)
        super.onDestroy()
    }
}
