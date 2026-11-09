package com.xiangxiang.audioextractor

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val files = mutableListOf<Uri>()
    private lateinit var status: TextView
    private lateinit var selected: TextView
    private lateinit var format: Spinner
    private lateinit var startTime: EditText
    private lateinit var endTime: EditText
    private lateinit var volume: EditText

    private var operation = "extract"
    private var busy = false

    private val formats = arrayOf("MP3", "WAV", "AAC")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 20, 22, 20)
            setBackgroundColor(Color.rgb(247, 249, 250))
        }

        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "湘湘视野"
            textSize = 27f
            setTextColor(Color.rgb(17, 126, 112))
            gravity = Gravity.CENTER
            setPadding(0, 25, 0, 4)
        }

        val subtitle = TextView(this).apply {
            text = "专业音频提取器"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 25)
        }

        page.addView(title)
        page.addView(subtitle)

        page.addView(section("一、选择媒体文件"))

        page.addView(makeButton("选择视频 / 音频文件") {
            pickFiles(false)
        })

        page.addView(makeButton("选择多个文件（用于合并）") {
            pickFiles(true)
        })

        selected = TextView(this).apply {
            text = "尚未选择文件"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(12, 15, 12, 15)
        }
        page.addView(selected)

        page.addView(section("二、输出格式"))

        format = Spinner(this)
        format.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            formats
        )
        page.addView(format)

        page.addView(section("三、音频剪切"))

        startTime = makeInput("开始时间（秒），例如 10")
        endTime = makeInput("结束时间（秒），例如 60")

        page.addView(startTime)
        page.addView(endTime)

        page.addView(section("四、音量调整"))

        volume = makeInput("音量倍数，例如 1.5 或 0.5")
        volume.setText("1.0")
        page.addView(volume)

        page.addView(section("五、选择处理功能"))

        page.addView(makeButton("提取视频音频 / 格式转换") {
            operation = "extract"
            process()
        })

        page.addView(makeButton("剪切音频") {
            operation = "trim"
            process()
        })

        page.addView(makeButton("合并多个音频") {
            operation = "merge"
            process()
        })

        page.addView(makeButton("调整音量") {
            operation = "volume"
            process()
        })

        page.addView(section("处理状态"))

        status = TextView(this).apply {
            text = "等待操作"
            textSize = 15f
            setTextColor(Color.rgb(17, 126, 112))
            setPadding(12, 18, 12, 18)
        }
        page.addView(status)

        val note = TextView(this).apply {
            text = "湘湘视野 · 行走，发现热爱生活。"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 35, 0, 15)
        }
        page.addView(note)

        scroll.addView(page)
        root.addView(scroll)
        setContentView(root)
    }

    private fun section(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(Color.rgb(17, 126, 112))
            setPadding(5, 20, 5, 10)
        }
    }

    private fun makeButton(
        text: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }
    }

    private fun makeInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 15f
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
        }
    }

    private fun pickFiles(multiple: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("video/*", "audio/*")
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }
        startActivityForResult(intent, 1001)
    }

    @Deprecated("使用系统文件选择器")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 1001 || resultCode != RESULT_OK || data == null) {
            return
        }

        files.clear()

        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                files.add(clip.getItemAt(i).uri)
            }
        } ?: data.data?.let {
            files.add(it)
        }

        selected.text = if (files.isEmpty()) {
            "尚未选择文件"
        } else {
            "已选择 ${files.size} 个文件\n" +
                files.joinToString("\n") { displayName(it) }
        }

        status.text = "文件选择完成"
    }

    private fun displayName(uri: Uri): String {
        var name = "媒体文件"
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use {
            if (it.moveToFirst()) {
                name = it.getString(0) ?: name
            }
        }
        return name
    }

    private fun quote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun codec(): Pair<String, String> {
        return when (formats[format.selectedItemPosition]) {
            "WAV" -> Pair("pcm_s16le", "wav")
            "AAC" -> Pair("aac", "aac")
            else -> Pair("libmp3lame", "mp3")
        }
    }

    private fun process() {
        if (busy) return

        if (files.isEmpty()) {
            status.text = "请先选择媒体文件"
            return
        }

        if (operation == "merge" && files.size < 2) {
            status.text = "合并至少需要选择两个文件"
            return
        }

        val codecInfo = codec()
        val extension = codecInfo.second
        val output = File(
            cacheDir,
            "audio_${System.currentTimeMillis()}.$extension"
        )

        busy = true
        status.text = "正在准备文件..."

        Thread {
            try {
                val inputs = files.mapIndexed { index, uri ->
                    val file = File(cacheDir, "input_$index.tmp")
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "无法读取文件" }
                        FileOutputStream(file).use { out ->
                            input.copyTo(out)
                        }
                    }
                    file
                }

                val command = when (operation) {
                    "trim" -> {
                        val start = startTime.text.toString().toDoubleOrNull()
                        val end = endTime.text.toString().toDoubleOrNull()

                        require(start != null && end != null &&
                            start >= 0 && end > start) {
                            "请填写正确的剪切起止时间"
                        }

                        "-y -ss $start -i ${quote(inputs[0].absolutePath)} " +
                            "-t ${end - start} -vn -c:a ${codecInfo.first} " +
                            quote(output.absolutePath)
                    }

                    "merge" -> {
                        val inputArgs = inputs.mapIndexed { i, file ->
                            "-i ${quote(file.absolutePath)}"
                        }.joinToString(" ")

                        val filter = inputs.indices.joinToString("") {
                            "[$it:a:0]"
                        } + "concat=n=${inputs.size}:v=0:a=1[outa]"

                        "-y $inputArgs -filter_complex ${quote(filter)} " +
                            "-map ${quote("[outa]")} -c:a ${codecInfo.first} " +
                            quote(output.absolutePath)
                    }

                    "volume" -> {
                        val factor = volume.text.toString().toDoubleOrNull()
                        require(factor != null && factor in 0.0..10.0) {
                            "音量倍数应在 0 到 10 之间"
                        }

                        "-y -i ${quote(inputs[0].absolutePath)} -vn " +
                            "-af ${quote("volume=$factor")} " +
                            "-c:a ${codecInfo.first} " +
                            quote(output.absolutePath)
                    }

                    else -> {
                        "-y -i ${quote(inputs[0].absolutePath)} " +
                            "-vn -c:a ${codecInfo.first} " +
                            if (extension == "mp3" || extension == "aac")
                                "-b:a 192k ${quote(output.absolutePath)}"
                            else
                                quote(output.absolutePath)
                    }
                }

                runOnUiThread {
                    status.text = "正在处理音频，请稍候..."
                }

                FFmpegKit.executeAsync(command) { session ->
                    val success = ReturnCode.isSuccess(session.returnCode)

                    runOnUiThread {
                        if (success && output.exists() && output.length() > 0) {
                            saveOutput(output, extension)
                        } else {
                            busy = false
                            status.text =
                                "处理失败：\n${session.failStackTrace ?: "请检查文件格式和音轨"}"
                        }
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    busy = false
                    status.text = "错误：${e.message}"
                }
            }
        }.start()
    }

    private fun saveOutput(file: File, extension: String) {
        try {
            val mime = when (extension) {
                "wav" -> "audio/wav"
                "aac" -> "audio/aac"
                else -> "audio/mpeg"
            }

            val timestamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(Date())

            val values = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    "湘湘视野_$timestamp.$extension"
                )
                put(MediaStore.MediaColumns.MIME_TYPE, mime)

                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "Music/XiangXiangAudio"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection =
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Audio.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

            val uri = contentResolver.insert(collection, values)
                ?: throw Exception("无法创建保存文件")

            contentResolver.openOutputStream(uri).use { stream ->
                requireNotNull(stream) { "无法打开保存位置" }
                file.inputStream().use { input ->
                    input.copyTo(stream)
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(uri, done, null, null)
            }

            file.delete()
            busy = false
            status.text = "处理成功！\n音频已保存到手机音乐目录。"

        } catch (e: Exception) {
            busy = false
            status.text = "保存失败：${e.message}"
        }
    }
}
