const BASE_URL = 'http://localhost:8080/api'

function request(path, options = {}) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}${path}`,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...options.headers
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          reject({ code: res.statusCode, message: res.data?.message || '请求失败' })
        }
      },
      fail: (err) => {
        reject({ code: -1, message: '网络异常，请检查网络连接' })
      }
    })
  })
}

export const menuApi = {
  getAll: () => request('/menu'),
  search: (keyword) => request('/menu/search', { data: { keyword } })
}

export const orderApi = {
  create: (data) => request('/order', { method: 'POST', data }),
  get: (orderNo) => request(`/order/${orderNo}`),
  getByTable: (tableNo) => request(`/order/table/${tableNo}`),
  listAll: () => request('/order/list'),
  updateStatus: (orderNo, status) => request(`/order/${orderNo}/status`, {
    method: 'PUT', data: { status }
  }),
  confirmPickup: (data) => request('/order/confirm', { method: 'POST', data }),
  merge: (orderNos) => request('/order/merge', { method: 'POST', data: orderNos })
}

export const API = {
  getMenu: () => request('/menu'),
  searchMenu: (keyword) => request('/menu/search', { data: { keyword } }),
  createOrder: (data) => request('/order', { method: 'POST', data }),
  getOrder: (orderNo) => request(`/order/${orderNo}`),
  getOrdersByTable: (tableNo) => request(`/order/table/${tableNo}`),
  confirmOrder: (data) => request('/order/confirm', { method: 'POST', data })
}
