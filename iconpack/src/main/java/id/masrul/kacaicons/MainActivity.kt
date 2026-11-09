package id.masrul.kacaicons

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.xmlpull.v1.XmlPullParser

/** Halaman pratinjau ikon + tombol terapkan ke Kaca Launcher. */
class MainActivity : Activity() {

    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val names = readDrawableNames()
        val ids = names.map { resources.getIdentifier(it, "drawable", packageName) }.filter { it != 0 }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#0B1026"), Color.parseColor("#1B2A6B"), Color.parseColor("#3A1C71")),
            )
            setPadding((16 * dp).toInt(), (40 * dp).toInt(), (16 * dp).toInt(), 0)
            fitsSystemWindows = true
        }
        root.addView(TextView(this).apply {
            text = "Kaca Icons"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "${ids.size} ikon kaca · aplikasi lain otomatis memakai piringan kaca"
            textSize = 14f
            setTextColor(Color.argb(190, 255, 255, 255))
            setPadding(0, (4 * dp).toInt(), 0, (12 * dp).toInt())
        })
        root.addView(Button(this).apply {
            text = "Terapkan di Kaca Launcher"
            setOnClickListener { applyToKaca() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = (76 * dp).toInt()
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = (10 * dp).toInt()
            setPadding(0, (12 * dp).toInt(), 0, (24 * dp).toInt())
            clipToPadding = false
            adapter = object : BaseAdapter() {
                override fun getCount() = ids.size
                override fun getItem(position: Int) = ids[position]
                override fun getItemId(position: Int) = ids[position].toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val iv = (convertView as? ImageView) ?: ImageView(this@MainActivity).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (68 * dp).toInt())
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    iv.setImageResource(ids[position])
                    return iv
                }
            }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun applyToKaca() {
        try {
            startActivity(
                Intent("id.masrul.kaca.APPLY_ICON_PACK")
                    .setPackage("id.masrul.kaca")
                    .putExtra("package", packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Kaca Launcher belum terpasang / versi lama. Pilih manual di pengaturan launcher.", Toast.LENGTH_LONG).show()
        }
    }

    private fun readDrawableNames(): List<String> {
        val out = ArrayList<String>()
        try {
            val p = resources.getXml(R.xml.drawable)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "item") {
                    p.getAttributeValue(null, "drawable")?.let { out.add(it) }
                }
                ev = p.next()
            }
        } catch (_: Exception) { }
        return out
    }
}
