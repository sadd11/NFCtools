package com.example.nfcemulator

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
importimport android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    
    private lateinit var statusTextView: TextView
    private lateinit var cardNameTextView: TextView
    private lateinit var cardUidTextView: TextView
    private lateinit var emuSwitch: MaterialSwitch
    private lateinit var cardsSpinner: Spinner
    private lateinit var logTextView: TextView
    private lateinit var visualCard: MaterialCardView
    
    private var isScanningMode = false
    private val cardNames = ArrayList<String>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тема MD3, которая сама поддерживает тёмный и светлый режимы Android
        setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        super.onCreate(savedInstanceState)

        // Главный контейнер со скроллом, чтобы лог транзакций не уходил за экран
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 64, 56, 56)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        scrollView.addView(rootLayout)

        // Красивый заголовок
        val titleView = TextView(this).apply {
            text = "NFC Nexus"
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }
        rootLayout.addView(titleView)

        // --- 1. ВИЗУАЛЬНАЯ СМАРТ-КАРТА (MD3 Expressive) ---
        visualCard = MaterialCardView(this).apply {
            radius = 48f
            strokeWidth = 0
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 480)
            params.setMargins(0, 0, 0, 40)
            layoutParams = params
            
            // Градиентный фон для карты
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF3F51B5.toInt(), 0xFF00363A.toInt())
            )
            gradient.cornerRadius = 48f
            background = gradient

            val cardContent = RelativeLayout(context).apply {
                setPadding(48, 48, 48, 48)
            }

            // Иконка импровизированного чипа
            val chipIcon = View(context).apply {
                id = View.generateViewId()
                val chipParams = RelativeLayout.LayoutParams(90, 70)
                chipParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                chipParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                layoutParams = chipParams
                val chipBg = GradientDrawable()
                chipBg.setColor(0xFFFFD700.toInt()) // Золотой чип
                chipBg.cornerRadius = 12f
                background = chipBg
            }
            cardContent.addView(chipIcon)

            // Текст статуса прямо на карте
            statusTextView = TextView(context).apply {
                id = View.generateViewId()
                text = "STATUS"
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                val statusParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
                statusParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                statusParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                layoutParams = statusParams
            }
            cardContent.addView(statusTextView)

            // Название выбранной карты
            cardNameTextView = TextView(context).apply {
                id = View.generateViewId()
                text = "Имя карты"
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                val nameParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
                nameParams.addRule(RelativeLayout.BELOW, chipIcon.id)
                nameParams.setMargins(0, 40, 0, 0)
                layoutParams = nameParams
            }
            cardContent.addView(cardNameTextView)

            // UID карты внизу
            cardUidTextView = TextView(context).apply {
                text = "UID: ----"
                textSize = 14f
                setTextColor(0xB3FFFFFF.toInt()) // Белый с прозрачностью
                val uidParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
                uidParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                uidParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                layoutParams = uidParams
            }
            cardContent.addView(cardUidTextView)

            addView(cardContent)
        }
        rootLayout.addView(visualCard)

        // --- 2. ТУМБЛЕР АКТИВАЦИИ ---
        emuSwitch = MaterialSwitch(this).apply {
            text = "Режим эмуляции"
            textSize = 18f
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 32)
            layoutParams = params
            setOnCheckedChangeListener { _, isChecked ->
                val sharedPrefs = context.getSharedPreferences("NfcData", Context.MODE_PRIVATE)
                sharedPrefs.edit().putBoolean("emu_active", isChecked).apply()
                updateUIState()
            }
        }
        rootLayout.addView(emuSwitch)

        // --- 3. ВЫБОР И УДАЛЕНИЕ КАРТ ---
        val spinnerLabel = TextView(this).apply {
            text = "Активный профиль:"
            textSize = 14f
            setPadding(0, 16, 0, 8)
        }
        rootLayout.addView(spinnerLabel)

        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 32)
            layoutParams = params
        }

        cardsSpinner = Spinner(this).apply {
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
            layoutParams = params
            setPadding(16, 16, 16, 16)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (cardNames.isNotEmpty()) {
                        val selectedName = cardNames[position]
                        val sharedPrefs = context.getSharedPreferences("NfcData", Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString("selected_card_name", selectedName).apply()
                        updateUIState()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        rowLayout.addView(cardsSpinner)

        // Кнопка удаления карты в стиле MD3 Icon
        val deleteButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Удалить"
            textSize = 12f
            cornerRadius = 24
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)
            layoutParams = params
            setOnClickListener {
                deleteCurrentCard()
            }
        }
        rowLayout.addView(deleteButton)
        rootLayout.addView(rowLayout)

        // --- 4. КНОПКА СКАНИРОВАНИЯ ---
        val scanButton = MaterialButton(this).apply {
            text = "Записать новую карту"
            textSize = 16f
            cornerRadius = 32
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                isScanningMode = true
                statusTextView.text = "СКАНИРОВАНИЕ..."
                Toast.makeText(context, "Поднесите оригинал к телефону", Toast.LENGTH_SHORT).show()
            }
        }
        rootLayout.addView(scanButton)

        // --- 5. ЛОГ ТРАНЗАКЦИЙ ---
        val logLabel = TextView(this).apply {
            text = "Лог событий терминала:"
            textSize = 14f
            setPadding(0, 40, 0, 12)
        }
        rootLayout.addView(logLabel)

        logTextView = TextView(this).apply {
            text = "Служба запущена. Ожидание событий...\n"
            textSize = 13f
            setBackgroundColor(0x1A000000)
            setPadding(24, 24, 24, 24)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 300)
        }
        rootLayout.addView(logTextView)

        setContentView(scrollView)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        loadCardsFromStorage()
    }

    private fun loadCardsFromStorage() {
        val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
        val savedCardsString = sharedPrefs.getString("cards_list_v2", "Стандартный пропуск=F0010203040506") ?: "Стандартный пропуск=F0010203040506"
        
        cardNames.clear()
        val pairs = savedCardsString.split(",")
        for (pair in pairs) {
            val parts = pair.split("=")
            if (parts.size == 2) {
                cardNames.add(parts[0])
            }
        }

        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cardNames)
        cardsSpinner.adapter = spinnerAdapter

        val activeName = sharedPrefs.getString("selected_card_name", "Стандартный пропуск")
        val index = cardNames.indexOf(activeName)
        if (index >= 0) cardsSpinner.setSelection(index)

        emuSwitch.isChecked = sharedPrefs.getBoolean("emu_active", false)
        updateUIState()
    }

    private fun updateUIState() {
        val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
        val activeName = sharedPrefs.getString("selected_card_name", "Пусто") ?: "Пусто"
        
        // Достаем UID для отображения на карте
        val savedCardsString = sharedPrefs.getString("cards_list_v2", "Стандартный пропуск=F0010203040506") ?: "Стандартный пропуск=F0010203040506"
        var activeUid = "----"
        for (pair in savedCardsString.split(",")) {
            val parts = pair.split("=")
            if (parts.size == 2 && parts[0] == activeName) {
                activeUid = parts[1]
                break
            }
        }

        cardNameTextView.text = activeName
        cardUidTextView.text = "UID: $activeUid"

        val gradient = GradientDrawable()
        gradient.cornerRadius = 48f

        if (emuSwitch.isChecked) {
            statusTextView.text = "АКТИВЕН"
            // Яркий зеленый/бирюзовый градиент в режиме работы
            gradient.setOrientation(GradientDrawable.Orientation.TL_BR)
            gradient.setColors(intArrayOf(0xFF00BFA5.toInt(), 0xFF00675B.toInt()))
        } else {
            statusTextView.text = "ВЫКЛЮЧЕН"
            // Сдержанный серый/синий градиент в простое
            gradient.setOrientation(GradientDrawable.Orientation.TL_BR)
            gradient.setColors(intArrayOf(0xFF78909C.toInt(), 0xFF37474F.toInt()))
        }
        visualCard.background = gradient
        
        // Обновляем лог из истории
        val history = sharedPrefs.getString("terminal_logs", "Ожидание...")
        logTextView.text = history
    }

    private fun deleteCurrentCard() {
        if (cardNames.size <= 1) {
            Toast.makeText(this, "Нельзя удалить единственную карту!", Toast.LENGTH_SHORT).show()
            return
        }
        val currentSelected = cardsSpinner.selectedItem.toString()
        val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
        val savedCardsString = sharedPrefs.getString("cards_list_v2", "") ?: ""
        
        val newList = savedCardsString.split(",").filter { !it.startsWith("$currentSelected=") }
        sharedPrefs.edit().putString("cards_list_v2", newList.joinToString(",")).apply()
        
        // Выбираем первую оставшуюся карту
        val firstRemaining = newList[0].split("=")[0]
        sharedPrefs.edit().putString("selected_card_name", firstRemaining).apply()

        Toast.makeText(this, "Карта удалена", Toast.LENGTH_SHORT).show()
        loadCardsFromStorage()
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        updateUIState() // Чтобы обновить логи при возврате на экран
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
                isScanningMode = false
                showRenameDialog(uidHex)
            }
        }
    }

    // --- МЕНЮ ПЕРЕИМЕНОВАНИЯ ПРИ СКАНИРОВАНИИ ---
    private fun showRenameDialog(uidHex: String) {
        val inputLayout = TextInputLayout(this)
        val input = TextInputEditText(this).apply {
            hint = "Например: Проездной метро"
        }
        inputLayout.addView(input)
        inputLayout.setPadding(32, 16, 32, 16)

        MaterialAlertDialogBuilder(this)
            .setTitle("Карта обнаружена!")
            .setMessage("Введите пользовательское имя для UID: $uidHex")
            .setView(inputLayout)
            .setCancelable(false)
            .setPositiveButton("Сохранить") { _, _ ->
                var customName = input.text.toString().trim()
                if (customName.isEmpty()) customName = "Карта $uidHex"

                val sharedPrefs = getSharedPreferences("NfcData", Context.MODE_PRIVATE)
                var savedCards = sharedPrefs.getString("cards_list_v2", "Стандартный пропуск=F0010203040506") ?: "Стандартный пропуск=F0010203040506"
                
                savedCards += ",$customName=$uidHex"
                sharedPrefs.edit().putString("cards_list_v2", savedCards).apply()
                sharedPrefs.edit().putString("selected_card_name", customName).apply()

                loadCardsFromStorage()
                Toast.makeText(this, "Сохранено успешно!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
