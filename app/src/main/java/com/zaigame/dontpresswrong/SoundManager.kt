package com.zaigame.dontpresswrong

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

object SoundManager {

    private lateinit var soundPool: SoundPool
    private lateinit var prefs: SharedPreferences
    private var bgmPlayer: MediaPlayer? = null

    var sfxVolume = 1.0f
        private set
    var bgmVolume = 0.5f
        private set

    const val ID_CHIME = 1
    const val ID_TWANG = 2
    const val ID_START = 3
    const val ID_RIFT = 4
    const val ID_POWERUP = 5
    const val ID_COMBO = 6
    const val ID_BOSS_HIT = 7

    private val soundMap = mutableMapOf<Int, Int>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("GameSettings", Context.MODE_PRIVATE)
        sfxVolume = prefs.getFloat("sfx_volume", 1.0f)
        bgmVolume = prefs.getFloat("bgm_volume", 0.5f)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        soundMap[ID_CHIME] = soundPool.load(context, R.raw.celestial_chime, 1)
        soundMap[ID_TWANG] = soundPool.load(context, R.raw.celestial_twang, 1)
        soundMap[ID_START] = soundPool.load(context, R.raw.celestial_start, 1)
        soundMap[ID_RIFT] = soundPool.load(context, R.raw.temporal_rift, 1)
        
        // Use existing sounds for new effects for now
        soundMap[ID_POWERUP] = soundPool.load(context, R.raw.celestial_chime, 1)
        soundMap[ID_COMBO] = soundPool.load(context, R.raw.celestial_start, 1)
        soundMap[ID_BOSS_HIT] = soundPool.load(context, R.raw.temporal_rift, 1)

        bgmPlayer = MediaPlayer.create(context, R.raw.celestial_bgm)
        bgmPlayer?.isLooping = true
        bgmPlayer?.setVolume(bgmVolume, bgmVolume)
    }

    fun playSfx(soundId: Int, pitch: Float = 1f, volume: Float = sfxVolume) {
        soundMap[soundId]?.let {
            soundPool.play(it, volume, volume, 1, 0, pitch)
        }
    }

    fun setSfxVolume(volume: Float) {
        sfxVolume = volume
        prefs.edit().putFloat("sfx_volume", volume).apply()
    }

    fun setBgmVolume(volume: Float) {
        bgmVolume = volume
        bgmPlayer?.setVolume(bgmVolume, bgmVolume)
        prefs.edit().putFloat("bgm_volume", volume).apply()
    }

    fun startBgm() {
        if (bgmPlayer?.isPlaying == false) {
            bgmPlayer?.start()
        }
    }

    fun pauseBgm() {
        if (bgmPlayer?.isPlaying == true) {
            bgmPlayer?.pause()
        }
    }

    fun release() {
        soundPool.release()
        bgmPlayer?.release()
        bgmPlayer = null
    }
}