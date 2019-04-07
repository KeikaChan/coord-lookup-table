package work.airz.qranalyze

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.*
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.*


class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSION = 1000
    private lateinit var qrReaderView: DecoratedBarcodeView
    private var lookupMap = hashMapOf<String, String>()
    private var urlMap = hashMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        while (!checkPermission()) {
            Log.d("permission", "not granted")
            Thread.sleep(1000)
        }
        Log.d("permission", "granted")
        loadLookup()
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

    fun loadLookup() {
        BufferedReader(InputStreamReader(resources.openRawResource(R.raw.lookup_table))).use {
            var line = it.readLines()
            line.forEach { line ->
                lookupMap[line.split(",")[0]] = line.split(",")[1]
            }
        }
        BufferedReader(InputStreamReader(resources.openRawResource(R.raw.urls))).use {
            var line = it.readLines()
            line.forEach { line ->
                urlMap[line.split(",")[0]] = line.split(",")[1]
            }
        }
        Toast.makeText(applicationContext,"データを読み込みました。",Toast.LENGTH_LONG)
        Log.d("aaaa","データを読み込みました")
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
        dialogRoot.findViewById<TextView>(R.id.hexall).text = qrstring
        editText.setText(qrstring.replace("\\s".toRegex(), "").substring(6, 18))
        var idText = dialogRoot.findViewById<TextView>(R.id.id)

        var builder = AlertDialog.Builder(this)
        builder.setView(dialogRoot)
        builder.setCancelable(false)
        builder.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialogInterface, _ ->
            dialogInterface.dismiss()
            readerView.resume()
        })
        builder.setPositiveButton("コピー&保存", DialogInterface.OnClickListener { dialogInterface, _ ->

            //クリップボードのサービスのインスタンスを取得する
            var mManager: ClipboardManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            //editTextに入力された文字をクリップボードにコピーする
            mManager.primaryClip = ClipData.newPlainText("label", editText.text)

            val savefile = File(Environment.getExternalStorageDirectory().absolutePath, "priqr_assist")
            if (!savefile.exists() || !savefile.isDirectory) savefile.mkdirs()
            val csvfile = File(savefile, "lookup.csv")
            csvfile.writeText("${editText.text},${idText.text}")

            readerView.resume()
        })
        builder.show()

        val hexStr = editText.text.toString().toLowerCase()
        if (lookupMap.containsKey(hexStr)) {
            Toast.makeText(applicationContext,"すでにあるっぽい",Toast.LENGTH_SHORT)
            Log.d("aaa","すでにあるっぽい")
            ImageAsyncTask().execute(urlMap[lookupMap[hexStr]], imageView)
            idText.text = lookupMap[hexStr]
        }else{
            Toast.makeText(applicationContext,"あたらしいやつ",Toast.LENGTH_SHORT)
            Log.d("aaa","あたらしいやつ")
        }
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

    private fun setImage(url: String, thumbnail: ImageView) {
        ImageAsyncTask().execute(url, thumbnail)
    }

    companion object {
        class ImageAsyncTask : AsyncTask<Any, Void, Bitmap>() {
            var imageView: ImageView? = null

            override fun doInBackground(vararg params: Any): Bitmap? {
                val url = URL(params[0] as? String ?: return null)
                imageView = params[1] as? ImageView ?: return null
                try {
                    val urlConnection = url.openConnection()  as? HttpURLConnection ?: return null
                    urlConnection.readTimeout = 5000
                    urlConnection.connectTimeout = 7000
                    urlConnection.requestMethod = "GET"
                    urlConnection.doInput = true
                    urlConnection.connect()
                    return BitmapFactory.decodeStream(urlConnection.inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }

            override fun onPostExecute(result: Bitmap?) {
                super.onPostExecute(result)
                result ?: return
                imageView!!.setImageBitmap(result)
            }
        }
    }


}
