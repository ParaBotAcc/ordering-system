/**
 * 全局 WebSocket 连接管理器
 * - 应用启动时自动建立长连接
 * - 断线自动重连（指数退避）
 * - 收到推送后通过 uni.$emit 派发给各页面
 */

let wsTask = null
let reconnectTimer = null
let reconnectAttempts = 0
const MAX_RECONNECT_DELAY = 30000 // 最大重连间隔 30s

function getWsUrl() {
  // #ifdef H5
  var host = window.location.host
  // #endif
  // #ifdef MP-WEIXIN
  var host = 'localhost:8080'
  // #endif
  return 'ws://' + (host || 'localhost:8080') + '/ws/order'
}

function getReconnectDelay() {
  // 指数退避：1s, 2s, 4s, 8s, ... 最大 30s
  var delay = Math.min(1000 * Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY)
  reconnectAttempts++
  return delay
}

export function connectWs() {
  // 避免重复连接
  if (wsTask) {
    try { wsTask.close() } catch(e) {}
    wsTask = null
  }

  var url = getWsUrl()
  console.log('[WS] 开始连接:', url)

  try {
    wsTask = uni.connectSocket({ url: url })

    wsTask.onOpen(function() {
      console.log('[WS] 已连接')
      reconnectAttempts = 0 // 重置重连计数
    })

    wsTask.onError(function(err) {
      console.warn('[WS] 连接错误:', JSON.stringify(err))
      scheduleReconnect()
    })

    wsTask.onMessage(function(res) {
      try {
        var data = JSON.parse(res.data)
        if (data.orderNo && data.status) {
          console.log('[WS] 收到推送:', data.orderNo, '->', data.status)
          // 全局事件派发，各页面监听
          uni.$emit('orderStatusChanged', data)
        }
      } catch(e) {
        console.warn('[WS] 消息解析失败:', res.data)
      }
    })

    wsTask.onClose(function() {
      console.log('[WS] 连接关闭')
      wsTask = null
      scheduleReconnect()
    })

  } catch(e) {
    console.error('[WS] 连接异常:', e.message)
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return // 已有重连计划
  var delay = getReconnectDelay()
  console.log('[WS] 计划重连 (第' + reconnectAttempts + '次, ' + delay + 'ms后)')
  reconnectTimer = setTimeout(function() {
    reconnectTimer = null
    connectWs()
  }, delay)
}

export function disconnectWs() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (wsTask) {
    try { wsTask.close() } catch(e) {}
    wsTask = null
  }
  reconnectAttempts = 0
  console.log('[WS] 已断开')
}
