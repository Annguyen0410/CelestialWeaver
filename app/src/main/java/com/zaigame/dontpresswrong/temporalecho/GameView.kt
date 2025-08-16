package com.zaigame.dontpresswrong

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var isAmbientMode = false

    private enum class GameState { WAITING_TO_START, PLAYING, GAME_OVER, TUTORIAL }
    private var currentState = GameState.WAITING_TO_START
    private var score = 0
    private var lives = 10
    private var currentWave = 1
    private var targetsInWave = 0
    private var targetsCompleted = 0

    // Tutorial state
    private var tutorialStep = 0
    private var tutorialMessage = ""

    private val gameScope = CoroutineScope(Dispatchers.Main + Job())
    private var gameLoopJob: Job? = null

    // Enhanced wave configuration with power-ups and special targets
    private fun getWaveConfig(wave: Int): WaveConfig {
        return when {
            wave <= 3 -> WaveConfig(
                targetsCount = 5 + wave * 2,
                baseSpeed = 3f + wave * 0.5f,
                speedIncrease = 0.1f,
                maxSpeed = 8f,
                temporalRiftChance = 0.15f,
                powerUpChance = 0.1f,
                specialTargetChance = 0.05f,
                bossWave = false
            )
            wave <= 6 -> WaveConfig(
                targetsCount = 8 + wave * 2,
                baseSpeed = 5f + wave * 0.3f,
                speedIncrease = 0.15f,
                maxSpeed = 12f,
                temporalRiftChance = 0.20f,
                powerUpChance = 0.15f,
                specialTargetChance = 0.1f,
                bossWave = false
            )
            wave % 5 == 0 -> WaveConfig( // Boss waves every 5 waves
                targetsCount = 1,
                baseSpeed = 2f,
                speedIncrease = 0f,
                maxSpeed = 2f,
                temporalRiftChance = 0f,
                powerUpChance = 0f,
                specialTargetChance = 0f,
                bossWave = true
            )
            else -> WaveConfig(
                targetsCount = 12 + wave,
                baseSpeed = 7f + wave * 0.2f,
                speedIncrease = 0.2f,
                maxSpeed = 15f + wave * 0.5f,
                temporalRiftChance = 0.25f,
                powerUpChance = 0.2f,
                specialTargetChance = 0.15f,
                bossWave = false
            )
        }
    }

    data class WaveConfig(
        val targetsCount: Int,
        val baseSpeed: Float,
        val speedIncrease: Float,
        val maxSpeed: Float,
        val temporalRiftChance: Float,
        val powerUpChance: Float,
        val specialTargetChance: Float,
        val bossWave: Boolean
    )

    // ART STYLE & EFFECTS
    data class Star(var x: Float, var y: Float, val radius: Float, var alpha: Float, val speed: Float)
    data class OrreryRing(val radius: Float, val speed: Float, var angle: Float, val stroke: Float)
    data class CometTrail(var x: Float, var y: Float, var life: Float)
    data class Mandala(var radius: Float, var angle: Float, var alpha: Int)
    data class NebulaCloud(var x: Float, var y: Float, val radius: Float, var alpha: Float, val color: Int)
    data class TemporalRemnant(val angle: Float, var life: Float)
    data class ScoreParticle(var x: Float, var y: Float, var life: Float)
    data class ShootingStar(var x: Float, var y: Float, var length: Float, var life: Float)
    data class WaveParticle(var x: Float, var y: Float, var life: Float, val color: Int)

    private val stars = mutableListOf<Star>()
    private val orreryRings = mutableListOf<OrreryRing>()
    private val cometTrail = mutableListOf<CometTrail>()
    private val mandalas = mutableListOf<Mandala>()
    private val nebulaClouds = mutableListOf<NebulaCloud>()
    private val temporalRemnants = mutableListOf<TemporalRemnant>()
    private val scoreParticles = mutableListOf<ScoreParticle>()
    private val shootingStars = mutableListOf<ShootingStar>()
    private val waveParticles = mutableListOf<WaveParticle>()

    private var crackleTime = 0
    private var breathingPhase = 0f
    private var waveTransitionTime = 0

    // Enhanced target system with special types
    enum class TargetType { NORMAL, POWERUP, SPECIAL, BOSS }
    enum class PowerUpType { EXTRA_LIFE, SLOW_TIME, DOUBLE_SCORE, SHIELD }
    
    data class Target(
        var angle: Float, 
        var radius: Float, 
        var speed: Float,
        val type: TargetType = TargetType.NORMAL,
        val powerUp: PowerUpType? = null,
        var health: Int = 1,
        var lastHitTime: Long = 0
    )
    private var currentTarget: Target? = null
    
    // Combo and scoring system
    private var comboCount = 0
    private var comboMultiplier = 1
    private var lastHitTime = 0L
    private val comboTimeWindow = 2000L // 2 seconds
    
    // Power-up states
    private var slowTimeActive = false
    private var slowTimeRemaining = 0
    private var doubleScoreActive = false
    private var doubleScoreRemaining = 0
    private var shieldActive = false
    private var shieldRemaining = 0
    
    // Game statistics tracking
    private var powerUpsCollectedThisGame = 0
    private var bossesDefeatedThisGame = 0
    private var combosAchievedThisGame = 0
    
    // Screen shake and camera effects
    private var screenShakeIntensity = 0f
    private var screenShakeDuration = 0
    private var cameraOffsetX = 0f
    private var cameraOffsetY = 0f
    
    // Enhanced particle effects
    data class ExplosionParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
    data class SparkParticle(var x: Float, var y: Float, var angle: Float, var speed: Float, var life: Float)
    data class RippleEffect(var x: Float, var y: Float, var radius: Float, var life: Float)
    
    private val explosionParticles = mutableListOf<ExplosionParticle>()
    private val sparkParticles = mutableListOf<SparkParticle>()
    private val rippleEffects = mutableListOf<RippleEffect>()

    // PAINTS
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val powerUpPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specialTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orreryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mandalaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cracklePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val remnantPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tutorialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val comboPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val powerUpStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val explosionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var orbitalRadius = 0f
    private val orreryBaseRadius = 80f
    private val hitZoneOuter = orreryBaseRadius + 60f
    private val hitZoneInner = orreryBaseRadius - 60f

    var onScoreUpdate: ((Int, Int, Int) -> Unit)? = null
    var onGameOver: ((Int, Int) -> Unit)? = null
    var onWaveComplete: ((Int) -> Unit)? = null
    var onTutorialComplete: (() -> Unit)? = null

    init {
        setupPaints()
        initializeVisualElements()
    }

    private fun setupPaints() {
        orreryPaint.style = Paint.Style.STROKE
        orreryPaint.color = Color.parseColor("#FFD700")

        mandalaPaint.style = Paint.Style.STROKE
        mandalaPaint.strokeWidth = 3f
        mandalaPaint.color = Color.parseColor("#FFFFFF")

        targetPaint.color = Color.parseColor("#FFD700")
        
        // Power-up targets (green with glow)
        powerUpPaint.color = Color.parseColor("#00FF00")
        
        // Special targets (purple with effects)
        specialTargetPaint.color = Color.parseColor("#FF00FF")
        
        // Boss targets (red with multiple layers)
        bossPaint.color = Color.parseColor("#FF4444")
        bossPaint.style = Paint.Style.STROKE
        bossPaint.strokeWidth = 8f
        
        trailPaint.color = Color.parseColor("#00FFFF")

        cracklePaint.style = Paint.Style.STROKE
        cracklePaint.strokeWidth = 4f
        cracklePaint.color = Color.parseColor("#FF0000")

        remnantPaint.style = Paint.Style.STROKE
        remnantPaint.strokeWidth = 5f
        remnantPaint.color = Color.parseColor("#00FFFF")

        tutorialPaint.color = Color.parseColor("#FFFFFF")
        tutorialPaint.textSize = 24f
        tutorialPaint.textAlign = Paint.Align.CENTER
        
        comboPaint.color = Color.parseColor("#FFFF00")
        comboPaint.textSize = 32f
        comboPaint.textAlign = Paint.Align.CENTER
        comboPaint.isFakeBoldText = true
        
        powerUpStatusPaint.color = Color.parseColor("#00FF00")
        powerUpStatusPaint.textSize = 16f
        powerUpStatusPaint.textAlign = Paint.Align.LEFT
        
        explosionPaint.style = Paint.Style.FILL
        sparkPaint.style = Paint.Style.STROKE
        sparkPaint.strokeWidth = 2f
        
        ripplePaint.style = Paint.Style.STROKE
        ripplePaint.strokeWidth = 3f
    }

    private fun initializeVisualElements() {
        orreryRings.add(OrreryRing(orreryBaseRadius - 15f, 0.5f, 0f, 4f))
        orreryRings.add(OrreryRing(orreryBaseRadius, -0.8f, 90f, 6f))
        orreryRings.add(OrreryRing(orreryBaseRadius + 15f, 0.3f, 180f, 3f))

        repeat(150) {
            stars.add(Star(
                Random.nextFloat() * 2f - 0.5f,
                Random.nextFloat() * 2f - 0.5f,
                Random.nextFloat() * 2.5f + 1f,
                Random.nextFloat() * 0.8f,
                Random.nextFloat() * 0.0002f + 0.0001f
            ))
        }

        repeat(10) {
            nebulaClouds.add(NebulaCloud(
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat() * 300f + 200f,
                Random.nextFloat() * 0.1f + 0.05f,
                if (Random.nextBoolean()) Color.parseColor("#4A148C") else Color.parseColor("#3F51B5")
            ))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, h)
        centerX = w / 2f
        centerY = h / 2f
        orbitalRadius = (w.coerceAtMost(h) / 2f) * 0.9f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Apply screen shake
        if (screenShakeDuration > 0) {
            canvas.save()
            canvas.translate(cameraOffsetX, cameraOffsetY)
        }
        
        canvas.drawColor(Color.parseColor("#0C0A1D"))
        drawBackground(canvas)

        if (isAmbientMode) {
            if (screenShakeDuration > 0) canvas.restore()
            return
        }

        drawEffects(canvas)
        drawEnhancedParticles(canvas)
        drawGameElements(canvas)
        drawFailureEffect(canvas)

        if (currentState == GameState.TUTORIAL) {
            drawTutorial(canvas)
        }

        if (waveTransitionTime > 0) {
            drawWaveTransition(canvas)
        }
        
        // Restore canvas after screen shake
        if (screenShakeDuration > 0) {
            canvas.restore()
        }
    }

    private fun drawBackground(canvas: Canvas) {
        nebulaClouds.forEach {
            nebulaPaint.shader = RadialGradient(
                it.x * width, it.y * height, it.radius,
                it.color, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            nebulaPaint.alpha = (255 * it.alpha).toInt()
            canvas.drawCircle(it.x * width, it.y * height, it.radius, nebulaPaint)
        }

        stars.forEach {
            starPaint.alpha = (255 * it.alpha).toInt()
            starPaint.color = Color.WHITE
            canvas.drawCircle(it.x * width, it.y * height, it.radius, starPaint)
        }

        shootingStars.forEach { star ->
            val endX = star.x + star.length
            val endY = star.y + star.length
            starPaint.alpha = (255 * star.life).toInt()
            canvas.drawLine(star.x, star.y, endX, endY, starPaint)
        }
    }

    private fun drawEffects(canvas: Canvas) {
        val glowRadius = orreryBaseRadius + 30f + sin(breathingPhase) * 10f
        glowPaint.shader = RadialGradient(
            centerX, centerY, glowRadius,
            Color.parseColor("#4DFFD700"), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        mandalas.forEach { mandala ->
            mandalaPaint.alpha = mandala.alpha
            canvas.save()
            canvas.rotate(mandala.angle, centerX, centerY)
            for (i in 0 until 6) {
                val lineAngle = i * (360f / 6f)
                val endX = centerX + mandala.radius * cos(Math.toRadians(lineAngle.toDouble())).toFloat()
                val endY = centerY + mandala.radius * sin(Math.toRadians(lineAngle.toDouble())).toFloat()
                canvas.drawLine(centerX, centerY, endX, endY, mandalaPaint)
            }
            canvas.restore()
        }

        cometTrail.forEach {
            trailPaint.alpha = (255 * it.life).toInt()
            canvas.drawCircle(it.x, it.y, 4f, trailPaint)
        }

        scoreParticles.forEach {
            starPaint.alpha = (255 * it.life).toInt()
            canvas.drawCircle(it.x, it.y, it.life * 5f, starPaint)
        }

        waveParticles.forEach { particle ->
            starPaint.alpha = (255 * particle.life).toInt()
            starPaint.color = particle.color
            canvas.drawCircle(particle.x, particle.y, particle.life * 8f, starPaint)
        }
    }
    
    private fun drawEnhancedParticles(canvas: Canvas) {
        // Draw explosion particles
        explosionParticles.forEach { particle ->
            explosionPaint.color = particle.color
            explosionPaint.alpha = (255 * particle.life).toInt()
            canvas.drawCircle(particle.x, particle.y, particle.life * 8f, explosionPaint)
        }
        
        // Draw spark particles
        sparkParticles.forEach { spark ->
            sparkPaint.color = Color.parseColor("#FFFF00")
            sparkPaint.alpha = (255 * spark.life).toInt()
            val endX = spark.x + cos(Math.toRadians(spark.angle.toDouble())).toFloat() * spark.speed * 2f
            val endY = spark.y + sin(Math.toRadians(spark.angle.toDouble())).toFloat() * spark.speed * 2f
            canvas.drawLine(spark.x, spark.y, endX, endY, sparkPaint)
        }
        
        // Draw ripple effects
        rippleEffects.forEach { ripple ->
            ripplePaint.color = Color.parseColor("#00FFFF")
            ripplePaint.alpha = (255 * ripple.life).toInt()
            canvas.drawCircle(ripple.x, ripple.y, ripple.radius, ripplePaint)
        }
    }

    private fun drawGameElements(canvas: Canvas) {
        temporalRemnants.forEach { remnant ->
            remnantPaint.alpha = (255 * remnant.life).toInt()
            val startAngle = remnant.angle - 15
            val sweepAngle = 30f
            canvas.drawArc(
                centerX - orreryBaseRadius, centerY - orreryBaseRadius,
                centerX + orreryBaseRadius, centerY + orreryBaseRadius,
                startAngle, sweepAngle, false, remnantPaint
            )
        }

        orreryRings.forEach {
            orreryPaint.strokeWidth = it.stroke
            canvas.save()
            canvas.rotate(it.angle, centerX, centerY)
            canvas.drawCircle(centerX, centerY, it.radius, orreryPaint)
            canvas.restore()
        }

        if (currentState == GameState.PLAYING || currentState == GameState.TUTORIAL) {
            currentTarget?.let { target ->
                val x = centerX + target.radius * cos(Math.toRadians(target.angle.toDouble())).toFloat()
                val y = centerY + target.radius * sin(Math.toRadians(target.angle.toDouble())).toFloat()

                // Enhanced target rendering based on type
                val baseRadius = if (currentState == GameState.TUTORIAL) {
                    20f + sin(breathingPhase * 3f) * 5f
                } else {
                    when (target.type) {
                        TargetType.BOSS -> 40f + sin(breathingPhase * 2f) * 8f
                        TargetType.SPECIAL -> 25f + sin(breathingPhase * 4f) * 3f
                        TargetType.POWERUP -> 22f + sin(breathingPhase * 5f) * 2f
                        else -> 20f
                    }
                }

                when (target.type) {
                    TargetType.BOSS -> {
                        // Multi-layered boss target
                        for (i in 1..target.health) {
                            val layerRadius = baseRadius + (i * 10f)
                            bossPaint.alpha = 255 - (i * 40)
                            canvas.drawCircle(x, y, layerRadius, bossPaint)
                        }
                        canvas.drawCircle(x, y, baseRadius, targetPaint)
                    }
                    TargetType.POWERUP -> {
                        // Glowing power-up with aura
                        val glowRadius = baseRadius + 15f
                        glowPaint.shader = RadialGradient(
                            x, y, glowRadius,
                            Color.parseColor("#4400FF00"), Color.TRANSPARENT, Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(x, y, glowRadius, glowPaint)
                        canvas.drawCircle(x, y, baseRadius, powerUpPaint)
                    }
                    TargetType.SPECIAL -> {
                        // Pulsing special target with trail
                        canvas.drawCircle(x, y, baseRadius + 5f, specialTargetPaint)
                        canvas.drawCircle(x, y, baseRadius, targetPaint)
                    }
                    else -> {
                        canvas.drawCircle(x, y, baseRadius, targetPaint)
                    }
                }
            }
        }
        
        // Draw power-up status indicators
        if (slowTimeActive || doubleScoreActive || shieldActive) {
            drawPowerUpStatus(canvas)
        }
        
        // Draw combo indicator
        if (comboCount > 1) {
            drawComboIndicator(canvas)
        }
    }

    private fun drawTutorial(canvas: Canvas) {
        val message = when (tutorialStep) {
            0 -> "Welcome to Celestial Weaver!\nTap anywhere to continue"
            1 -> "Golden orbs approach from the edges\nYour goal is to tap when they reach the rings"
            2 -> "Tap when the orb is near the golden rings!\nTry it now!"
            3 -> "Perfect! Notice the blue remnant left behind\nThese create temporal rifts"
            4 -> "Tap a rift when an orb passes through\nto gain extra life and bonus points!"
            5 -> "Miss too many orbs and lose all lives\nand your fate will be severed!"
            else -> "Tutorial complete!\nGet ready for the real challenge"
        }

        // Semi-transparent background
        canvas.drawColor(Color.parseColor("#80000000"))

        // Draw message
        val lines = message.split("\n")
        var yOffset = centerY - (lines.size * 30f) / 2f

        lines.forEach { line ->
            canvas.drawText(line, centerX, yOffset, tutorialPaint)
            yOffset += 40f
        }
    }

    private fun drawWaveTransition(canvas: Canvas) {
        val alpha = (waveTransitionTime / 60f * 255).toInt().coerceIn(0, 255)
        val waveConfig = getWaveConfig(currentWave)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (waveConfig.bossWave) Color.parseColor("#FF4444") else Color.parseColor("#FFD700")
            textSize = 48f
            textAlign = Paint.Align.CENTER
            this.alpha = alpha
        }

        canvas.drawColor(Color.parseColor("#${alpha.toString(16).padStart(2, '0')}000000"))
        
        val waveText = if (waveConfig.bossWave) "BOSS WAVE $currentWave" else "WAVE $currentWave"
        canvas.drawText(waveText, centerX, centerY - 30f, paint)

        val subPaint = Paint(paint).apply {
            textSize = 24f
        }
        val subText = if (waveConfig.bossWave) "Cosmic Guardian Approaches..." else "Prepare yourself..."
        canvas.drawText(subText, centerX, centerY + 30f, subPaint)
    }
    
    private fun drawPowerUpStatus(canvas: Canvas) {
        var yOffset = 100f
        
        if (slowTimeActive) {
            canvas.drawText("⏱️ SLOW TIME (${slowTimeRemaining/60}s)", 20f, yOffset, powerUpStatusPaint)
            yOffset += 25f
        }
        
        if (doubleScoreActive) {
            canvas.drawText("⭐ DOUBLE SCORE (${doubleScoreRemaining/60}s)", 20f, yOffset, powerUpStatusPaint)
            yOffset += 25f
        }
        
        if (shieldActive) {
            canvas.drawText("🛡️ SHIELD (${shieldRemaining/60}s)", 20f, yOffset, powerUpStatusPaint)
            yOffset += 25f
        }
    }
    
    private fun drawComboIndicator(canvas: Canvas) {
        val comboText = "COMBO x$comboCount"
        val comboY = centerY + 100f
        
        // Pulsing effect for combo
        val pulseFactor = 1f + sin(breathingPhase * 8f) * 0.2f
        comboPaint.textSize = 32f * pulseFactor
        
        // Shadow effect
        comboPaint.color = Color.parseColor("#80000000")
        canvas.drawText(comboText, centerX + 2f, comboY + 2f, comboPaint)
        
        // Main text
        comboPaint.color = Color.parseColor("#FFFF00")
        canvas.drawText(comboText, centerX, comboY, comboPaint)
    }

    private fun drawFailureEffect(canvas: Canvas) {
        if (crackleTime > 0) {
            cracklePaint.alpha = (255 * (crackleTime / 10f)).toInt()
            orreryRings.forEach {
                for (i in 0..3) {
                    val startAngle = Random.nextFloat() * 360
                    val endAngle = startAngle + Random.nextInt(-20, 20)
                    val startX = centerX + it.radius * cos(Math.toRadians(startAngle.toDouble())).toFloat()
                    val startY = centerY + it.radius * sin(Math.toRadians(startAngle.toDouble())).toFloat()
                    val endX = centerX + it.radius * cos(Math.toRadians(endAngle.toDouble())).toFloat()
                    val endY = centerY + it.radius * sin(Math.toRadians(endAngle.toDouble())).toFloat()
                    canvas.drawLine(startX, startY, endX, endY, cracklePaint)
                }
            }
        }
    }

    private fun updateGame() {
        orreryRings.forEach { it.angle += it.speed }
        stars.forEach {
            it.y += it.speed
            if (it.y > 1.5f) it.y = -0.5f
        }

        shootingStars.removeAll { it.life <= 0 }
        shootingStars.forEach {
            it.x += 15f
            it.y += 15f
            it.life -= 0.02f
        }
        if (Random.nextInt(0, 100) == 0) {
            shootingStars.add(ShootingStar(0f, Random.nextFloat() * height, 20f, 1f))
        }

        if (isAmbientMode) {
            invalidate()
            return
        }

        breathingPhase += 0.03f

        cometTrail.removeAll { it.life <= 0 }
        cometTrail.forEach { it.life -= 0.04f }

        mandalas.removeAll { it.alpha <= 0 }
        mandalas.forEach {
            it.radius += 20f
            it.angle += 2f
            it.alpha -= 10
        }

        scoreParticles.removeAll { it.life <= 0 }
        scoreParticles.forEach { particle ->
            val targetY = 50f
            particle.y -= (particle.y - targetY) * 0.1f
            particle.life -= 0.03f
        }

        waveParticles.removeAll { it.life <= 0 }
        waveParticles.forEach {
            it.life -= 0.02f
        }
        
        // Update enhanced particle effects
        explosionParticles.removeAll { it.life <= 0 }
        explosionParticles.forEach { particle ->
            particle.x += particle.vx
            particle.y += particle.vy
            particle.vx *= 0.98f // Slight drag
            particle.vy *= 0.98f
            particle.life -= 0.03f
        }
        
        sparkParticles.removeAll { it.life <= 0 }
        sparkParticles.forEach { spark ->
            spark.x += cos(Math.toRadians(spark.angle.toDouble())).toFloat() * spark.speed
            spark.y += sin(Math.toRadians(spark.angle.toDouble())).toFloat() * spark.speed
            spark.speed *= 0.95f
            spark.life -= 0.04f
        }
        
        rippleEffects.removeAll { it.life <= 0 }
        rippleEffects.forEach { ripple ->
            ripple.radius += 15f
            ripple.life -= 0.02f
        }
        
        // Update screen shake
        if (screenShakeDuration > 0) {
            screenShakeDuration--
            val shakeX = (Random.nextFloat() - 0.5f) * screenShakeIntensity
            val shakeY = (Random.nextFloat() - 0.5f) * screenShakeIntensity
            cameraOffsetX = shakeX
            cameraOffsetY = shakeY
            screenShakeIntensity *= 0.9f // Fade out shake
        } else {
            cameraOffsetX = 0f
            cameraOffsetY = 0f
        }

        if (crackleTime > 0) crackleTime--
        if (waveTransitionTime > 0) waveTransitionTime--

        // Update power-up timers
        if (slowTimeActive) {
            slowTimeRemaining--
            if (slowTimeRemaining <= 0) slowTimeActive = false
        }
        
        if (doubleScoreActive) {
            doubleScoreRemaining--
            if (doubleScoreRemaining <= 0) doubleScoreActive = false
        }
        
        if (shieldActive) {
            shieldRemaining--
            if (shieldRemaining <= 0) shieldActive = false
        }
        
        // Check combo expiration
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastHitTime > comboTimeWindow) {
            comboCount = 0
            comboMultiplier = 1
        }

        temporalRemnants.removeAll { it.life <= 0 }
        temporalRemnants.forEach { it.life -= 0.002f }

        if (currentState == GameState.PLAYING || currentState == GameState.TUTORIAL) {
            currentTarget?.let { target ->
                // Apply slow time effect
                val effectiveSpeed = if (slowTimeActive) target.speed * 0.3f else target.speed
                target.radius -= effectiveSpeed
                
                if (target.radius > orreryBaseRadius) {
                    val x = centerX + target.radius * cos(Math.toRadians(target.angle.toDouble())).toFloat()
                    val y = centerY + target.radius * sin(Math.toRadians(target.angle.toDouble())).toFloat()
                    cometTrail.add(CometTrail(x, y, 1f))
                }
                if (target.radius < hitZoneInner) {
                    if (currentState == GameState.TUTORIAL) {
                        handleTutorialMiss()
                    } else {
                        handleMiss()
                    }
                }
            }
        }

        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action != MotionEvent.ACTION_DOWN) return true

        when (currentState) {
            GameState.TUTORIAL -> handleTutorialTap()
            GameState.PLAYING -> handlePlayTap()
            else -> return true
        }
        return true
    }

    private fun handleTutorialTap() {
        when (tutorialStep) {
            0, 1 -> {
                tutorialStep++
                if (tutorialStep == 2) {
                    spawnTutorialTarget()
                }
            }
            2, 3, 4 -> {
                val target = currentTarget
                if (target != null && target.radius in hitZoneInner..hitZoneOuter) {
                    handleTutorialSuccess()
                } else {
                    // Show hint for missing
                    tutorialMessage = "Try tapping when the orb is closer to the rings!"
                }
            }
            5 -> {
                tutorialStep++
                onTutorialComplete?.invoke()
                currentState = GameState.WAITING_TO_START
            }
        }
    }

    private fun handlePlayTap() {
        val target = currentTarget ?: return

        val activatedRemnant = temporalRemnants.find { r ->
            val diff = abs(target.angle - r.angle)
            (diff < 15f || diff > 345f) && target.radius in (orreryBaseRadius - 20f)..(orreryBaseRadius + 20f)
        }

        if (activatedRemnant != null) {
            handleRiftActivation(activatedRemnant)
            return
        }

        if (target.radius in hitZoneInner..hitZoneOuter) {
            handleSuccess()
        } else {
            handleMiss()
        }
    }

    private fun handleTutorialSuccess() {
        SoundManager.playSfx(SoundManager.ID_CHIME)
        temporalRemnants.add(TemporalRemnant(currentTarget!!.angle, 1f))
        tutorialStep++

        when (tutorialStep) {
            3 -> tutorialMessage = "Great! See the blue rift you created?"
            4 -> {
                tutorialMessage = "Now try tapping the rift when the next orb passes through!"
                spawnTutorialTarget()
            }
            5 -> tutorialMessage = "Excellent! You're ready for the real challenge!"
        }

        if (tutorialStep < 5) {
            spawnTutorialTarget()
        }
    }

    private fun handleTutorialMiss() {
        // In tutorial, just respawn target
        spawnTutorialTarget()
    }

    private fun handleRiftActivation(remnant: TemporalRemnant) {
        SoundManager.playSfx(SoundManager.ID_RIFT)
        if (lives < 10) lives++
        score += 5
        onScoreUpdate?.invoke(score, lives, currentWave)

        temporalRemnants.remove(remnant)
        mandalas.add(Mandala(0f, 0f, 255))

        // Wave particles for rift activation
        repeat(20) {
            waveParticles.add(WaveParticle(
                centerX + Random.nextFloat() * 200f - 100f,
                centerY + Random.nextFloat() * 200f - 100f,
                1f,
                Color.parseColor("#00FFFF")
            ))
        }

        spawnNewTarget()
    }

    private fun handleSuccess() {
        SoundManager.playSfx(SoundManager.ID_CHIME)
        val target = currentTarget!!
        
        // Handle different target types
        when (target.type) {
            TargetType.POWERUP -> {
                activatePowerUp(target.powerUp!!)
            }
            TargetType.BOSS -> {
                target.health--
                SoundManager.playSfx(SoundManager.ID_BOSS_HIT)
                if (target.health > 0) {
                    // Boss still alive, don't spawn new target yet
                    target.lastHitTime = System.currentTimeMillis()
                    return
                } else {
                    // Boss defeated - special sound effect
                    SoundManager.playSfx(SoundManager.ID_START, 0.7f) // Lower pitch for dramatic effect
                    bossesDefeatedThisGame++
                }
            }
            TargetType.SPECIAL -> {
                // Special targets give bonus points
                score += 3
            }
            else -> {
                // Normal target
            }
        }
        
        // Update combo system
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastHitTime <= comboTimeWindow) {
            comboCount++
            if (comboCount > 1 && comboCount % 5 == 0) {
                SoundManager.playSfx(SoundManager.ID_COMBO) // Play combo sound every 5 hits
                combosAchievedThisGame++ // Track combo achievements
            }
        } else {
            comboCount = 1
        }
        lastHitTime = currentTime
        comboMultiplier = (comboCount / 5) + 1
        
        // Calculate score with multipliers
        var pointsEarned = 1 * comboMultiplier
        if (doubleScoreActive) pointsEarned *= 2
        
        score += pointsEarned
        targetsCompleted++
        onScoreUpdate?.invoke(score, lives, currentWave)

        repeat(15 + comboCount) {
            scoreParticles.add(ScoreParticle(centerX, centerY, 1f))
        }
        
        // Enhanced visual effects for different target types
        val targetX = centerX + target.radius * cos(Math.toRadians(target.angle.toDouble())).toFloat()
        val targetY = centerY + target.radius * sin(Math.toRadians(target.angle.toDouble())).toFloat()
        
        when (target.type) {
            TargetType.BOSS -> {
                createExplosion(targetX, targetY, Color.parseColor("#FF4444"), 30)
                createSparks(targetX, targetY, 20)
                triggerScreenShake(8f, 20)
                createRipple(targetX, targetY)
            }
            TargetType.POWERUP -> {
                createExplosion(targetX, targetY, Color.parseColor("#00FF00"), 25)
                triggerScreenShake(4f, 10)
                createRipple(targetX, targetY)
            }
            TargetType.SPECIAL -> {
                createExplosion(targetX, targetY, Color.parseColor("#FF00FF"), 20)
                createSparks(targetX, targetY, 15)
                triggerScreenShake(6f, 15)
            }
            else -> {
                createExplosion(targetX, targetY, Color.parseColor("#FFD700"), 15)
                if (comboCount > 5) {
                    createSparks(targetX, targetY, 10)
                    triggerScreenShake(2f, 5)
                }
            }
        }

        temporalRemnants.add(TemporalRemnant(target.angle, 1f))
        mandalas.add(Mandala(orreryBaseRadius, Random.nextFloat() * 360f, 255))

        checkWaveCompletion()
    }
    
    private fun activatePowerUp(powerUp: PowerUpType) {
        SoundManager.playSfx(SoundManager.ID_POWERUP)
        powerUpsCollectedThisGame++
        
        when (powerUp) {
            PowerUpType.EXTRA_LIFE -> {
                if (lives < 15) lives++ // Cap at 15 lives
            }
            PowerUpType.SLOW_TIME -> {
                slowTimeActive = true
                slowTimeRemaining = 300 // 5 seconds at 60fps
            }
            PowerUpType.DOUBLE_SCORE -> {
                doubleScoreActive = true
                doubleScoreRemaining = 600 // 10 seconds at 60fps
            }
            PowerUpType.SHIELD -> {
                shieldActive = true
                shieldRemaining = 180 // 3 seconds at 60fps
            }
        }
    }

    private fun handleMiss() {
        // Check if shield is active
        if (shieldActive) {
            // Shield absorbs the hit
            shieldActive = false
            shieldRemaining = 0
            SoundManager.playSfx(SoundManager.ID_CHIME) // Different sound for shield
        } else {
            SoundManager.playSfx(SoundManager.ID_TWANG)
            lives--
            crackleTime = 10
            
            // Screen shake on miss
            triggerScreenShake(5f, 15)
            
            // Reset combo on miss
            comboCount = 0
            comboMultiplier = 1
        }
        
        onScoreUpdate?.invoke(score, lives, currentWave)

        if (lives <= 0) {
            endGame()
        } else {
            spawnNewTarget()
        }
    }

    private fun checkWaveCompletion() {
        val waveConfig = getWaveConfig(currentWave)
        if (targetsCompleted >= waveConfig.targetsCount) {
            completeWave()
        } else {
            spawnNewTarget()
        }
    }

    private fun completeWave() {
        onWaveComplete?.invoke(currentWave)
        currentWave++
        targetsCompleted = 0

        // Wave completion effects
        repeat(30) {
            waveParticles.add(WaveParticle(
                centerX + Random.nextFloat() * 300f - 150f,
                centerY + Random.nextFloat() * 300f - 150f,
                1f,
                Color.parseColor("#FFD700")
            ))
        }

        waveTransitionTime = 120 // 2 seconds at 60fps

        // Delay before spawning next wave
        currentTarget = null
        gameScope.launch {
            delay(2000)
            spawnNewTarget()
        }
    }

    fun startAmbientMode() {
        isAmbientMode = true
        gameLoopJob?.cancel()
        gameLoopJob = gameScope.launch {
            while (isActive) {
                updateGame()
                delay(16)
            }
        }
    }

    fun startTutorial() {
        isAmbientMode = false
        currentState = GameState.TUTORIAL
        tutorialStep = 0
        score = 0
        lives = 10
        currentWave = 1
        targetsCompleted = 0

        mandalas.clear()
        temporalRemnants.clear()
        scoreParticles.clear()
        waveParticles.clear()

        onScoreUpdate?.invoke(score, lives, currentWave)
    }

    fun startGame() {
        isAmbientMode = false
        SoundManager.playSfx(SoundManager.ID_START)

        // Reset game state
        score = 0
        lives = 10
        currentWave = 1
        targetsCompleted = 0
        currentState = GameState.PLAYING
        
        // Reset combo system
        comboCount = 0
        comboMultiplier = 1
        lastHitTime = 0L
        
        // Reset power-ups
        slowTimeActive = false
        slowTimeRemaining = 0
        doubleScoreActive = false
        doubleScoreRemaining = 0
        shieldActive = false
        shieldRemaining = 0
        
        // Reset game statistics
        powerUpsCollectedThisGame = 0
        bossesDefeatedThisGame = 0
        combosAchievedThisGame = 0

        onScoreUpdate?.invoke(score, lives, currentWave)

        mandalas.clear()
        temporalRemnants.clear()
        scoreParticles.clear()
        waveParticles.clear()
        explosionParticles.clear()
        sparkParticles.clear()
        rippleEffects.clear()

        spawnNewTarget()
    }

    private fun spawnNewTarget() {
        val angle = Random.nextFloat() * 360f
        val waveConfig = getWaveConfig(currentWave)
        val speed = (waveConfig.baseSpeed + targetsCompleted * waveConfig.speedIncrease)
            .coerceAtMost(waveConfig.maxSpeed)
        
        // Determine target type based on wave configuration
        val targetType = when {
            waveConfig.bossWave -> TargetType.BOSS
            Random.nextFloat() < waveConfig.powerUpChance -> TargetType.POWERUP
            Random.nextFloat() < waveConfig.specialTargetChance -> TargetType.SPECIAL
            else -> TargetType.NORMAL
        }
        
        // Set target properties based on type
        val (health, powerUp) = when (targetType) {
            TargetType.BOSS -> {
                val bossHealth = 3 + (currentWave / 5) // Stronger bosses in later waves
                Pair(bossHealth, null)
            }
            TargetType.POWERUP -> {
                val randomPowerUp = PowerUpType.values().random()
                Pair(1, randomPowerUp)
            }
            else -> Pair(1, null)
        }

        currentTarget = Target(
            angle = angle,
            radius = orbitalRadius,
            speed = speed,
            type = targetType,
            powerUp = powerUp,
            health = health
        )
        cometTrail.clear()
    }

    private fun spawnTutorialTarget() {
        val angle = Random.nextFloat() * 360f
        currentTarget = Target(angle, orbitalRadius, 3f) // Slow speed for tutorial
        cometTrail.clear()
    }

    private fun endGame() {
        currentState = GameState.GAME_OVER
        currentTarget = null
        onGameOver?.invoke(score, currentWave - 1)
    }
    
    // Public getters for statistics
    fun getPowerUpsCollected(): Int = powerUpsCollectedThisGame
    fun getBossesDefeated(): Int = bossesDefeatedThisGame  
    fun getCombosAchieved(): Int = combosAchievedThisGame
    
    // Method to continue current game (for wave transitions)
    fun continueCurrentGame() {
        if (currentState == GameState.PLAYING && currentTarget == null) {
            // Only continue if we're in a wave transition state
            spawnNewTarget()
        }
    }
    
    // Enhanced visual effects methods
    private fun triggerScreenShake(intensity: Float, duration: Int) {
        screenShakeIntensity = intensity
        screenShakeDuration = duration
    }
    
    private fun createExplosion(x: Float, y: Float, color: Int, particleCount: Int = 20) {
        repeat(particleCount) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 8f + 2f
            val vx = cos(Math.toRadians(angle.toDouble())).toFloat() * speed
            val vy = sin(Math.toRadians(angle.toDouble())).toFloat() * speed
            explosionParticles.add(ExplosionParticle(x, y, vx, vy, 1f, color))
        }
    }
    
    private fun createSparks(x: Float, y: Float, sparkCount: Int = 15) {
        repeat(sparkCount) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 5f + 2f
            sparkParticles.add(SparkParticle(x, y, angle, speed, 1f))
        }
    }
    
    private fun createRipple(x: Float, y: Float) {
        rippleEffects.add(RippleEffect(x, y, 10f, 1f))
    }
}