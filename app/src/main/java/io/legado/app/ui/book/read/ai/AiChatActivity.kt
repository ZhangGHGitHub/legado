package io.legado.app.ui.book.read.ai

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.lib.dialogs.alert
import io.legado.app.databinding.ActivityAiChatBinding
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.config.AiConfigDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AiChatActivity : BaseActivity<ActivityAiChatBinding>(false) {

    override val binding by viewBinding(ActivityAiChatBinding::inflate)
    private val viewModel by viewModels<AiChatViewModel>()
    private val adapter by lazy { ChatAdapter() }

    /** 是否为独立模式（从"我的"页面进入，无书籍上下文） */
    private val isStandalone: Boolean get() = ReadBook.book == null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        bindEvent()
        observeData()
        setupKeyboardAdjustment()

        if (isStandalone) {
            // 独立模式：隐藏章节选择区域，直接初始化
            binding.layoutChapterRange.visibility = View.GONE
            binding.titleBar.title = getString(R.string.ai_assistant)
            viewModel.initMessages(0, 0)
        } else {
            val currentChapter = (ReadBook.durChapterIndex + 1).toString()
            binding.etChapterStart.setText(currentChapter)
            binding.etChapterEnd.setText(currentChapter)
            viewModel.initMessages(
                ReadBook.durChapterIndex + 1,
                ReadBook.durChapterIndex + 1
            )
            updateWordCount()
        }
    }

    private fun setupKeyboardAdjustment() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottomPadding = if (imeHeight > navBarHeight) imeHeight else navBarHeight
            binding.root.setPadding(0, 0, 0, bottomPadding)
            insets
        }
    }

    private fun initView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter
    }

    private fun bindEvent() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateWordCount()
            }
        }
        binding.etChapterStart.addTextChangedListener(textWatcher)
        binding.etChapterEnd.addTextChangedListener(textWatcher)

        binding.btnSend.setOnClickListener {
            if (viewModel.isGeneratingLiveData.value == true) {
                toastOnUi("正在生成中...")
                return@setOnClickListener
            }
            val text = binding.etInput.text.toString()
            if (text.isNotBlank()) {
                val start: Int
                val end: Int
                if (isStandalone) {
                    start = 0
                    end = 0
                } else {
                    start = binding.etChapterStart.text.toString().toIntOrNull()
                        ?: (ReadBook.durChapterIndex + 1)
                    end = binding.etChapterEnd.text.toString().toIntOrNull()
                        ?: (ReadBook.durChapterIndex + 1)
                }
                viewModel.sendMessage(text, start, end)
                binding.etInput.setText("")
            }
        }
    }

    private fun updateWordCount() {
        if (isStandalone) return
        val start = binding.etChapterStart.text.toString().toIntOrNull()
        val end = binding.etChapterEnd.text.toString().toIntOrNull()
        val bookUrl = ReadBook.book?.bookUrl
        val chapterSize = ReadBook.chapterSize
        if (start != null && end != null && start > 0 && end > 0 && bookUrl != null && chapterSize > 0) {
            viewModel.calculateWordCount(bookUrl, start, end)
        }
    }

    private fun observeData() {
        viewModel.messagesLiveData.observe(this) { msgs ->
            val displayMsgs = msgs.filter { it.role != "system" && it.role != "tool" }
            adapter.submitList(displayMsgs)
            if (displayMsgs.isNotEmpty()) {
                binding.recyclerView.scrollToPosition(displayMsgs.size - 1)
            }
        }

        viewModel.wordCountLiveData.observe(this) { count ->
            if (isStandalone) return@observe
            val countStr = if (count >= 10000) String.format("%.1f万", count / 10000f) else count.toString()
            binding.tvWordCount.text = "字数: $countStr"
            if (count > 50000) {
                binding.tvWordCount.setTextColor(Color.RED)
            } else {
                binding.tvWordCount.setTextColor(Color.parseColor("#888888"))
            }
        }

        viewModel.isGeneratingLiveData.observe(this) { isGenerating ->
            if (isGenerating) {
                binding.btnSend.setImageResource(R.drawable.ic_stop_black_24dp)
            } else {
                binding.btnSend.setImageResource(R.drawable.ic_send)
            }
        }

        viewModel.confirmationLiveData.observe(this) { request ->
            if (request == null) return@observe
            alert(R.string.ai_confirm_title) {
                setMessage(request.description)
                yesButton {
                    viewModel.confirmAction(true)
                }
                noButton {
                    viewModel.confirmAction(false)
                }
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ai_chat_menu, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_ai_settings -> {
                showDialogFragment(AiConfigDialog())
                return true
            }
            R.id.menu_ai_summarize -> {
                if (isStandalone) {
                    toastOnUi("独立模式下无法归纳记忆，请在阅读界面中使用")
                    return true
                }
                val start = binding.etChapterStart.text.toString().toIntOrNull()
                    ?: (ReadBook.durChapterIndex + 1)
                val end = binding.etChapterEnd.text.toString().toIntOrNull()
                    ?: (ReadBook.durChapterIndex + 1)
                viewModel.summarizeAndMemory(start, end)
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }
}
