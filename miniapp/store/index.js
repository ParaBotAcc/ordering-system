import { reactive } from 'vue'

const TABLE_NO_KEY = 'ordering_table_no'

export const store = reactive({
  tableNo: '',
  cart: [],
  orders: [],

  init() {
    this.tableNo = uni.getStorageSync(TABLE_NO_KEY) || ''
  },

  setTableNo(no) {
    this.tableNo = no
    uni.setStorageSync(TABLE_NO_KEY, no)
  },

  addToCart(item) {
    const existing = this.cart.find(c => c.id === item.id && c.spec === item.spec)
    if (existing) {
      existing.quantity++
      existing.subtotal = existing.price * existing.quantity
    } else {
      this.cart.push({
        ...item,
        quantity: 1,
        subtotal: item.price
      })
    }
  },

  updateQuantity(itemId, spec, delta) {
    const item = this.cart.find(c => c.id === itemId && c.spec === spec)
    if (!item) return
    item.quantity = Math.max(0, item.quantity + delta)
    item.subtotal = item.price * item.quantity
    if (item.quantity === 0) {
      this.cart = this.cart.filter(c => !(c.id === itemId && c.spec === spec))
    }
  },

  removeItem(itemId, spec) {
    this.cart = this.cart.filter(c => !(c.id === itemId && c.spec === spec))
  },

  clearCart() {
    this.cart = []
  },

  get cartCount() {
    return this.cart.reduce((sum, c) => sum + c.quantity, 0)
  },

  get cartTotal() {
    return this.cart.reduce((sum, c) => sum + c.subtotal, 0)
  },

  getCartItemCount(itemId) {
    const items = this.cart.filter(c => c.id === itemId)
    return items.reduce((s, c) => s + c.quantity, 0)
  }
})
