package com.example.mlkitwithlivecamera

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import com.example.mlkitwithlivecamera.databinding.ArCoreActivityBinding
import com.google.android.filament.RenderableManager
import com.google.android.filament.utils.Utils
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Sceneform
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.ArFrontFacingFragment
import com.google.ar.sceneform.ux.AugmentedFaceNode
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.util.function.Consumer
import java.util.function.Function


class ArCoreActivity : AppCompatActivity() {
    private lateinit var binding: ArCoreActivityBinding

    private var rendableManager: RenderableManager? = null
    private var rm: Int? = null

    private var profileModel: ModelRenderable? = null
    private var arFragment: ArFrontFacingFragment? = null
    private var arSceneView: ArSceneView? = null
    private var session: Session? = null
    private var mlKitFaceDetactor: MLKitFaceDetactor? = null
    private val facesNodes = HashMap<AugmentedFace, AugmentedFaceNode>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.ar_core_activity)
        mlKitFaceDetactor = MLKitFaceDetactor(this)

        if (Sceneform.isSupported(this)) {
            supportFragmentManager.beginTransaction()
                .add(R.id.arFragment, ArFrontFacingFragment::class.java, null).commit()
        }


        supportFragmentManager.addFragmentOnAttachListener(FragmentOnAttachListener { fragmentManager: FragmentManager?, fragment: Fragment? ->
            fragmentManager?.let { fragment?.let { it1 -> onAttachFragment(it, it1) } }
        })

        checkForPermission()
        onClicks()
        setModelData()

    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onAttachFragment(fragmentManager: FragmentManager, fragment: Fragment) {
        if (fragment.id == R.id.arFragment) {
            arFragment = fragment as ArFrontFacingFragment
            arFragment?.setOnViewCreatedListener(ArFragment.OnViewCreatedListener { arSceneView: ArSceneView? ->
                arSceneView?.let { onViewCreated(it) }
            })
        }
    }


    private fun onViewCreated(arSceneView: ArSceneView) {
        this.arSceneView = arSceneView
        // This is important to make sure that the camera stream renders first so that
        // the face mesh occlusion works correctly.
        arSceneView.setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST)
        arSceneView.setZOrderMediaOverlay(true)

        session = arSceneView.session

//        arSceneView.scene?.addOnUpdateListener(::updateListener)


        // Check for face detections
        arFragment!!.setOnAugmentedFaceUpdateListener { augmentedFace: AugmentedFace? ->
            augmentedFace?.let {
                this.onAugmentedFaceTrackingUpdate(
                    it
                )
            }
        }
    }

    private val TAG = "ARCORE"

    private fun onAugmentedFaceTrackingUpdate(augmentedFace: AugmentedFace) {
        if (profileModel == null) {
            return
        }

        val existingFaceNode = facesNodes[augmentedFace]

        when (augmentedFace.trackingState) {
            TrackingState.TRACKING -> {

                try {
                    val frame = arFragment?.arSceneView?.session?.update()
                    Log.e(TAG, "fragme " + frame)
                    frame?.let { tryAcquireCameraImage(it) }


                } catch (ex: Exception) {
                    Log.e(TAG, "error session " + ex.message)
                }


                // here code to track the face with animations
                if (existingFaceNode == null) {
                    val faceNode = AugmentedFaceNode(augmentedFace)

                    val modelInstance = faceNode.setFaceRegionsRenderable(profileModel)
                    arSceneView?.scene?.addChild(faceNode)
                    facesNodes[augmentedFace] = faceNode

                    val entity =
                        modelInstance?.filamentAsset?.getFirstEntityByName("head_lod3_mesh")  //head_lod3_mesh , Wolf3D_Head


                    rendableManager =
                        arSceneView?.scene?.renderer?.filamentRenderer?.engine?.renderableManager
                    rm = rendableManager?.getInstance(entity!!)
                }


            }
            TrackingState.STOPPED -> {
                if (existingFaceNode != null) {
                    arSceneView?.scene?.removeChild(existingFaceNode)
                }
                facesNodes.remove(augmentedFace)
            }
            else -> {}
        }
    }

    private fun tryAcquireCameraImage(frame: Frame) {
        try {
            Log.e(TAG, "image")
            val image = frame.acquireCameraImage()
            val camId = session?.cameraConfig?.cameraId
            mlKitFaceDetactor?.detectFaces(image, 0) { faces ->
                if (!faces.isNullOrEmpty()) {
                    faces.forEach {
                        setAnimation(it)
                    }


                }
                image.close()

            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "image error " + e.message)
            e.printStackTrace()
        }
    }


    private fun checkForPermission() {
        if (allPermissionsGranted()) {
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun onClicks() {

    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {

            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    private fun setAnimation(face: Face) {
        val smileProb = face.smilingProbability ?: 0f
        val leftEye = face.leftEyeOpenProbability ?: 0f
        val rightEye = face.rightEyeOpenProbability ?: 0f


        rm?.let { rendableManager?.setMorphWeights(it, floatArrayOf( if (smileProb > 0.9f) smileProb else 0f,1-leftEye,1-rightEye),0) }


      /*  rm?.let {
            rendableManager?.setMorphWeights(
                it,
                floatArrayOf(
                    0f,
                    if (smileProb > 0.8f) smileProb else 0f,
                    0f,
                    0f,
                    0f,
                    1 - rightEye,
                    0f,
                    0f,
                    1 - leftEye

                ),
                0
            )
        }*/


    }

    private fun setModelData() {
        renderModel()

    }


    private fun renderModel() {
        ModelRenderable.builder()
            .setSource(this, Uri.parse("rp_and.glb"))
            .setIsFilamentGltf(true)
            .setAsyncLoadEnabled(true)
            .build().thenAccept(Consumer { model: ModelRenderable ->
                this.profileModel = model

            })
            .exceptionally(Function<Throwable, Void?> { throwable: Throwable? ->
                Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG)
                    .show()
                null
            })

    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

        init {
            Utils.init()
        }
    }


}