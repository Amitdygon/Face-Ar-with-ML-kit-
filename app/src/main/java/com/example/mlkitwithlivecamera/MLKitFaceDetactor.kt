package com.example.mlkitwithlivecamera

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class MLKitFaceDetactor(context: Context) {
    private val TAG = "MlKitFaceDetactor"

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
    // [END set_detector_options]

    // [START get_detector]
    val detector = FaceDetection.getClient(realTimeOpts)


    fun detectFaces(image: InputImage) {
        detector.process(image).addOnSuccessListener {
            if (it.isNullOrEmpty()) {
                Log.e(TAG,"no face")
                return@addOnSuccessListener
            }

            for (face in it) {

                val smileProb = face.smilingProbability ?: 0f
                val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
                val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f

                Log.e(TAG, "simle")

                if (smileProb > 0.8f) {
                    Log.e(TAG, "simle")
                }


                if (leftEyeOpenProb < 0.2f) {
                    Log.e(TAG, "left blink")
                }

                if (rightEyeOpenProb < 0.2f) {
                    Log.e(TAG, "right blink")
                }
//                detector.close()
            }


        }.addOnFailureListener {
            Log.e(TAG,"error "+it.message)
//            detector.close()
        }

//        detector.process()


    }

    fun detectFaces(image: Image, rotation: Int,listener:(face:MutableList<Face>?) -> Unit) {
        detector.process(image,rotation).addOnSuccessListener {
            listener.invoke(it)
            if (it.isNullOrEmpty()) {
                Log.e(TAG,"no face")
                return@addOnSuccessListener
            }

            for (face in it) {

                val smileProb = face.smilingProbability ?: 0f
                val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
                val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f

                if (smileProb > 0.8f) {
                    Log.e(TAG, "simle")
                }


                if (leftEyeOpenProb < 0.2f) {
                    Log.e(TAG, "left blink")
                }

                if (rightEyeOpenProb < 0.2f) {
                    Log.e(TAG, "right blink")
                }
            }


        }.addOnFailureListener {
            listener.invoke(null)
            Log.e(TAG,"error "+it.message)
        }
    }


}