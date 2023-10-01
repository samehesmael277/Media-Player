package com.sameh.mediaplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.sameh.mediaplayer.databinding.ItemAudioBinding

class AudioAdapter(
    private val context: Context
) : RecyclerView.Adapter<AudioAdapter.ViewHolder>() {

    private var audioClickListener: ((Audio, Int) -> Unit)? = null

    fun onAudioClickListener(clickListener: (Audio, Int) -> Unit) {
        audioClickListener = clickListener
    }

    var selectedAudioPosition = -1

    inner class ViewHolder(private val binding: ItemAudioBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(audio: Audio, position: Int) {
            binding.apply {
                tvAudioTitle.text = audio.title
                tvAudioArtist.text = audio.artist
                tvAudioDuration.text = audio.duration
                ivAudio.load(audio.albumArt) {
                    error(R.drawable.ic_music)
                }
                if (position == selectedAudioPosition)
                    binding.icAudioPlay.visibility = View.VISIBLE
                else
                    binding.icAudioPlay.visibility = View.INVISIBLE

                root.setOnClickListener {
                    if (selectedAudioPosition != layoutPosition) {
                        notifyItemChanged(selectedAudioPosition)
                        selectedAudioPosition = layoutPosition
                        notifyItemChanged(selectedAudioPosition)
                        audioClickListener?.invoke(audio, position)
                    }
                }
            }
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Audio>() {
        override fun areItemsTheSame(oldItem: Audio, newItem: Audio): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Audio, newItem: Audio): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemAudioBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = differ.currentList[position]
        holder.bind(currentItem, position)
    }
}