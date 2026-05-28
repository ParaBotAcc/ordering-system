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
</script>

<style>
page {
  background-color: #F5F5F5;
}
</style>
