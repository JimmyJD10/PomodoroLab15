package com.bpareja.pomodorotec.pomodoro

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bpareja.pomodorotec.MainActivity
import com.bpareja.pomodorotec.PomodoroReceiver
import com.bpareja.pomodorotec.R

enum class Phase {
    FOCUS, BREAK
}

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {

    init {
        instance = this
    }

    companion object {
        private var instance: PomodoroViewModel? = null
        fun skipBreak() {
            instance?.startFocusSession() // Saltar el descanso y comenzar sesión de concentración
        }
    }

    private val context = getApplication<Application>().applicationContext

    private val _timeLeft = MutableLiveData("01:00")
    val timeLeft: LiveData<String> = _timeLeft

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _isPaused = MutableLiveData(false)
    val isPaused: LiveData<Boolean> = _isPaused

    private val _currentPhase = MutableLiveData(Phase.FOCUS)
    val currentPhase: LiveData<Phase> = _currentPhase

    private val _isSkipBreakButtonVisible = MutableLiveData(false)
    val isSkipBreakButtonVisible: LiveData<Boolean> = _isSkipBreakButtonVisible

    private var countDownTimer: CountDownTimer? = null
    private var timeRemainingInMillis: Long = 30 * 1000L

    // Función para iniciar la sesión de concentración
    fun startFocusSession() {
        countDownTimer?.cancel() // Cancela cualquier temporizador en ejecución
        _currentPhase.value = Phase.FOCUS
        timeRemainingInMillis = 30 * 1000L
        _timeLeft.value = "01:00"
        _isPaused.value = false
        _isSkipBreakButtonVisible.value = false // Ocultar el botón si estaba visible
        showNotification("Inicio de Concentración", "La sesión de concentración ha comenzado.")
        startTimer() // Inicia el temporizador con el tiempo de enfoque actualizado
    }

    // Función para iniciar la sesión de descanso
    private fun startBreakSession() {
        countDownTimer?.cancel()
        _currentPhase.value = Phase.BREAK
        timeRemainingInMillis = 30 * 1000L
        _timeLeft.value = "01:00"
        _isPaused.value = false
        _isSkipBreakButtonVisible.value = true // Mostrar el botón durante el descanso
        showNotification("Inicio de Descanso", "La sesión de descanso ha comenzado.")
        startTimer()
    }

    // Inicia o reanuda el temporizador
    fun startTimer() {
        countDownTimer?.cancel()
        _isRunning.value = true
        _isPaused.value = false

        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                _timeLeft.value = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                _isRunning.value = false
                when (_currentPhase.value ?: Phase.FOCUS) {
                    Phase.FOCUS -> startBreakSession()
                    Phase.BREAK -> startFocusSession()
                }
            }
        }.start()
    }

    // Pausa el temporizador
    fun pauseTimer() {
        countDownTimer?.cancel()
        _isRunning.value = false
        _isPaused.value = true
    }

    // Reanuda el temporizador desde donde se pausó
    fun resumeTimer() {
        if (_isPaused.value == true) {
            _isPaused.value = false
            startTimer()
        }
    }

    // Restablece el temporizador
    fun resetTimer() {
        countDownTimer?.cancel()
        _isRunning.value = false
        _currentPhase.value = Phase.FOCUS
        timeRemainingInMillis = 30 * 1000L
        _timeLeft.value = "01:00"
        _isPaused.value = false
        _isSkipBreakButtonVisible.value = false // Ocultar el botón al restablecer
    }

    // Muestra la notificación personalizada
    private fun showNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = if (_currentPhase.value == Phase.FOCUS) R.drawable.ic_focus else R.drawable.ic_break
        val soundUri = if (_currentPhase.value == Phase.FOCUS)
            Uri.parse("android.resource://${context.packageName}/raw/focus_sound")
        else
            Uri.parse("android.resource://${context.packageName}/raw/break_sound")

        val notificationColor = if (_currentPhase.value == Phase.FOCUS)
            0xFFFF0000.toInt()
        else
            0xFF00FF00.toInt()

        val bigPicture = if (_currentPhase.value == Phase.FOCUS)
            BitmapFactory.decodeResource(context.resources, R.drawable.focus_image)
        else
            BitmapFactory.decodeResource(context.resources, R.drawable.breack_image)

        // Crear vibración personalizada
        val vibrator = context.getSystemService(Application.VIBRATOR_SERVICE) as Vibrator
        val vibrationEffect = if (_currentPhase.value == Phase.FOCUS) {
            VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE) // Vibración rápida para enfoque
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 500, 500), intArrayOf(0, 255, 0), -1) // Vibración de onda para descanso
        }
            vibrator.vibrate(vibrationEffect)

        val progress = (timeRemainingInMillis / 1000).toInt()

        val builder = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setProgress(100, progress, false) // Barra de progreso
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setColor(notificationColor)
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bigPicture))
            .addAction(
                R.drawable.ic_skip, "Saltar Descanso",
                PendingIntent.getBroadcast(context, 0, Intent(context, PomodoroReceiver::class.java).apply {
                    action = "SKIP_BREAK"
                }, PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(
                R.drawable.ic_pause, if (_isPaused.value == true) "Reanudar" else "Pausar",
                PendingIntent.getBroadcast(context, 1, Intent(context, PomodoroReceiver::class.java).apply {
                    action = if (_isPaused.value == true) "RESUME_TIMER" else "PAUSE_TIMER"
                }, PendingIntent.FLAG_IMMUTABLE)
            )

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(MainActivity.NOTIFICATION_ID, builder.build())
        }
    }
}
