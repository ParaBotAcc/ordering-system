<template>
  <view class="page">
    <view class="search-box">
      <view class="search-input-wrapper">
        <text class="s-icon">🔍</text>
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索菜品名称"
          confirm-type="search"
          @confirm="doSearch"
          focus
        />
        <text v-if="keyword" class="clear-btn" @tap="keyword = ''; results = []">✕</text>
      </view>
      <text class="cancel-btn" @tap="goBack">取消</text>
    </view>

    <view class="results" v-if="results.length > 0">
      <view v-for="item in results" :key="item.id" class="result-item">
        <image class="item-image" :src="item.imageUrl || '/static/placeholder.png'" mode="aspectFill" />
        <view class="item-info">
          <text class="item-name">{{ item.name }}</text>
          <text class="item-desc">{{ item.description || '' }}</text>
          <text class="item-price">¥{{ (item.price / 100).toFixed(2) }}</text>
        </view>
        <view class="add-btn-sm" @tap="addItem(item)">+</view>
      </view>
    </view>

    <view v-else-if="searched && results.length === 0" class="no-result">
      <text>未找到相关菜品</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { menuApi } from '../../api/index.js'
import { store } from '../../store/index.js'

const keyword = ref('')
const results = ref([])
const searched = ref(false)

let searchTimer = null

function doSearch() {
  if (!keyword.value.trim()) return
  searched.value = true
  menuApi.search(keyword.value.trim()).then(data => {
    results.value = data || []
  }).catch(() => {
    uni.showToast({ title: '搜索失败', icon: 'none' })
  })
}

function addItem(item) {
  store.addToCart(item)
  uni.vibrateShort({ type: 'light' })
  uni.showToast({ title: '已加入购物车', icon: 'none' })
}

function goBack() {
  uni.navigateBack()
}
</script>

<style>
.page { padding: 8px 12px; background: #F5F5F5; min-height: 100vh; }

.search-box {
  display: flex; align-items: center; gap: 8px;
  background: #FFF; padding: 8px 0;
  position: sticky; top: 0; z-index: 10;
}

.search-input-wrapper {
  flex: 1; display: flex; align-items: center;
  background: #F5F5F5; border-radius: 8px; padding: 8px 10px;
}

.s-icon { margin-right: 6px; font-size: 14px; }
.search-input { flex: 1; font-size: 14px; }
.clear-btn { color: #999; font-size: 14px; padding: 0 4px; }
.cancel-btn { color: #FF6B35; font-size: 14px; }

.result-item {
  display: flex; align-items: center;
  background: #FFF; padding: 10px; border-radius: 8px; margin-bottom: 8px;
}

.item-image {
  width: 56px; height: 56px; border-radius: 6px; background: #EEE; flex-shrink: 0;
}

.item-info { flex: 1; margin-left: 10px; }
.item-name { font-size: 15px; font-weight: bold; display: block; }
.item-desc { font-size: 12px; color: #999; display: block; margin-top: 2px; }
.item-price { font-size: 14px; color: #FF6B35; font-weight: bold; display: block; margin-top: 4px; }

.add-btn-sm {
  width: 28px; height: 28px; background: #FF6B35; color: #FFF;
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: bold; flex-shrink: 0;
}

.no-result { padding: 60px 0; text-align: center; color: #999; }
</style>
