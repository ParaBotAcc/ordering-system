<template>
  <view class="page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input" @tap="onSearchTap">
        <text class="search-icon">🔍</text>
        <text class="search-placeholder">搜索菜品</text>
      </view>
    </view>

    <view class="main-content">
      <!-- 左侧分类导航 -->
      <scroll-view class="category-nav" scroll-y :scroll-into-view="'cat-' + activeCategory">
        <view
          v-for="cat in categories"
          :key="cat.name"
          :id="'cat-' + cat.name"
          class="category-item"
          :class="{ active: activeCategory === cat.name }"
          @tap="switchCategory(cat.name)"
        >
          <text>{{ cat.name }}</text>
          <view v-if="getCategoryCartCount(cat.name) > 0" class="cat-badge">
            {{ getCategoryCartCount(cat.name) }}
          </view>
        </view>
      </scroll-view>

      <!-- 右侧菜品列表 -->
      <scroll-view class="menu-list" scroll-y @scrolltolower="loadMore">
        <view v-for="item in filteredItems" :key="item.id" class="menu-item">
          <image class="item-image" :src="item.imageUrl || '/static/placeholder.png'" mode="aspectFill" />
          <view class="item-info">
            <text class="item-name">{{ item.name }}</text>
            <text class="item-desc">{{ item.description || item.spec || '' }}</text>
            <view class="item-bottom">
              <text class="item-price">¥{{ (item.price / 100).toFixed(2) }}</text>
              <view class="item-actions">
                <view v-if="getCartItemCount(item) > 0" class="qty-control">
                  <view class="qty-btn" @tap="decrement(item)">−</view>
                  <text class="qty-num">{{ getCartItemCount(item) }}</text>
                </view>
                <view
                  class="add-btn"
                  :class="{ soldout: !item.available }"
                  @tap="item.available ? addToCart(item) : () => {}"
                >
                  <text v-if="item.available">+</text>
                  <text v-else>售罄</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <view v-if="filteredItems.length === 0" class="empty-state">
          <text>该分类暂无菜品</text>
        </view>
      </scroll-view>
    </view>

    <!-- 购物车底栏 -->
    <view class="cart-bar" @tap="toggleDrawer" v-if="cartCount > 0">
      <view class="cart-icon-wrapper">
        <view class="cart-icon">{{ cartCount }}</view>
      </view>
      <view class="cart-info">
        <text class="cart-total">¥{{ (cartTotal / 100).toFixed(2) }}</text>
        <text class="cart-hint">另需配送费¥0</text>
      </view>
      <view class="cart-checkout" @tap.stop="goCheckout">去结算</view>
    </view>

    <view class="cart-bar cart-bar-empty" v-else>
      <view class="cart-icon-wrapper">
        <view class="cart-icon cart-icon-empty">0</view>
      </view>
      <text class="cart-empty-hint">购物车是空的</text>
    </view>

    <!-- 购物车抽屉弹窗 -->
    <view class="drawer-mask" v-if="showDrawer" @tap="toggleDrawer"></view>
    <view class="cart-drawer" :class="{ open: showDrawer }">
      <view class="drawer-header">
        <text class="drawer-title">购物车</text>
        <text class="drawer-clear" @tap="clearCart">清空</text>
      </view>
      <scroll-view class="drawer-items" scroll-y>
        <view v-for="item in cart" :key="item.id + (item.spec || '')" class="drawer-item">
          <view class="drawer-item-info">
            <text class="drawer-item-name">{{ item.name }}</text>
            <text v-if="item.spec" class="drawer-item-spec">{{ item.spec }}</text>
          </view>
          <text class="drawer-item-price">¥{{ (item.subtotal / 100).toFixed(2) }}</text>
          <view class="qty-control drawer-qty">
            <view class="qty-btn" @tap="decrement(item)">−</view>
            <text class="qty-num">{{ item.quantity }}</text>
            <view class="qty-btn" @tap="increment(item)">+</view>
          </view>
        </view>
      </scroll-view>
      <view class="drawer-footer">
        <text class="drawer-total">合计：¥{{ (cartTotal / 100).toFixed(2) }}</text>
        <view class="cart-checkout" @tap="goCheckout">提交订单</view>
      </view>
    </view>
  </view>

  <!-- 桌号输入弹窗 -->
  <view class="drawer-mask" v-if="showTableInput" @tap="cancelTableInput"></view>
  <view class="table-input-modal" v-if="showTableInput">
    <view class="table-input-header">
      <text class="table-input-title">输入桌号</text>
    </view>
    <view class="table-input-body">
      <input
        class="table-input-field"
        v-model="tableInputValue"
        placeholder="如 A01"
        focus="true"
        maxlength="10"
      />
    </view>
    <view class="table-input-footer">
      <view class="table-input-btn cancel" @tap="cancelTableInput">取消</view>
      <view class="table-input-btn confirm" @tap="confirmTableInput">确定</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { store } from '../../store/index.js'
import { menuApi, orderApi } from '../../api/index.js'

const categories = ref([])
const menuItems = ref([])
const activeCategory = ref('')
const showDrawer = ref(false)
const showTableInput = ref(false)
const tableInputValue = ref('')

const cart = computed(() => store.cart)
const cartCount = computed(() => store.cartCount)
const cartTotal = computed(() => store.cartTotal)

const filteredItems = computed(() => {
  if (!activeCategory.value) return menuItems.value
  return menuItems.value.filter(i => i.category === activeCategory.value)
})

onMounted(async () => {
  store.init()
  try {
    const data = await menuApi.getAll()
    categories.value = data.categories || []
    menuItems.value = (data.items || []).map(i => ({
      ...i,
      price: Number(i.price),
      stock: Number(i.stock)
    }))
    if (categories.value.length > 0) {
      activeCategory.value = categories.value[0].name
    }
  } catch (e) {
    uni.showToast({ title: '加载菜单失败', icon: 'none' })
  }
})

function switchCategory(name) {
  activeCategory.value = name
}

function addToCart(item) {
  store.addToCart(item)
  // 按钮动画反馈
  uni.vibrateShort({ type: 'light' })
}

function increment(item) {
  store.updateQuantity(item.id, item.spec, 1)
}

function decrement(item) {
  store.updateQuantity(item.id, item.spec, -1)
}

function getCartItemCount(item) {
  return store.getCartItemCount(item.id)
}

function getCategoryCartCount(catName) {
  return cart.value
    .filter(c => c.category === catName)
    .reduce((s, c) => s + c.quantity, 0)
}

function toggleDrawer() {
  if (cartCount.value > 0) showDrawer.value = !showDrawer.value
}

function clearCart() {
  uni.showModal({
    title: '提示',
    content: '确认清空购物车？',
    success: (res) => {
      if (res.confirm) {
        store.clearCart()
        showDrawer.value = false
      }
    }
  })
}

function onSearchTap() {
  uni.navigateTo({ url: '/pages/search/index' })
}

function loadMore() {}

function goCheckout() {
  console.log('goCheckout called')
  if (!store.tableNo) {
    showTableInput.value = true
    return
  }
  doCreateOrder(function() {
    showDrawer.value = false
    showTableInput.value = false
  })
}

function doCreateOrder(onSuccess) {
  var items = cart.value.map(function(c) {
    return { name: c.name, spec: c.spec || '', price: c.price, quantity: c.quantity, subtotal: c.subtotal }
  })
  if (items.length === 0) return
  var data = { tableNo: store.tableNo, items: items, note: '' }
  uni.request({
    url: 'http://localhost:8080/api/order',
    method: 'POST',
    data: data,
    dataType: 'json',
    header: { 'Content-Type': 'application/json' },
    success: function(res) {
      if (res.statusCode >= 200 && res.statusCode < 300 && res.data && res.data.orderNo) {
        store.clearCart()
        if (onSuccess) onSuccess()
        uni.showToast({ title: '下单成功', icon: 'success' })
        uni.navigateTo({ url: '/pages/order-detail/index?orderNo=' + res.data.orderNo })
      } else {
        uni.showToast({ title: '下单失败', icon: 'none' })
      }
    },
    fail: function() {
      uni.showToast({ title: '网络异常', icon: 'none' })
    }
  })
}

function confirmTableInput() {
  if (!tableInputValue.value.trim()) {
    uni.showToast({ title: '请输入桌号', icon: 'none' })
    return
  }
  store.setTableNo(tableInputValue.value.trim())
  showTableInput.value = false
  tableInputValue.value = ''
  // 继续下单
  submitOrder()
}

function cancelTableInput() {
  showTableInput.value = false
  tableInputValue.value = ''
}

function submitOrder() {
  doCreateOrder(function() {
    showDrawer.value = false
    showTableInput.value = false
  })
}
</script>

<style>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #F5F5F5;
}

.search-bar {
  padding: 8px 12px;
  background: #FFF;
  position: sticky;
  top: 0;
  z-index: 10;
}

.search-input {
  display: flex;
  align-items: center;
  background: #F5F5F5;
  border-radius: 16px;
  padding: 8px 14px;
}

.search-icon { margin-right: 6px; font-size: 14px; }
.search-placeholder { color: #999; font-size: 14px; }

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.category-nav {
  width: 80px;
  background: #FFF;
  flex-shrink: 0;
}

.category-item {
  padding: 14px 8px;
  text-align: center;
  font-size: 13px;
  color: #333;
  position: relative;
  border-left: 3px solid transparent;
}

.category-item.active {
  color: #FF6B35;
  background: #FFF5F0;
  border-left-color: #FF6B35;
  font-weight: bold;
}

.cat-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  background: #FF6B35;
  color: #FFF;
  font-size: 10px;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  border-radius: 8px;
}

.menu-list {
  flex: 1;
  padding: 8px;
}

.menu-item {
  display: flex;
  background: #FFF;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.item-image {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  flex-shrink: 0;
  background: #EEE;
}

.item-info {
  flex: 1;
  margin-left: 10px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.item-name {
  font-size: 15px;
  font-weight: bold;
  color: #1A1A1A;
}

.item-desc {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.item-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}

.item-price {
  font-size: 16px;
  font-weight: bold;
  color: #FF6B35;
}

.item-actions {
  display: flex;
  align-items: center;
}

.qty-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qty-btn {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid #FF6B35;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #FF6B35;
  font-weight: bold;
}

.qty-num {
  font-size: 14px;
  min-width: 16px;
  text-align: center;
}

.add-btn {
  width: 52px;
  height: 26px;
  background: #FF6B35;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #FFF;
  font-size: 16px;
  font-weight: bold;
  margin-left: 4px;
}

.add-btn.soldout {
  background: #CCC;
  font-size: 12px;
}

/* 购物车底栏 */
.cart-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 50px;
  background: #1A1A1A;
  display: flex;
  align-items: center;
  padding: 0 12px;
  z-index: 100;
}

.cart-bar-empty {
  background: #333;
}

.cart-icon-wrapper {
  position: relative;
  margin-right: 10px;
}

.cart-icon {
  width: 40px;
  height: 40px;
  background: #FF6B35;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #FFF;
  font-size: 14px;
  font-weight: bold;
  margin-top: -16px;
}

.cart-icon-empty {
  background: #666;
}

.cart-info { flex: 1; }
.cart-total { color: #FFF; font-size: 16px; font-weight: bold; }
.cart-hint { color: #999; font-size: 11px; margin-left: 6px; }
.cart-empty-hint { color: #999; font-size: 13px; }

.cart-checkout {
  background: #FF6B35;
  color: #FFF;
  padding: 10px 24px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: bold;
}

/* 购物车抽屉 */
.drawer-mask {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0,0,0,0.5);
  z-index: 99;
}

.cart-drawer {
  position: fixed;
  bottom: 50px;
  left: 0;
  right: 0;
  max-height: 50vh;
  background: #FFF;
  border-radius: 16px 16px 0 0;
  z-index: 100;
  transform: translateY(100%);
  transition: transform 0.3s ease;
}

.cart-drawer.open {
  transform: translateY(0);
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #F0F0F0;
}

.drawer-title { font-size: 16px; font-weight: bold; }
.drawer-clear { color: #999; font-size: 14px; }

.drawer-items {
  max-height: 35vh;
  padding: 0 16px;
}

.drawer-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #F5F5F5;
}

.drawer-item-info { flex: 1; }
.drawer-item-name { font-size: 14px; color: #333; }
.drawer-item-spec { font-size: 12px; color: #999; }

.drawer-item-price {
  font-size: 14px;
  color: #333;
  margin: 0 16px;
  min-width: 60px;
  text-align: right;
}

.drawer-qty { gap: 4px; }
.drawer-qty .qty-btn { width: 20px; height: 20px; font-size: 14px; }

.drawer-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid #F0F0F0;
}

.drawer-total {
  font-size: 16px;
  font-weight: bold;
  color: #FF6B35;
}

.empty-state {
  padding: 40px 0;
  text-align: center;
  color: #999;
  font-size: 14px;
}

/* 桌号输入弹窗 */
.table-input-modal {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 280px;
  background: #FFF;
  border-radius: 12px;
  z-index: 100;
  overflow: hidden;
}

.table-input-header {
  padding: 16px 16px 8px;
  text-align: center;
}

.table-input-title {
  font-size: 16px;
  font-weight: bold;
}

.table-input-body {
  padding: 12px 16px;
}

.table-input-field {
  width: 100%;
  height: 40px;
  border: 1px solid #DDD;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 15px;
  text-align: center;
  box-sizing: border-box;
}

.table-input-footer {
  display: flex;
  border-top: 1px solid #F0F0F0;
}

.table-input-btn {
  flex: 1;
  padding: 12px;
  text-align: center;
  font-size: 15px;
}

.table-input-btn.confirm {
  color: #FF6B35;
  font-weight: bold;
  border-left: 1px solid #F0F0F0;
}

.table-input-btn.cancel {
  color: #666;
}
</style>
