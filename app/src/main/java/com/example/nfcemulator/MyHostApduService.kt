package com.example.nfcemulator

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class MyHostApduService : HostApduService() {

    companion object {
        private const val TAG = "NFCEmulator"
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            return STATUS_FAILED
        }

        val hexCommand = commandApdu.joinToString("") { String.format("%02X", it) }
        Log.d(TAG, "Получена команда APDU: $hexCommand")

        return if (isSelectAidCommand(commandApdu)) {
            Log.d(TAG, "Терминал выбрал наш AID. Отправляем сохраненные данные карты...")
            
            // Читаем сохраненные данные из памяти устройства
            val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
            val savedPayload = sharedPrefs.getString("payload", "Hello Reader") ?: "Hello Reader"
            
            val payload = savedPayload.toByteArray(Charsets.UTF_8)
            payload + STATUS_SUCCESS
        } else {
            STATUS_SUCCESS
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Связь потеряна. Причина: $reason")
    }

    private fun isSelectAidCommand(commandApdu: ByteArray): Boolean {
        return commandApdu.size >= 4 &&
                commandApdu[0] == 0x00.toByte() && 
                commandApdu[1] == 0xA4.toByte() && 
                commandApdu[2] == 0x04.toByte() && 
                commandApdu[3] == 0x00.toByte()
    }
}
