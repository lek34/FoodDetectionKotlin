package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException


fun String.toMediaTypeOrNull(): MediaType? = try {
    this.toMediaTypeOrNull()
} catch (e: IllegalArgumentException) {
    null
}

class UploadImage : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var uploadButton: Button

    private val PICK_IMAGE_REQUEST = 1
    private var selectedImage: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_image)

        imageView = findViewById(R.id.imageView)
        uploadButton = findViewById(R.id.uploadButton)

        imageView.setOnClickListener {
            openImagePicker()
        }

        uploadButton.setOnClickListener {
            if (selectedImage != null) {
                uploadImage()
            } else {
                openImagePicker() // Open image picker if no image is selected
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            try {
                val bitmap: Bitmap =
                    MediaStore.Images.Media.getBitmap(contentResolver, data.data)
                imageView.setImageBitmap(bitmap)

                // Convert Bitmap to ByteArray
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
                selectedImage = byteArrayOutputStream.toByteArray()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadImage() {
        // Replace with your actual PHP script URL
        val uploadUrl = "https://fastapicc-php-ku3urc7swa-uc.a.run.app" // Example IP address, replace with your server's IP

        // Create request body
        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "image.jpg",
                RequestBody.create("image/jpeg".toMediaTypeOrNull(), selectedImage!!)
            )
            .build()

        val client = OkHttpClient()
        val request: Request = Request.Builder()
            .url(uploadUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            Log.d("API Response Body", responseBody)
                        }

                        // Parse the JSON response
                        val jsonResponse = JSONObject(responseBody!!)
                        val predictions = jsonResponse.getJSONArray("predictions")

                        // Get the image dimensions
                        val imageInfo = jsonResponse.getJSONObject("image")
                        val originalWidth = imageInfo.getInt("width")
                        val originalHeight = imageInfo.getInt("height")

                        // Resize the image to 450x450
                        val drawable = imageView.drawable as BitmapDrawable
                        val originalBitmap = drawable.bitmap
//                        val resizedBitmap =
//                            Bitmap.createScaledBitmap(originalBitmap, 450, 450, true)

                        // Calculate scaling factors for drawing on the ImageView
                        val scaleX: Float = imageView.width.toFloat() / originalWidth.toFloat()
                        val scaleY: Float = imageView.height.toFloat() / originalHeight.toFloat()

                        // Create a mutable bitmap for drawing
                        val mutableBitmap =
                            Bitmap.createBitmap(originalBitmap)
                                .copy(Bitmap.Config.ARGB_8888, true)

                        // Get the canvas to draw on the bitmap
                        val canvas = Canvas(mutableBitmap)

                        // Iterate through predictions and draw bounding boxes
                        for (i in 0 until predictions.length()) {
                            val prediction = predictions.getJSONObject(i)

                            val centerX = prediction.getDouble("x").toFloat()
                            val centerY = prediction.getDouble("y").toFloat()
                            val width = prediction.getDouble("width").toFloat()
                            val height = prediction.getDouble("height").toFloat()

                            // Calculate corner points
                            val x1 = centerX - (width / 2)
                            val y1 = centerY - (height / 2)
                            val x2 = centerX + (width / 2)
                            val y2 = centerY + (height / 2)

                            // Draw bounding box
                            val paint = Paint()
                            paint.color = Color.RED
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 5f
                            canvas.drawRect(x1, y1, x2, y2, paint)

                            // Draw class label
                            val label = prediction.getString("class")
                            paint.color = Color.RED
                            paint.style = Paint.Style.FILL
                            paint.textSize = 30f
                            canvas.drawText(label, x1, y1 - 10, paint)
                        }

                        // Update the ImageView with the modified bitmap
                        runOnUiThread {
                            imageView.setImageBitmap(mutableBitmap)
                        }

                    } else {
                        Log.e(
                            "API Error",
                            "Unsuccessful response: " + response.code + " " + response.message
                        )
                    }
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                } finally {
                    response.body?.close()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e("API Error", "Failed to make API request: " + e.message)
            }
        })
    }
}