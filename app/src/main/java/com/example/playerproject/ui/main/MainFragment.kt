package com.example.playerproject.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.example.playerproject.R
import com.example.playerproject.ui.base.BaseFragment
import com.example.playerproject.ui.player.PlayerFragment
import kotlinx.android.synthetic.main.main_fragment.*

class MainFragment : BaseFragment() {

    companion object {
        fun newInstance() = MainFragment()

        const val FOLDER_REQUEST_CODE = 1
    }

    override val viewModel: MainViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnChooseFolder.setOnClickListener {
            openDirectory()
        }

        ivPlayPause.setOnClickListener {
            viewModel.onPlayPauseClick()
        }

        ivNext.setOnClickListener {
            viewModel.onNextClick()
        }

        llPlayer.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .addToBackStack(null)
                .add(R.id.container, PlayerFragment.newInstance())
                .commit()
        }

        viewModel.isPlaying.observe(viewLifecycleOwner, Observer { isPlaying ->
            ivPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause_28 else R.drawable.ic_play_48)
        })

        viewModel.remainingTime.observe(viewLifecycleOwner, Observer { remainingTime ->
            tvRemainingTime.text = remainingTime
        })

        viewModel.description.observe(viewLifecycleOwner, Observer { description ->
            tvPlayerTitle.text = description?.title
            ivArtwork.setImageBitmap(description?.iconBitmap)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FOLDER_REQUEST_CODE) {
            data?.data?.let { viewModel.onFolderChosen(it) }
        }
    }

    private fun openDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivityForResult(intent, FOLDER_REQUEST_CODE)
    }
}
