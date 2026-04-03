package com.feitu.monitor

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

/**
 * “配置”菜单主页面
 */
class ConfigFragment : Fragment(R.layout.fragment_config) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🌟 功能一：管理加密参数入口
        // 对应布局中的 card_manage_crypto
        view.findViewById<View>(R.id.card_manage_crypto).setOnClickListener {
            val intent = Intent(requireContext(), CryptoParamsActivity::class.java)
            startActivity(intent)
        }

        // 此处可以继续添加其他功能的点击事件
    }

    override fun onResume() {
        super.onResume()
        // 动态更新 Toolbar 标题
        (activity as? MainActivity)?.updateToolbarTitle("系统配置")
    }
}