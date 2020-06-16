package com.example.playerproject.ui.player

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.example.playerproject.R
import com.example.playerproject.ui.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_player.*

class PlayerFragment : BaseFragment() {

    companion object {
        fun newInstance() = PlayerFragment()
    }

    override val viewModel: PlayerViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivPlayPause.setOnClickListener {
            viewModel.onPlayPauseClick()
        }

        ivNext.setOnClickListener {
            viewModel.onNextClick()
        }

        ivPrevious.setOnClickListener {
            viewModel.onPreviousClick()
        }

        sbPosition.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                viewModel.onSeek(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        viewModel.isPlaying.observe(viewLifecycleOwner, Observer { isPlaying ->
            ivPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause_48 else R.drawable.ic_play_48)
        })

        viewModel.position.observe(viewLifecycleOwner, Observer { playPosition ->
            sbPosition.progress = playPosition.position
            tvCurrentTime.text = playPosition.currentTime
            tvRemainingTime.text = playPosition.remainingTime
        })

        viewModel.metadata.observe(viewLifecycleOwner, Observer { metadata ->
            tvTitle.text = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            tvArtist.text = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            ivArtwork.setImageBitmap(metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART))
        })
    }
}
