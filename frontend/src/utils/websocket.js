const WS_URL = 'ws://localhost:8081/ws'

const MESSAGE_TYPES = {
  AUTH: 'auth',
  MESSAGE: 'message',
  ACK: 'ack',
  HEARTBEAT: 'heartbeat',
  SYSTEM: 'system'
}

class WebSocketService {
  constructor() {
    this.ws = null
    this.isConnected = false
    this.reconnectTimer = null
    this.heartbeatTimer = null
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectInterval = 3000
    this.heartbeatInterval = 30000
    this.messageHandlers = []
    this.token = null
  }

  connect(token) {
    if (this.isConnected && this.ws && this.ws.readyState === WebSocket.OPEN) {
      return
    }

    this.token = token
    
    try {
      this.ws = new WebSocket(WS_URL)
      
      this.ws.onopen = () => {
        console.log('WebSocket connected')
        this.isConnected = true
        this.reconnectAttempts = 0
        
        this.sendAuth()
        this.startHeartbeat()
        this.notifyHandlers({ type: 'connected' })
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handleMessage(data)
        } catch (error) {
          console.error('Failed to parse message:', error)
        }
      }

      this.ws.onclose = () => {
        console.log('WebSocket disconnected')
        this.isConnected = false
        this.stopHeartbeat()
        this.notifyHandlers({ type: 'disconnected' })
        this.tryReconnect()
      }

      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error)
        this.notifyHandlers({ type: 'error', error })
      }
    } catch (error) {
      console.error('Failed to create WebSocket:', error)
      this.tryReconnect()
    }
  }

  sendAuth() {
    if (this.ws && this.token) {
      this.send({
        type: MESSAGE_TYPES.AUTH,
        token: this.token
      })
    }
  }

  sendMessage(message) {
    if (this.ws && this.isConnected) {
      this.send({
        type: MESSAGE_TYPES.MESSAGE,
        ...message
      })
    } else {
      console.warn('WebSocket is not connected')
    }
  }

  sendAck(msgId) {
    if (this.ws && this.isConnected) {
      this.send({
        type: MESSAGE_TYPES.ACK,
        msgId: msgId
      })
    }
  }

  sendHeartbeat() {
    if (this.ws && this.isConnected) {
      this.send({
        type: MESSAGE_TYPES.HEARTBEAT
      })
    }
  }

  send(data) {
    try {
      this.ws.send(JSON.stringify(data))
    } catch (error) {
      console.error('Failed to send message:', error)
    }
  }

  handleMessage(data) {
    switch (data.type) {
      case MESSAGE_TYPES.MESSAGE:
        // 先通知上层，再发送ACK，确保消息被处理
        this.notifyHandlers(data)
        // 仅在消息包含msgId时发送ACK
        if (data.msgId) {
          this.sendAck(data.msgId)
        }
        break
      case MESSAGE_TYPES.SYSTEM:
        this.notifyHandlers(data)
        break
      case MESSAGE_TYPES.ACK:
        this.notifyHandlers(data)
        break
      default:
        this.notifyHandlers(data)
    }
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat()
    }, this.heartbeatInterval)
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  tryReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.log('Max reconnect attempts reached')
      return
    }

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
    }

    this.reconnectAttempts++
    this.reconnectTimer = setTimeout(() => {
      console.log(`Reconnecting... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)
      if (this.token) {
        this.connect(this.token)
      }
    }, this.reconnectInterval)
  }

  onMessage(handler) {
    this.messageHandlers.push(handler)
  }

  offMessage(handler) {
    const index = this.messageHandlers.indexOf(handler)
    if (index > -1) {
      this.messageHandlers.splice(index, 1)
    }
  }

  notifyHandlers(data) {
    this.messageHandlers.forEach(handler => {
      try {
        handler(data)
      } catch (error) {
        console.error('Error in message handler:', error)
      }
    })
  }

  disconnect() {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.isConnected = false
    this.token = null
  }

  ensureConnected(token) {
    if (!token) return
    if (this.isConnected && this.ws && this.ws.readyState === WebSocket.OPEN) {
      return
    }
    this.connect(token)
  }
}

export const wsService = new WebSocketService()
export default wsService
