/**
 * 全局 WebSocket 连接管理器
 * - 应用启动时自动建立长连接
 * - 断线自动重连（指数退避）
 * - 收到推送后通过 uni.$emit 派发给各页面
 *
 * 平台兼容：H5 用原生 WebSocket，小程序用 wx 原生 API
 */

let reconnectTimer = null
let reconnectAttempts = 0
const MAX_RECONNECT_DELAY = 30000

function getWsUrl() {
  var host = 'localhost:8080'
  // #ifdef H5
  host = window.location.host
  // #endif
  return 'ws://' + host + '/ws/order'
}

function getReconnectDelay() {
  var delay = Math.min(1000 * Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY)
  reconnectAttempts++
  return delay
}

function clearReconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return
  var delay = getReconnectDelay()
  console.log('[WS] 计划重连 (第' + reconnectAttempts + '次, ' + delay + 'ms后)')
  reconnectTimer = setTimeout(function() {
    reconnectTimer = null
    connectWs()
  }, delay)
}

// ========== 平台分叉实现 ==========

function connectWsH5(url) {
  var ws = new WebSocket(url)

  ws.onopen = function() {
    console.log('[WS] 已连接')
    reconnectAttempts = 0
  }

  ws.onerror = function(err) {
    console.warn('[WS] 连接错误:', JSON.stringify(err))
  }

  ws.onmessage = function(res) {
    try {
      var data = JSON.parse(res.data)
      if (data.orderNo && data.status) {
        console.log('[WS] 收到推送:', data.orderNo, '->', data.status)
        uni.$emit('orderStatusChanged', data)
      }
    } catch(e) {
      console.warn('[WS] 消息解析失败:', res.data)
    }
  }

  ws.onclose = function() {
    console.log('[WS] 连接关闭')
    scheduleReconnect()
  }

  return ws
}

function connectWsMP(url) {
  // 微信小程序原生 API
  wx.connectSocket({ url: url })

  wx.onSocketOpen(function() {
    console.log('[WS] 已连接')
    reconnectAttempts = 0
  })

  wx.onSocketError(function(err) {
    console.warn('[WS] 连接错误:', JSON.stringify(err))
  })

  wx.onSocketMessage(function(res) {
    try {
      var data = JSON.parse(res.data)
      if (data.orderNo && data.status) {
        console.log('[WS] 收到推送:', data.orderNo, '->', data.status)
        uni.$emit('orderStatusChanged', data)
      }
    } catch(e) {
      console.warn('[WS] 消息解析失败:', res.data)
    }
  })

  wx.onSocketClose(function() {
    console.log('[WS] 连接关闭')
    wsTask = null
    scheduleReconnect()
  })

  return true
}

// ========== 公开 API ==========

let wsTask = null
var inMP = false

export function connectWs() {
  clearReconnect()

  // 避免重复连接
  if (wsTask) {
    try {
      if (inMP) {
        wx.closeSocket()
      } else {
        wsTask.close()
      }
    } catch(e) {}
    wsTask = null
  }

  var url = getWsUrl()
  console.log('[WS] 开始连接:', url)

  // #ifdef MP-WEIXIN
  inMP = true
  connectWsMP(url)
  // #endif

  // #ifndef MP-WEIXIN
  inMP = false
  wsTask = connectWsH5(url)
  // #endif
}

export function disconnectWs() {
  clearReconnect()
  if (wsTask) {
    try {
      if (inMP) {
        wx.closeSocket()
      } else {
        wsTask.close()
      }
    } catch(e) {}
    wsTask = null
  }
  reconnectAttempts = 0
  console.log('[WS] 已断开')
}
