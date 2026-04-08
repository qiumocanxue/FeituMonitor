package com.feitu.monitor.remote

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.feitu.monitor.R
import com.feitu.monitor.common.models.AgentInfo

class AgentAdapter : RecyclerView.Adapter<AgentAdapter.AgentViewHolder>() {

    private val agentList = mutableListOf<AgentInfo>()
    var onItemClick: ((AgentInfo) -> Unit)? = null

    fun setAgents(newList: List<AgentInfo>) {
        val diffCallback = AgentDiffCallback(this.agentList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        this.agentList.clear()
        this.agentList.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateAgentStats(uniqueId: String, cpu: Int, ram: Double, up: Double, down: Double) {
        val index = agentList.indexOfFirst { it.UniqueId == uniqueId }

        if (index != -1) {
            val agent = agentList[index]
            agent.CpuUsage = cpu
            agent.RamUsage = ram
            agent.NetUp = up
            agent.NetDown = down
            // 局部刷新保持不变，这是高效的写法
            notifyItemChanged(index, "UPDATE_VALS")
        } else {
            val newAgent = AgentInfo(uniqueId, "新设备(${uniqueId.take(4)})", "Unknown", "Online", cpu, ram, up, down)
            agentList.add(newAgent)
            notifyItemInserted(agentList.size - 1)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_agent, parent, false)
        return AgentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AgentViewHolder, position: Int) {
        val agent = agentList[position]

        holder.tvAlias.text = agent.Alias
        holder.tvStatus.text = agent.Status

        val onlineColor = "#4CAF50".toColorInt()
        holder.tvStatus.setTextColor(if (agent.Status == "Online") onlineColor else Color.GRAY)

        updateStatsOnly(holder, agent)

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(agent)
        }
    }

    override fun getItemCount(): Int = agentList.size

    override fun onBindViewHolder(holder: AgentViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("UPDATE_VALS")) {
            updateStatsOnly(holder, agentList[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun updateStatsOnly(holder: AgentViewHolder, agent: AgentInfo) {
        val res = holder.itemView.context.resources

        // 使用 getString 自动处理 Locale（如不同国家的数字格式）和占位符
        holder.tvCpu.text = res.getString(R.string.agent_cpu_format, agent.CpuUsage)
        holder.tvRam.text = res.getString(R.string.agent_ram_format, agent.RamUsage)
        holder.tvNetUp.text = res.getString(R.string.agent_net_up, agent.NetUp)
        holder.tvNetDown.text = res.getString(R.string.agent_net_down, agent.NetDown)
    }

    class AgentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAlias: TextView = view.findViewById(R.id.tv_alias)
        val tvStatus: TextView = view.findViewById(R.id.tv_status)
        val tvCpu: TextView = view.findViewById(R.id.tv_cpu)
        val tvRam: TextView = view.findViewById(R.id.tv_ram)
        val tvNetUp: TextView = view.findViewById(R.id.tv_net_up)
        val tvNetDown: TextView = view.findViewById(R.id.tv_net_down)
    }

    class AgentDiffCallback(private val oldList: List<AgentInfo>, private val newList: List<AgentInfo>) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].UniqueId == newList[newItemPosition].UniqueId
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}