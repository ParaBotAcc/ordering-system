<template>
  <view>
    <image src="/static/tab-menu.png" mode="aspectFit"
           style="position:fixed;top:-9999px;opacity:0;width:1px;height:1px"/>
    <image src="/static/tab-menu-active.png" mode="aspectFit"
           style="position:fixed;top:-9999px;opacity:0;width:1px;height:1px"/>
    <image src="/static/tab-order.png" mode="aspectFit"
           style="position:fixed;top:-9999px;opacity:0;width:1px;height:1px"/>
    <image src="/static/tab-order-active.png" mode="aspectFit"
           style="position:fixed;top:-9999px;opacity:0;width:1px;height:1px"/>
    <scroll-view scroll-y style="height: 100vh;">
      <slot />
    </scroll-view>
  </view>
</template>

<script setup>
import { onLaunch, onHide } from '@dcloudio/uni-app'
import { connectWs, disconnectWs } from '@/api/websocket.js'

onLaunch(function() {
  // 应用启动后建立全局 WS 长连接
  connectWs()
})

onHide(function() {
  // 切到后台时断开（小程序规范）
  disconnectWs()
})
</script>

<style>
page {
  background-color: #F5F5F5;
}
</style>
