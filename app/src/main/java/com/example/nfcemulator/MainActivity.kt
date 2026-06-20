package com.example.nfcemulator

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    
    private lateinit var statusTextView: TextView
    private lateinit var emuSwitch: MaterialSwitch
    private lateinit var cardsSpinner: Spinner
    
    private var isScanningMode = false
    private val cardList = ArrayList<String>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        // Устанавливаем тему Material 3 Expressive (светлая без стандартного ActionBar)
        setTheme(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar)
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 80, 64, 64) // Выразительные отступы MD3 Expressive
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            backgroundColor = 0xFFF7F9FF.toInt() // Мягкий фон
        }

        // Заголовок приложения
        val titleView = TextView(this).apply {
            text = "NFC Emulator"
            textSize = 28f
            fontWeight = android.graphics.Typeface.BOLD
            setTextColor(0xFF1A1C1E.toInt())
            setPadding(0, 0, 0, 48)
        }
        rootLayout.addView(titleView)

        // Блок управления эмуляцией (Главная карточка MD3)
        val mainCard = MaterialCardView(this).apply {
            radius = 64f // Крупное закругление углов в стиле Expressive
            setCardBackgroundColor(0xFFDFE2EB.toInt())
            strokeWidth = 0
            setPadding(48, 48, 48, 48)
            val cardLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            
            statusTextView = TextView(context).apply {
                text = "Эмуляция отключена"
                textSize = 18f
                setTextColor(0xFF43474E.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 32)
            }
            cardLayout.addView(statusTextView)

            // Переключатель включения/выключения эмуляции в стиле MD3
            emuSwitch = MaterialSwitch(context).apply {
                text = "Активность эмулятора"
                textSize = 16f
                isChecked = false
                setOnCheckedChangeListener { _, isChecked ->
                    val sharedPrefs = context.getSharedPreferences("NfcData", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putBoolean("emu_active", isChecked).apply()
                    updateStatus()
                }
            }
            cardLayout.addView(emuSwitch)
            addView(cardLayout)
        }
        rootLayout.addView(mainCard)

        // Кнопка сканирования новой карты
        val scanButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = "Сканировать новую карту"
            textSize = 16f
            cornerRadius = 40 // Закругленные углы MD3 Expressive
            setPadding(32, 32, 32, 32)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 48, 0, 32)
            layoutParams = params
            setOnClickListener {
                isScanningMode = true
                statusTextView.text = "Приложите карту для чтения..."
                Toast.makeText(context, "Поднесите карту к задней панели", Toast.LENGTH_SHORT).show()
            }
        }
        rootLayout.addView(scanButton)

        // Секция выбора карт
        val menuTitle = TextView(this).apply {
            text = "Выберите карту для эмуляции:"
            textSize = 14f
            setTextColor(0xFF43474E.toInt())
            setPadding(0, 24, 0, 16)
        }
        rootLayout.addView(menuTitle)

        // Меню выбора сохранённых карт (Spinner)
        cardsSpinner = Spinner(this).apply {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = params
            setPadding(24, 24, 24, 24)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val selected = cardList[position]
                    val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putString("selected_card", selected).apply()
                    Toast.makeText(context, "Выбрано: $selected", Toast.LENGTH_SHORT).show()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        rootLayout.addView(cardsSpinner)

        setContentView(rootLayout)

        // Загружаем список карт и состояние
        loadSavedCards()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    private fun loadSavedCards() {
        val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
        
        // Получаем сохраненный список карт (строка через запятую)
        val savedCardsString = sharedPrefs.getString("all_cards", "Стандартная карта") ?: "Стандартная карта"
        cardList.clear()
        cardList.addAll(savedCardsString.split(","))

        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cardList)
        spinnerAdapter.setDropDownViewResource(android.widget.SimpleCursorAdapter.IGNORE_ITEM_VIEW_TYPE)
        cardsSpinner.adapter = spinnerAdapter

        // Восстанавливаем выбранную карту
        val selectedCard = sharedPrefs.getString("selected_card", "Стандартная карта")
        val index = cardList.indexOf(selectedCard)
        if (index >= 0) cardsSpinner.setSelection(index)

        // Восстанавливаем переключатель
        emuSwitch.isChecked = sharedPrefs.getBoolean("emu_active", false)
        updateStatus()
    }

    private fun updateStatus() {
        if (emuSwitch.isChecked) {
            statusTextView.text = "Режим: Эмуляция ЗАПУЩЕНА"
            statusTextView.setTextColor(0xFF106D14.toInt()) // Зелёный акцент
        } else {
            statusTextView.text = "Режим: Эмуляция ОТКЛЮЧЕНА"
            statusTextView.setTextColor(0xFFBA1A1A.toInt()) // Красный акцент
        }
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
                val uidHex = tag.id.joinToString("") { String.format("%02X", it) }
                val newCardName = "Карта $uidHex"

                val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
                
                // Добавляем новую карту в общий список
                var savedCards = sharedPrefs.getString("all_cards", "Стандартная карта") ?: "Стандартная карта"
                if (!savedCards.contains(newCardName)) {
                    savedCards += ",$newCardName"
                    sharedPrefs.edit().putString("all_cards", savedCards).apply()
                }

                // Автоматически выбираем её
                sharedPrefs.edit().putString("selected_card", newCardName).apply()

                isScanningMode = false
                loadSavedCards()
                
                Toast.makeText(this, "Успешно сохранено в меню!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
