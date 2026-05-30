<template>
  <view class="page">
    <view class="header">
      <text class="back" @tap="goBack">← 返回</text>
      <text class="title">订单详情</text>
      <view style="width:40px"></view>
    </view>

    <view v-if="order" class="content">
      <!-- 状态卡片 -->
      <view class="status-card" :class="'bg-' + order.status">
        <text class="status-icon">{{ statusIcon }}</text>
        <view>
          <text class="status-title">{{ orderStatusText }}</text>
          <text class="status-desc">{{ orderStatusDesc }}</text>
        </view>
      </view>

      <!-- 桌号与单号 -->
      <view class="info-section">
        <view class="info-row"><text>桌号</text><text>{{ order.tableNo }}</text></view>
        <view class="info-row"><text>订单编号</text><text>{{ order.orderNo }}</text></view>
        <view class="info-row"><text>下单时间</text><text>{{ formatTime(order.createdAt) }}</text></view>
      </view>

      <!-- 菜品明细 -->
      <view class="items-section">
        <text class="section-title">菜品明细</text>
        <view v-for="(item, i) in parsedItems" :key="i" class="item-row">
          <view class="item-left">
            <text class="item-name">{{ item.name }}</text>
            <text v-if="item.spec" class="item-spec">{{ item.spec }}</text>
          </view>
          <text class="item-qty">x{{ item.quantity }}</text>
          <text class="item-subtotal">¥{{ (item.subtotal / 100).toFixed(2) }}</text>
        </view>
        <view class="total-row">
          <text>合计</text>
          <text class="total-price">¥{{ (order.totalPrice / 100).toFixed(2) }}</text>
        </view>
      </view>

      <!-- 取餐确认（待确认状态时显示） -->
      <view v-if="order.status === 'PENDING_CONFIRM'" class="confirm-section">
        <text class="section-title">取餐确认</text>
        <text class="confirm-hint">请勾选您实际取走的菜品：</text>
        <view v-for="(item, i) in parsedItems" :key="i" class="confirm-item" @tap="toggleConfirm(item.name)">
          <view class="checkbox" :class="{ checked: confirmedItems.includes(item.name) }">
            <text v-if="confirmedItems.includes(item.name)">✓</text>
          </view>
          <text class="confirm-item-name">{{ item.name }}</text>
          <text class="confirm-item-qty">x{{ item.quantity }}</text>
        </view>
        <view class="confirm-btn" @tap="submitConfirm" :class="{ disabled: confirmedItems.length === 0 }">
          确认取餐（{{ confirmedItems.length }}/{{ parsedItems.length }}）
        </view>
      </view>
    </view>

    <view v-else class="loading">
      <text>加载中...</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { orderApi } from '../../api/index.js'

const order = ref(null)
const confirmedItems = ref([])

const parsedItems = computed(() => {
  if (!order.value) return []
  try { return JSON.parse(order.value.items) }
  catch { return [] }
})

onLoad(function(options) {
  if (options && options.orderNo) {
    orderApi.get(options.orderNo).then(function(data) {
      order.value = data
    }).catch(function() {
      uni.showToast({ title: '加载订单失败', icon: 'none' })
    })
  }
})

const statusIcon = computed(function() {
  var map = {
    CREATED: '📋', PREPARING: '👨‍🍳', PENDING_CONFIRM: '🔔',
    CONFIRMED: '✅', CLOSED: '✔', VERIFIED: '✅'
  }
  return map[order.value ? order.value.status : null] || '📋'
})

const orderStatusText = computed(function() {
  if (!order.value) return ''
  var map = {
    CREATED: '已提交', PREPARING: '备餐中', PENDING_CONFIRM: '待取餐确认',
    CONFIRMED: '已确认', CLOSED: '已完成', VERIFIED: '已核销'
  }
  return map[order.value.status] || order.value.status
})

const orderStatusDesc = computed(function() {
  if (!order.value) return ''
  var map = {
    CREATED: '订单已提交，等待商家处理',
    PREPARING: '商家正在为您备餐',
    PENDING_CONFIRM: '请确认您取走的菜品',
    CONFIRMED: '取餐已确认，祝您用餐愉快',
    CLOSED: '订单已完成',
    VERIFIED: '订单已核销'
  }
  return map[order.value.status] || ''
})

function toggleConfirm(name) {
  const idx = confirmedItems.value.indexOf(name)
  if (idx > -1) confirmedItems.value.splice(idx, 1)
  else confirmedItems.value.push(name)
}

function submitConfirm() {
  if (confirmedItems.value.length === 0) return
  orderApi.confirmPickup({
    orderNo: order.value.orderNo,
    confirmedItemNames: confirmedItems.value
  }).then(function() {
    order.value.status = 'CONFIRMED'
    uni.showToast({ title: '取餐确认成功', icon: 'success' })
  }).catch(function(e) {
    uni.showToast({ title: (e && e.message) || '提交失败', icon: 'none' })
  })
}

function goBack() {
  uni.navigateBack()
}

function formatTime(t) {
  if (!t) return ''
  return t.slice(0, 19).replace('T', ' ')
}
</script>

<style>
.page { min-height: 100vh; background: #F5F5F5; }

.header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #FFF;
}
.back { font-size: 20px; }
.title { font-size: 17px; font-weight: bold; }

.content { padding: 12px; }

.status-card {
  display: flex; align-items: center; gap: 12px;
  padding: 16px; border-radius: 10px; margin-bottom: 12px;
  background: #FFF;
}
.status-icon { font-size: 28px; }
.status-title { font-size: 16px; font-weight: bold; display: block; }
.status-desc { font-size: 13px; color: #666; margin-top: 4px; display: block; }

.info-section {
  background: #FFF; border-radius: 10px; padding: 14px; margin-bottom: 12px;
}
.info-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; color: #333; }
.info-row text:first-child { color: #999; }

.items-section {
  background: #FFF; border-radius: 10px; padding: 14px; margin-bottom: 12px;
}
.section-title { font-size: 15px; font-weight: bold; display: block; margin-bottom: 10px; }
.item-row { display: flex; align-items: center; padding: 8px 0; border-bottom: 1px solid #F5F5F5; }
.item-left { flex: 1; }
.item-name { font-size: 14px; color: #333; }
.item-spec { font-size: 12px; color: #999; margin-left: 6px; }
.item-qty { font-size: 14px; color: #666; margin: 0 12px; min-width: 24px; }
.item-subtotal { font-size: 14px; color: #333; min-width: 60px; text-align: right; }
.total-row { display: flex; justify-content: space-between; padding: 10px 0 0; font-size: 14px; }
.total-price { font-size: 18px; font-weight: bold; color: #FF6B35; }

.confirm-section { background: #FFF; border-radius: 10px; padding: 14px; }
.confirm-hint { font-size: 13px; color: #666; margin: 6px 0 10px; display: block; }
.confirm-item {
  display: flex; align-items: center; padding: 10px 0; border-bottom: 1px solid #F5F5F5;
}
.checkbox {
  width: 20px; height: 20px; border: 2px solid #CCC; border-radius: 4px;
  display: flex; align-items: center; justify-content: center; margin-right: 10px;
  font-size: 12px; color: #FFF;
}
.checkbox.checked { background: #FF6B35; border-color: #FF6B35; }
.confirm-item-name { flex: 1; font-size: 14px; }
.confirm-item-qty { font-size: 13px; color: #999; }

.confirm-btn {
  margin-top: 14px;
  background: #FF6B35; color: #FFF; text-align: center;
  padding: 12px; border-radius: 22px; font-size: 15px; font-weight: bold;
}
.confirm-btn.disabled { background: #CCC; }

.loading { padding: 80px 0; text-align: center; color: #999; }
</style>
