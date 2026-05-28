<template>
  <view class="page">
    <view class="header">
      <text class="title">我的订单</text>
    </view>

    <view v-if="orders.length === 0" class="empty">
      <text>暂无订单</text>
      <text class="empty-hint">去点餐页选购吧</text>
    </view>

    <scroll-view scroll-y class="order-list" v-else>
      <view
        v-for="order in orders"
        :key="order.orderNo"
        class="order-card"
        @tap="goDetail(order.orderNo)"
      >
        <view class="order-header">
          <text class="order-no">#{{ order.orderNo.slice(-8) }}</text>
          <text class="order-status" :class="'status-' + order.status">{{ statusText(order.status) }}</text>
        </view>
        <view class="order-table">
          <text>桌号：{{ order.tableNo }}</text>
        </view>
        <view class="order-items">
          <text class="order-items-text">{{ orderItemsPreview(order.items) }}</text>
        </view>
        <view class="order-footer">
          <text class="order-total">¥{{ (order.totalPrice / 100).toFixed(2) }}</text>
          <text class="order-time">{{ formatTime(order.createdAt) }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { orderApi } from '../../api/index.js'

const orders = ref([])

onShow(() => {
  loadOrders()
})

async function loadOrders() {
  try {
    orders.value = await orderApi.listAll()
  } catch (e) {
    console.error('加载订单失败', e)
  }
}

function statusText(status) {
  const map = {
    CREATED: '已提交',
    PREPARING: '备餐中',
    PENDING_CONFIRM: '待确认',
    CONFIRMED: '已确认',
    CLOSED: '已完成',
    VERIFIED: '已核销'
  }
  return map[status] || status
}

function orderItemsPreview(itemsStr) {
  try {
    const items = JSON.parse(itemsStr)
    return items.map(i => `${i.name}x${i.quantity}`).join('、')
  } catch { return itemsStr }
}

function formatTime(t) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

function goDetail(orderNo) {
  uni.navigateTo({ url: `/pages/order-detail/index?orderNo=${orderNo}` })
}
</script>

<style>
.page { min-height: 100vh; background: #F5F5F5; }
.header { padding: 12px 16px; background: #FFF; }
.title { font-size: 18px; font-weight: bold; }

.empty { padding: 80px 0; text-align: center; color: #999; font-size: 15px; }
.empty-hint { display: block; margin-top: 8px; font-size: 13px; color: #CCC; }

.order-list { padding: 8px 12px; }
.order-card {
  background: #FFF;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.order-header { display: flex; justify-content: space-between; align-items: center; }
.order-no { font-size: 14px; color: #666; }
.order-status { font-size: 13px; padding: 2px 8px; border-radius: 4px; }
.status-CREATED { color: #FF6B35; background: #FFF5F0; }
.status-PREPARING { color: #1890FF; background: #F0F8FF; }
.status-PENDING_CONFIRM { color: #FAAD14; background: #FFFBE6; }
.status-CONFIRMED { color: #52C41A; background: #F6FFED; }
.status-CLOSED { color: #999; background: #F5F5F5; }

.order-table { margin-top: 6px; font-size: 13px; color: #333; }
.order-items { margin-top: 4px; }
.order-items-text { font-size: 13px; color: #666; }
.order-footer { display: flex; justify-content: space-between; margin-top: 10px; padding-top: 8px; border-top: 1px solid #F0F0F0; }
.order-total { font-size: 16px; font-weight: bold; color: #FF6B35; }
.order-time { font-size: 12px; color: #999; }
</style>
