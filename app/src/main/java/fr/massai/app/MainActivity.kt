package fr.massai.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        findViewById<Button>(R.id.captureButton).setOnClickListener {
            startActivityForResult(Intent(MediaStore.ACTION_VIDEO_CAPTURE), 10)
        }
        findViewById<Button>(R.id.importButton).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="video/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 11)
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode==Activity.RESULT_OK && data?.data!=null) status.text = "Vidéo chargée ✓\nPrête pour l'extraction des images et la reconstruction 3D."
    }
}
