package com.example.eiscamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.eiscamera.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    
    // OpenCV объекты для FFT
    private var anchorFrame: Mat? = null
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Полный экран
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV Error", Toast.LENGTH_SHORT).show()
        }

        setupInteractions()
        checkPermissions()
    }

    private fun setupInteractions() {
        binding.btnSettings.setOnClickListener {
            val isVisible = binding.panelSettings.visibility == View.VISIBLE
            binding.panelSettings.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding.btnResetAnchor.setOnClickListener {
            anchorFrame = null
            Toast.makeText(this, "Якорь сброшен", Toast.LENGTH_SHORT).show()
        }

        binding.btnRecord.setOnClickListener {
            isRecording = !isRecording
            binding.btnRecord.setImageResource(
                if (isRecording) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            // Логика MediaCodec будет здесь
        }

        binding.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                // Здесь мы будем перехватывать кадр и делать FFT корреляцию
                processCurrentFrame()
            }
        }
    }

    private fun processCurrentFrame() {
        val bitmap = binding.textureView.bitmap ?: return
        val rgba = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, rgba)
        
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        gray.convertTo(gray, CvType.CV_32F)

        if (anchorFrame == null) {
            anchorFrame = gray
            return
        }

        // ХАРДКОР: Фазовая корреляция Фурье (FFT)
        // Находим сдвиг (shift.x, shift.y) и уровень корреляции (response)
        val shift = Imgproc.phaseCorrelate(anchorFrame, gray)
        
        if (shift.x != 0.0 || shift.y != 0.0) {
            // Тут будет код трансформации матрицы для стабилизации
        }
    }

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
            
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, null)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startPreview() {
        val surfaceTexture = binding.textureView.surfaceTexture ?: return
        surfaceTexture.setDefaultBufferSize(1280, 720)
        val surface = android.view.Surface(surfaceTexture)
        
        val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder?.addTarget(surface)
        
        cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                builder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                session.setRepeatingRequest(builder!!.build(), null, null)
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, null)
    }

    private fun checkPermissions() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        ActivityCompat.requestPermissions(this, perms, 123)
    }
}
