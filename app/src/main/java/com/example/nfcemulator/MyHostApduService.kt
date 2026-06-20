package com.example.nfcemulator

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyHostApduService : HostApduService() {

    companion object {
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean("emu_active", false)
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (!isEnabled) {
            addLog("[$timeStamp] Терминал запросил связь -> Отказ (Эмуляция отключена)")
            return STATUS_FAILED
        }

        if (commandApdu == null) return STATUS_FAILED

        if (isSelectAidCommand(commandApdu)) {
            val activeName = sharedPrefs.getString("selected_card_name", "Стандартный пропуск") ?: "Стандартный пропуск"
            val savedCardsString = sharedPrefs.getString("cards_list_v2", "Стандартный пропуск=F0010203040506") ?: "Стандартный пропуск=F0010203040506"
            
            var activeUid = "F0010203040506"
            for (pair in savedCardsString.split(",")) {
                val parts = pair.split("=")
                if (parts.size == 2 && parts[0] == activeName) {
                    activeUid = parts[1]
                    break
                }
            }

            addLog("[$timeStamp] Считывание карты: \"$activeName\" (UID: $activeUid) -> УСПЕХ")
            
            val payload = "Card_UID_$activeUid".toByteArray(Charsets.UTF_8)
            return payload + STATUS_SUCCESS
        }

        return STATUS_SUCCESS
    }

    override fun onDeactivated(reason: Int) {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        addLog("[$timeStamp] Сессия завершена считывателем (Код: $reason)")
    }

    private fun isSelectAidCommand(commandApdu: ByteArray): Boolean {
        return commandApdu.size >= 4 &&
                commandApdu[0] == 0x00.toByte() && 
                commandApdu[1] == 0xA4.toByte() && 
                commandApdu[2] == 0x04.toByte() && 
                commandApdu[3] == 0x00.toByte()
    }

    private fun addLog(message: String) {
        val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
        val currentLogs = sharedPrefs.getString("terminal_logs", "") ?: ""
        val logLines = currentLogs.split("\n").takeLast(5).toMutableList()
        logLines.add(message)
        sharedPrefs.edit().putString("terminal_logs", logLines.joinToString("\n")).apply()
    }
}
