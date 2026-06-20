package com.example.nfcemulator

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private lateinit var statusTextView: TextView
    private lateinit var savedDataTextView: TextView
    private var isScanningMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Создаем простой интерфейс программно
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50) // ИСПРАВЛЕНО: Правильный вызов отступов
            gravity = android.view.Gravity.CENTER
        }

        statusTextView = TextView(this).apply {
            text = "Режим: Эмуляция включена"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        
        val scanButton = Button(this).apply {
            text = "Сканировать карту"
            setOnClickListener {
                isScanningMode = true
                statusTextView.text = "Приложите карту для чтения..."
                Toast.makeText(context, "Режим сканирования активирован", Toast.LENGTH_SHORT).show()
            }
        }

        savedDataTextView = TextView(this).apply {
            val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
            val savedText = sharedPrefs.getString("payload", "Hello Reader")
            text = "Текущие данные эмуляции:\n$savedText"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 50, 0, 0)
        }

        layout.addView(statusTextView)
        layout.addView(scanButton)
        layout.addView(savedDataTextView)
        setContentView(layout)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isScanningMode && NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val uidBytes = tag.id
                val uidHex = uidBytes.joinToString("") { String.format("%02X", it) }
                val dataToSave = "Card_UID_$uidHex"

                val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("payload", dataToSave).apply()

                savedDataTextView.text = "Текущие данные эмуляции:\n$dataToSave"
                statusTextView.text = "Карта успешно сохранена!"
                isScanningMode = false
                
                Toast.makeText(this, "Данные карты сохранены для эмуляции!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
