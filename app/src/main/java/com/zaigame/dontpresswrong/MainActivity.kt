package com.zaigame.dontpresswrong

import androidx.appcompat.app.AppCompatActivity
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var scoreText: TextView
    private lateinit var livesText: TextView
    private lateinit var statusText: TextView
    private lateinit var waveText: TextView

    private lateinit var startMenuLayout: ConstraintLayout
    private lateinit var settingsMenuLayout: ConstraintLayout
    private lateinit var tutorialLayout: ConstraintLayout
    private lateinit var statsLayout: ConstraintLayout
    private lateinit var gameOverLayout: ConstraintLayout
    
    // Achievement notification views
    private lateinit var achievementNotification: ConstraintLayout
    private lateinit var achievementName: TextView
    private lateinit var achievementDescription: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SoundManager.init(this)
        GameDataManager.init(this)
        findViews()
        setupMenuListeners()
        setupGameCallbacks()
        setupTutorialListeners()
        setupGameOverListeners()
        updateHighScoreDisplay()

        gameView.startAmbientMode()
    }

    private fun findViews() {
        gameView = findViewById(R.id.game_view)
        scoreText = findViewById(R.id.score_text)
        livesText = findViewById(R.id.lives_text)
        statusText = findViewById(R.id.status_text)
        waveText = findViewById(R.id.wave_text)
        startMenuLayout = findViewById(R.id.start_menu_layout)
        settingsMenuLayout = findViewById(R.id.settings_menu_layout)
        tutorialLayout = findViewById(R.id.tutorial_layout)
        statsLayout = findViewById(R.id.stats_layout)
        gameOverLayout = findViewById(R.id.game_over_layout)
        
        // Achievement notification views
        achievementNotification = findViewById(R.id.achievement_notification)
        achievementName = findViewById(R.id.achievement_name)
        achievementDescription = findViewById(R.id.achievement_description)
    }

    private fun setupMenuListeners() {
        val playButton = findViewById<Button>(R.id.play_button)
        val tutorialButton = findViewById<Button>(R.id.tutorial_button)
        val settingsButton = findViewById<Button>(R.id.settings_button)
        val statsButton = findViewById<Button>(R.id.stats_button)

        playButton.setOnClickListener {
            showGameLayout()
            gameView.startGame()
        }
        tutorialButton.setOnClickListener { showTutorialLayout() }
        settingsButton.setOnClickListener { showSettingsLayout() }
        statsButton.setOnClickListener { showStatsLayout() }

        val backButton = findViewById<Button>(R.id.back_button)
        val statsBackButton = findViewById<Button>(R.id.stats_back_button)
        val bgmSlider = findViewById<SeekBar>(R.id.bgm_volume_slider)
        val sfxSlider = findViewById<SeekBar>(R.id.sfx_volume_slider)
        backButton.setOnClickListener { showStartMenuLayout() }
        statsBackButton.setOnClickListener { showStartMenuLayout() }

        bgmSlider.progress = (SoundManager.bgmVolume * 100).toInt()
        sfxSlider.progress = (SoundManager.sfxVolume * 100).toInt()

        bgmSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                if (u) SoundManager.setBgmVolume(p / 100f)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        sfxSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                if (u) SoundManager.setSfxVolume(p / 100f)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        statusText.setOnClickListener {
            statusText.visibility = View.GONE
            
            // Check if this is a wave completion message or tutorial completion
            val statusMessage = statusText.text.toString()
            when {
                statusMessage.contains("WAVE") && statusMessage.contains("COMPLETE") -> {
                    // This is a wave completion, continue current game
                    gameView.continueCurrentGame()
                }
                statusMessage.contains("TUTORIAL COMPLETE") -> {
                    // Tutorial completed, start actual game
                    gameView.startGame()
                }
                else -> {
                    // Default case (game over, etc.) - start new game
                    gameView.startGame()
                }
            }
        }
    }

    private fun setupTutorialListeners() {
        val tutorialBackButton = findViewById<Button>(R.id.tutorial_back_button)
        val startTutorialButton = findViewById<Button>(R.id.start_tutorial_button)

        tutorialBackButton.setOnClickListener { showStartMenuLayout() }
        startTutorialButton.setOnClickListener {
            showGameLayout()
            gameView.startTutorial()
        }
    }

    private fun setupGameOverListeners() {
        val playAgainButton = findViewById<Button>(R.id.play_again_button)
        val mainMenuButton = findViewById<Button>(R.id.main_menu_button)
        val exitGameButton = findViewById<Button>(R.id.exit_game_button)

        playAgainButton.setOnClickListener {
            hideGameOverLayout()
            gameView.startGame()
        }

        mainMenuButton.setOnClickListener {
            hideGameOverLayout()
            showStartMenuLayout()
        }

        exitGameButton.setOnClickListener {
            finish() // Close the app
        }
    }

    private fun setupGameCallbacks() {
        gameView.onScoreUpdate = { score, lives, wave ->
            runOnUiThread {
                scoreText.text = "SCORE: $score"
                livesText.text = "LIVES: $lives"
                waveText.text = "WAVE: $wave"
            }
        }

        gameView.onGameOver = { finalScore, finalWave ->
            runOnUiThread {
                handleGameEnd(finalScore, finalWave)
                showGameOverLayout(finalScore, finalWave)
            }
        }

        gameView.onWaveComplete = { wave ->
            runOnUiThread {
                statusText.text = "WAVE $wave COMPLETE!\n\nTap to continue or wait..."
                statusText.visibility = View.VISIBLE
                // Auto-hide after 2 seconds (this matches the GameView delay)
                statusText.postDelayed({
                    statusText.visibility = View.GONE
                }, 2000)
            }
        }

        gameView.onTutorialComplete = {
            runOnUiThread {
                statusText.text = "TUTORIAL COMPLETE!\n\nTap to start the real challenge"
                statusText.visibility = View.VISIBLE
            }
        }
    }

    private fun showGameLayout() {
        startMenuLayout.visibility = View.GONE
        settingsMenuLayout.visibility = View.GONE
        tutorialLayout.visibility = View.GONE
        gameOverLayout.visibility = View.GONE
        scoreText.visibility = View.VISIBLE
        livesText.visibility = View.VISIBLE
        waveText.visibility = View.VISIBLE
    }

    private fun showStartMenuLayout() {
        settingsMenuLayout.visibility = View.GONE
        tutorialLayout.visibility = View.GONE
        statsLayout.visibility = View.GONE
        gameOverLayout.visibility = View.GONE
        startMenuLayout.visibility = View.VISIBLE
        scoreText.visibility = View.GONE
        livesText.visibility = View.GONE
        waveText.visibility = View.GONE
        statusText.visibility = View.GONE
    }

    private fun showSettingsLayout() {
        startMenuLayout.visibility = View.GONE
        settingsMenuLayout.visibility = View.VISIBLE
    }

    private fun showTutorialLayout() {
        startMenuLayout.visibility = View.GONE
        tutorialLayout.visibility = View.VISIBLE
    }
    
    private fun showStatsLayout() {
        startMenuLayout.visibility = View.GONE
        populateStatistics()
        statsLayout.visibility = View.VISIBLE
    }

    private fun showGameOverLayout(finalScore: Int, finalWave: Int) {
        val gameOverScoreText = findViewById<TextView>(R.id.game_over_score_text)
        val gameOverWaveText = findViewById<TextView>(R.id.game_over_wave_text)
        
        val stats = GameDataManager.getStats()
        val isNewHighScore = finalScore == stats.highScore
        val isNewBestWave = finalWave == stats.bestWave

        if (isNewHighScore) {
            gameOverScoreText.text = "🆕 NEW HIGH SCORE: $finalScore"
            gameOverScoreText.setTextColor(resources.getColor(android.R.color.holo_orange_light, null))
        } else {
            gameOverScoreText.text = "Final Score: $finalScore\nHigh Score: ${stats.highScore}"
        }
        
        if (isNewBestWave) {
            gameOverWaveText.text = "🆕 NEW RECORD: $finalWave Waves"
            gameOverWaveText.setTextColor(resources.getColor(android.R.color.holo_blue_light, null))
        } else {
            gameOverWaveText.text = "Waves Survived: $finalWave\nBest: ${stats.bestWave}"
        }

        gameOverLayout.visibility = View.VISIBLE
    }

    private fun hideGameOverLayout() {
        gameOverLayout.visibility = View.GONE
        showGameLayout()
    }

    override fun onResume() {
        super.onResume()
        SoundManager.startBgm()
    }

    override fun onPause() {
        super.onPause()
        SoundManager.pauseBgm()
    }

    private fun updateHighScoreDisplay() {
        val stats = GameDataManager.getStats()
        // Update UI elements to show high score if needed
        // This could be added to the main menu later
    }
    
    private fun handleGameEnd(finalScore: Int, finalWave: Int) {
        val oldStats = GameDataManager.getStats()
        
        // Record game statistics
        val powerUpsCollected = gameView.getPowerUpsCollected()
        val bossesDefeated = gameView.getBossesDefeated() 
        val combosAchieved = gameView.getCombosAchieved()
        
        GameDataManager.recordGameEnd(finalScore, finalWave, powerUpsCollected, bossesDefeated, combosAchieved)
        
        val newStats = GameDataManager.getStats()
        val newAchievements = GameDataManager.checkNewAchievements(oldStats, newStats)
        
        // Show achievement notifications
        if (newAchievements.isNotEmpty()) {
            showAchievementNotification(newAchievements.first())
        }
    }
    
    private fun showAchievementNotification(achievement: GameDataManager.Achievement) {
        // Play achievement sound with higher pitch
        SoundManager.playSfx(SoundManager.ID_CHIME, 1.3f)
        
        runOnUiThread {
            // Set achievement text
            achievementName.text = achievement.title
            achievementDescription.text = achievement.description
            
            // Reset position and make visible
            achievementNotification.translationX = -300f
            achievementNotification.visibility = View.VISIBLE
            
            // Create slide-in animation
            val slideIn = ObjectAnimator.ofFloat(achievementNotification, "translationX", -300f, 20f)
            slideIn.duration = 500
            
            // Create slide-out animation (after delay)
            val slideOut = ObjectAnimator.ofFloat(achievementNotification, "translationX", 20f, -300f)
            slideOut.duration = 400
            slideOut.startDelay = 3000 // Show for 3 seconds
            
            // Create animator set
            val animatorSet = AnimatorSet()
            animatorSet.playSequentially(slideIn, slideOut)
            
            // Hide after animation completes
            animatorSet.addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    achievementNotification.visibility = View.GONE
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    achievementNotification.visibility = View.GONE
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            
            animatorSet.start()
        }
    }
    
    private fun populateStatistics() {
        val statsContent = findViewById<LinearLayout>(R.id.stats_content)
        statsContent.removeAllViews()
        
        val stats = GameDataManager.getStats()
        val achievements = GameDataManager.getAchievements()
        
        // Add statistics sections
        addStatsSection(statsContent, "📊 Game Statistics", listOf(
            "High Score" to "${stats.highScore}",
            "Best Wave" to "${stats.bestWave}",
            "Games Played" to "${stats.totalGamesPlayed}",
            "Combos Achieved" to "${stats.totalTimesComboed}",
            "Power-ups Collected" to "${stats.totalPowerUpsCollected}",
            "Bosses Defeated" to "${stats.totalBossesDefeated}"
        ))
        
        addStatsSection(statsContent, "🏆 Achievements", 
            achievements.map { achievement ->
                val status = if (achievement.unlocked) "✅" else "❌"
                val progress = if (achievement.unlocked) "Complete" else "${achievement.progress}/${achievement.target}"
                "${achievement.title}" to "$status $progress"
            }
        )
    }
    
    private fun addStatsSection(parent: LinearLayout, title: String, items: List<Pair<String, String>>) {
        // Section title
        val titleView = TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(resources.getColor(android.R.color.white, null))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        parent.addView(titleView)
        
        // Section items
        items.forEach { (label, value) ->
            val itemView = TextView(this).apply {
                text = "$label: $value"
                textSize = 16f
                setTextColor(resources.getColor(android.R.color.white, null))
                setPadding(16, 8, 0, 8)
            }
            parent.addView(itemView)
        }
        
        // Spacing
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                32
            )
        }
        parent.addView(spacer)
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}