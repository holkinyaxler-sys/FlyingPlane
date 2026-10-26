package com.example.flyingplane

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.SeekBar

class MainActivity : Activity() {
    private lateinit var prefs: android.content.SharedPreferences
    private var difficulty = 1
    private var selectedSkin = 0
    private var unlockedSkins = mutableSetOf(0)
    private var screen = "menu"
    private var gameView: GameView? = null
    private var savedGameState: Bundle? = null
    private var runCoins = 0
    private var runScore = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("flying_plane", MODE_PRIVATE)
        difficulty = prefs.getInt("difficulty", 1)
        selectedSkin = prefs.getInt("selected_skin", 0)
        unlockedSkins = prefs.getStringSet("unlocked_skins", setOf("0"))!!
            .mapNotNull { it.toIntOrNull() }.toMutableSet()
        screen = savedInstanceState?.getString("screen") ?: "menu"
        savedGameState = savedInstanceState?.getBundle("game_state")
        if (screen == "game") showGame() else showMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen)
        gameView?.let { outState.putBundle("game_state", it.exportState()) }
        super.onSaveInstanceState(outState)
    }

    private fun baseLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(24), dp(18))
            setBackgroundColor(Color.rgb(202, 239, 255))
        }
    }

    private fun label(text: String, size: Float): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.rgb(25, 68, 103))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

    private fun button(text: String, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 16f
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun addButton(layout: LinearLayout, text: String, action: () -> Unit) {
        layout.addView(button(text, action), LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(7)
        })
    }

    private fun showMenu() {
        screen = "menu"
        gameView = null
        val layout = baseLayout()
        layout.addView(label("✈ БУМАЖНЫЙ\nСАМОЛЁТИК", 30f))
        layout.addView(label("Рекорд: ${prefs.getInt("best_score", 0)} очков   •   Монеты: ${prefs.getInt("bank", 0)}", 16f))
        addButton(layout, "НАЧАТЬ ИГРУ") { showGame() }
        addButton(layout, "Сложность: ${difficultyName() }  (нажми для смены)") {
            difficulty = (difficulty + 1) % 3
            prefs.edit().putInt("difficulty", difficulty).apply()
            showMenu()
        }
        addButton(layout, "МАГАЗИН СКИНОВ") { showShop() }
        addButton(layout, "КАК ИГРАТЬ") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Как играть")
                .setMessage("Проведи пальцем по экрану, чтобы нарисовать путь самолётика. Отпусти палец — самолётик полетит по линии. Собирай золотые монеты и избегай облаков и птиц. Столкновение завершает игру.")
                .setPositiveButton("Понятно", null).show()
        }
        setContentView(layout)
    }

    private fun difficultyName() = when (difficulty) {
        0 -> "Лёгкая"
        1 -> "Средняя"
        else -> "Сложная"
    }

    private fun showShop() {
        screen = "shop"
        val layout = baseLayout()
        layout.addView(label("МАГАЗИН САМОЛЁТИКОВ", 25f))
        layout.addView(label("Баланс: ${prefs.getInt("bank", 0)} монет", 18f))
        val names = listOf("Белый бумажный — бесплатно", "Синий — 50 монет", "Золотой — 300 монет", "Розовый — 75 монет")
        for (i in names.indices) {
            val owned = unlockedSkins.contains(i)
            val text = (if (selectedSkin == i) "✓ " else "") + names[i] +
                if (owned) "  • выбрать" else "  • купить"
            addButton(layout, text) {
                if (owned) {
                    selectedSkin = i
                    prefs.edit().putInt("selected_skin", i).apply()
                    showShop()
                } else {
                    val cost = listOf(0, 50, 300, 75)[i]
                    val bank = prefs.getInt("bank", 0)
                    if (bank >= cost) {
                        prefs.edit().putInt("bank", bank - cost).apply()
                        unlockedSkins.add(i)
                        prefs.edit().putStringSet("unlocked_skins", unlockedSkins.map { it.toString() }.toSet()).apply()
                        selectedSkin = i
                        prefs.edit().putInt("selected_skin", i).apply()
                        showShop()
                    } else {
                        android.widget.Toast.makeText(this, "Недостаточно монет. Собери их в игре!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        addButton(layout, "НАЗАД В МЕНЮ") { showMenu() }
        setContentView(layout)
    }

    private fun showGame() {
        screen = "game"
        val root = FrameLayout(this)
        val gv = GameView(this, difficulty, selectedSkin, prefs, savedGameState)
        gameView = gv
        gv.onGameOver = { coins, score ->
            runCoins = coins
            runScore = score
            val bank = prefs.getInt("bank", 0) + coins
            val best = maxOf(prefs.getInt("best_score", 0), score)
            prefs.edit().putInt("bank", bank).putInt("best_score", best).apply()
            showResult(coins, score, best)
        }
        root.addView(gv, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        savedGameState = null
    }

    private fun showResult(coins: Int, score: Int, best: Int) {
        screen = "result"
        gameView = null
        val layout = baseLayout()
        layout.addView(label("✈ ПОЛЁТ ЗАВЕРШЁН", 28f))
        layout.addView(label("Счёт: $score", 25f))
        layout.addView(label("Собрано монет: $coins", 20f))
        layout.addView(label("Рекорд: $best", 18f))
        addButton(layout, "ИГРАТЬ ЕЩЁ") { showGame() }
        addButton(layout, "В МЕНЮ") { showMenu() }
        addButton(layout, "ОТКРЫТЬ МАГАЗИН") { showShop() }
        setContentView(layout)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
