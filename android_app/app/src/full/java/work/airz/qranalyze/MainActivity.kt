package work.airz.qranalyze

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.*
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.nio.charset.Charset
import java.util.*


class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSION = 1000
    private lateinit var qrReaderView: DecoratedBarcodeView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        while (!checkPermission()) {
            Log.d("permission", "not granted")
            Thread.sleep(1000)
        }
        Log.d("permission", "granted")
        readQR()
    }

    fun readQR() {
        qrReaderView = findViewById(R.id.decoratedBarcodeView)
        qrReaderView.decodeContinuous(object : BarcodeCallback {
            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {

            }

            override fun barcodeResult(result: BarcodeResult?) {
                if (result == null || result.barcodeFormat != BarcodeFormat.QR_CODE) return

                val bytes = result.resultMetadata[ResultMetadataType.BYTE_SEGMENTS] as? List<*>
                val data = bytes?.get(0) as? ByteArray ?: return
                val strb = StringBuilder()
                data.forEach { strb.append(String.format("%02X ", it)) }
                Log.d("QR DUMP", strb.toString())

                Log.d("maskIndex", result.result.maskIndex.toString())
                Log.d("QRのサイズ", result.rawBytes.size.toString())
                val qrBitmap = createQR(data, result.result.maskIndex, result.sourceData.isInverted, detectVersionM(result.rawBytes.size))
                qrReaderView.pause()
                saveAlert(qrBitmap, qrReaderView, strb.toString())
            }
        })
        qrReaderView.resume()
    }


    fun createQR(data: ByteArray, maskindex: Int, isInverted: Boolean, version: Int): Bitmap {
        var hints = EnumMap<EncodeHintType, Object>(EncodeHintType::class.java)
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M as Object
        hints[EncodeHintType.MASK_INDEX] = maskindex as Object
        hints[EncodeHintType.QR_VERSION] = version as Object

        val image = MultiFormatWriter().encode(String(data, Charset.forName("ISO-8859-1")), BarcodeFormat.QR_CODE, 256, 256, hints)

        val width = image.width
        val height = image.height
        var bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                when (isInverted) {
                    true -> bmp.setPixel(x, y,
                            if (image.get(x, y)) {
                                Color.WHITE
                            } else {
                                Color.BLACK
                            })
                    false -> bmp.setPixel(x, y,
                            if (image.get(x, y)) {
                                Color.BLACK
                            } else {
                                Color.WHITE
                            })
                }
            }
        }
        return bmp
    }

    fun detectVersionM(size: Int): Int {
        //rawdata -2のサイズがQRコードのバージョン表Mの部分と一致する
        return when (size) {
            14 + 2 -> 1
            26 + 2 -> 2
            42 + 2 -> 3
            62 + 2 -> 4
            84 + 2 -> 5
            106 + 2 -> 6
            122 + 2 -> 7
            152 + 2 -> 8
            180 + 2 -> 9
            213 + 2 -> 10
            else -> 40
        }
    }


    fun saveAlert(qrImage: Bitmap, readerView: DecoratedBarcodeView, qrstring: String) {

        val inflater = LayoutInflater.from(applicationContext)
        var dialogRoot = inflater.inflate(R.layout.save_dialog, null)
/**/
        var imageView = dialogRoot.findViewById<ImageView>(R.id.qrimage)
        imageView.scaleType = ImageView.ScaleType.FIT_XY
        imageView.adjustViewBounds = true
        imageView.setImageBitmap(qrImage)
        var editText = dialogRoot.findViewById<EditText>(R.id.filename)
        dialogRoot.findViewById<TextView>(R.id.hexall).setText(qrstring)
        editText.setText(qrstring.replace("\\s".toRegex(),"") .substring(6,18))

        var builder = AlertDialog.Builder(this)
        builder.setView(dialogRoot)
        builder.setCancelable(false)
        builder.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialogInterface, _ ->
            dialogInterface.dismiss()
            readerView.resume()
        })
        builder.setPositiveButton("クリップボードにコピー", DialogInterface.OnClickListener { dialogInterface, _ ->

            //クリップボードのサービスのインスタンスを取得する
            var mManager: ClipboardManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                //editTextに入力された文字をクリップボードにコピーする
            mManager.primaryClip = ClipData.newPlainText("label", editText.text)

            readerView.resume()
        })
        builder.show()
    }
    private fun checkPermission(): Boolean {
        // 既に許可している
        return if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            requestStoragePermission()
            false
        }// 拒否していた場合
    }


    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA), REQUEST_PERMISSION)

    }


    override fun onResume() {
        super.onResume()
        qrReaderView.resume()
    }

    override fun onPause() {
        super.onPause()
        qrReaderView.resume()
    }

    override fun onStop() {
        super.onStop()
        qrReaderView.pause()
    }


}
