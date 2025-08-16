package com.zaigame.dontpresswrong

import android.content.Context
import android.content.SharedPreferences

object GameDataManager {
    
    private lateinit var prefs: SharedPreferences
    
    data class GameStats(
        val highScore: Int = 0,
        val bestWave: Int = 0,
        val totalGamesPlayed: Int = 0,
        val totalTimesComboed: Int = 0,
        val totalPowerUpsCollected: Int = 0,
        val totalBossesDefeated: Int = 0,
        val perfectWaves: Int = 0,
        val totalTimePlayed: Long = 0L
    )
    
    data class Achievement(
        val id: String,
        val title: String,
        val description: String,
        val unlocked: Boolean = false,
        val progress: Int = 0,
        val target: Int = 1
    )
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences("GameData", Context.MODE_PRIVATE)
    }
    
    fun getStats(): GameStats {
        return GameStats(
            highScore = prefs.getInt("high_score", 0),
            bestWave = prefs.getInt("best_wave", 0),
            totalGamesPlayed = prefs.getInt("total_games", 0),
            totalTimesComboed = prefs.getInt("total_combos", 0),
            totalPowerUpsCollected = prefs.getInt("total_powerups", 0),
            totalBossesDefeated = prefs.getInt("total_bosses", 0),
            perfectWaves = prefs.getInt("perfect_waves", 0),
            totalTimePlayed = prefs.getLong("total_time", 0L)
        )
    }
    
    fun updateHighScore(score: Int): Boolean {
        val currentHigh = prefs.getInt("high_score", 0)
        if (score > currentHigh) {
            prefs.edit().putInt("high_score", score).apply()
            return true
        }
        return false
    }
    
    fun updateBestWave(wave: Int): Boolean {
        val currentBest = prefs.getInt("best_wave", 0)
        if (wave > currentBest) {
            prefs.edit().putInt("best_wave", wave).apply()
            return true
        }
        return false
    }
    
    fun incrementStat(statName: String, amount: Int = 1) {
        val current = prefs.getInt(statName, 0)
        prefs.edit().putInt(statName, current + amount).apply()
    }
    
    fun incrementLongStat(statName: String, amount: Long = 1L) {
        val current = prefs.getLong(statName, 0L)
        prefs.edit().putLong(statName, current + amount).apply()
    }
    
    fun recordGameEnd(score: Int, wave: Int, powerUpsCollected: Int, bossesDefeated: Int, combosAchieved: Int) {
        updateHighScore(score)
        updateBestWave(wave)
        incrementStat("total_games")
        incrementStat("total_powerups", powerUpsCollected)
        incrementStat("total_bosses", bossesDefeated)
        incrementStat("total_combos", combosAchieved)
    }
    
    fun getAchievements(): List<Achievement> {
        val stats = getStats()
        return listOf(
            Achievement(
                "first_game", "First Steps", "Play your first game",
                stats.totalGamesPlayed >= 1, stats.totalGamesPlayed, 1
            ),
            Achievement(
                "score_100", "Cosmic Novice", "Reach a score of 100",
                stats.highScore >= 100, stats.highScore, 100
            ),
            Achievement(
                "score_500", "Star Weaver", "Reach a score of 500",
                stats.highScore >= 500, stats.highScore, 500
            ),
            Achievement(
                "score_1000", "Celestial Master", "Reach a score of 1000",
                stats.highScore >= 1000, stats.highScore, 1000
            ),
            Achievement(
                "wave_10", "Cosmic Survivor", "Survive 10 waves",
                stats.bestWave >= 10, stats.bestWave, 10
            ),
            Achievement(
                "wave_20", "Galactic Champion", "Survive 20 waves",
                stats.bestWave >= 20, stats.bestWave, 20
            ),
            Achievement(
                "games_10", "Persistent Weaver", "Play 10 games",
                stats.totalGamesPlayed >= 10, stats.totalGamesPlayed, 10
            ),
            Achievement(
                "combos_50", "Combo Master", "Achieve 50 combos",
                stats.totalTimesComboed >= 50, stats.totalTimesComboed, 50
            ),
            Achievement(
                "powerups_25", "Power Collector", "Collect 25 power-ups",
                stats.totalPowerUpsCollected >= 25, stats.totalPowerUpsCollected, 25
            ),
            Achievement(
                "bosses_5", "Boss Slayer", "Defeat 5 boss targets",
                stats.totalBossesDefeated >= 5, stats.totalBossesDefeated, 5
            )
        )
    }
    
    fun checkNewAchievements(oldStats: GameStats, newStats: GameStats): List<Achievement> {
        val achievements = getAchievements()
        val newlyUnlocked = mutableListOf<Achievement>()
        
        achievements.forEach { achievement ->
            if (achievement.unlocked) {
                // Check if this achievement was just unlocked
                when (achievement.id) {
                    "first_game" -> if (oldStats.totalGamesPlayed == 0 && newStats.totalGamesPlayed >= 1) newlyUnlocked.add(achievement)
                    "score_100" -> if (oldStats.highScore < 100 && newStats.highScore >= 100) newlyUnlocked.add(achievement)
                    "score_500" -> if (oldStats.highScore < 500 && newStats.highScore >= 500) newlyUnlocked.add(achievement)
                    "score_1000" -> if (oldStats.highScore < 1000 && newStats.highScore >= 1000) newlyUnlocked.add(achievement)
                    "wave_10" -> if (oldStats.bestWave < 10 && newStats.bestWave >= 10) newlyUnlocked.add(achievement)
                    "wave_20" -> if (oldStats.bestWave < 20 && newStats.bestWave >= 20) newlyUnlocked.add(achievement)
                    "games_10" -> if (oldStats.totalGamesPlayed < 10 && newStats.totalGamesPlayed >= 10) newlyUnlocked.add(achievement)
                    "combos_50" -> if (oldStats.totalTimesComboed < 50 && newStats.totalTimesComboed >= 50) newlyUnlocked.add(achievement)
                    "powerups_25" -> if (oldStats.totalPowerUpsCollected < 25 && newStats.totalPowerUpsCollected >= 25) newlyUnlocked.add(achievement)
                    "bosses_5" -> if (oldStats.totalBossesDefeated < 5 && newStats.totalBossesDefeated >= 5) newlyUnlocked.add(achievement)
                }
            }
        }
        
        return newlyUnlocked
    }
}
