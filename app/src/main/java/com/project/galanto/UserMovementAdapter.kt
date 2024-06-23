package com.project.galanto

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.galanto.databinding.UserMovementItemRowBinding
import com.project.galanto.model.UserMovementModel

class UserMovementAdapter(private val context: Context,private val userMovementArray: ArrayList<UserMovementModel>) :
    RecyclerView.Adapter<UserMovementAdapter.MyViewHolder>() {
    class MyViewHolder(val binding: UserMovementItemRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(
            UserMovementItemRowBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.binding.successRateProgress.setDonut_progress(userMovementArray[position].percent)
        holder.binding.successRateProgress.text = userMovementArray[position].percent
        holder.binding.successRateProgress.max = userMovementArray[position].max
        holder.binding.successRateText.text = userMovementArray[position].userText
    }

    override fun getItemCount(): Int {
        return userMovementArray.size
    }
}