# WeLink Bug 修复报告

## 1. 概述

本文档记录了 WeLink 即时通讯系统前后端代码审查中发现的 Bug 及其修复方案。

**审查日期：** 2026-05-07

**审查范围：**
- 后端 Java 代码（Spring Boot + Netty WebSocket）
- 前端 Vue 3 代码（Element Plus + Pinia）

---

## 2. 后端 Bug 修复

### 2.1 IMService - Channel 到 UserId 映射性能问题

**严重程度：** 高

**问题描述：**

在 [IMService.java](file:///c:/Users/epsilon/Desktop/项目/分布式项目/WeLink/WeLink/src/main/java/com/epsilon/welink/im/service/IMService.java#L214-L220) 中，`getUserIdByChannel()` 方法使用遍历 `onlineUsers` Map 的方式查找 Channel 对应的 UserId。当在线用户数量较大时，每次处理消息、心跳或断开连接都需要 O(n) 的时间复杂度，严重影响性能。

**原始代码：**

```java
private Long getUserIdByChannel(Channel channel) {
    for (Map.Entry<Long, Channel> entry : onlineUsers.entrySet()) {
        if (entry.getValue() == channel) {
            return entry.getKey();
        }
    }
    return null;
}
```

**修复方案：**

添加 `channelUserMap` 反向映射，将查找时间复杂度从 O(n) 降低到 O(1)。

```java
// 新增反向映射
private final ConcurrentHashMap<Channel, Long> channelUserMap = new ConcurrentHashMap<>();

// 优化后的查找方法
private Long getUserIdByChannel(Channel channel) {
    return channelUserMap.get(channel);
}
```

**影响范围：**
- `handleAuth()` - 认证时建立双向映射
- `handleDisconnect()` - 断开时清理双向映射
- `handleHeartbeat()` - 心跳时快速查找用户
- `handleSendMessage()` - 发送消息时快速查找用户

---

### 2.2 IMService - 用户重复登录未处理旧连接

**严重程度：** 高

**问题描述：**

当用户使用新连接登录时，如果旧连接仍然在线，系统没有清理旧连接，导致：
1. 同一用户可能有两个活跃连接
2. 旧连接的心跳超时后会错误地清理新连接的路由信息
3. 消息可能被推送到已失效的旧连接

**修复方案：**

在 `handleAuth()` 方法中，认证前先检查用户是否已在线，如果已在线则关闭旧连接。

```java
private void handleAuth(ChannelHandlerContext ctx, JsonNode jsonNode) {
    // ... 验证 Token ...
    
    Long userId = jwtUtil.getUserId(token);
    
    // 如果用户已在线，先清理旧连接
    Channel oldChannel = onlineUsers.get(userId);
    if (oldChannel != null && oldChannel.isActive()) {
        channelUserMap.remove(oldChannel);
        oldChannel.close();
    }
    
    onlineUsers.put(userId, ctx.channel());
    channelUserMap.put(ctx.channel(), userId);
    
    // ... 设置 Redis 状态 ...
}
```

---

### 2.3 MessageService - markAsRead 空指针异常

**严重程度：** 中

**问题描述：**

在 [MessageService.java](file:///c:/Users/epsilon/Desktop/项目/分布式项目/WeLink/WeLink/src/main/java/com/epsilon/welink/message/service/MessageService.java#L77-L84) 中，`markAsRead()` 方法在消息不存在时会抛出 `NullPointerException`。

**原始代码：**

```java
public void markAsRead(String msgId) {
    Message message = new Message();
    message.setId(messageMapper.selectOne(
            new LambdaQueryWrapper<Message>().eq(Message::getMsgId, msgId)
    ).getId());  // 如果 selectOne 返回 null，这里会抛出 NPE
    message.setStatus(1);
    messageMapper.updateById(message);
}
```

**修复方案：**

添加空值检查，避免空指针异常。

```java
public void markAsRead(String msgId) {
    Message message = messageMapper.selectOne(
            new LambdaQueryWrapper<Message>().eq(Message::getMsgId, msgId)
    );
    if (message != null) {
        message.setStatus(1);
        messageMapper.updateById(message);
    }
}
```

---

## 3. 前端 Bug 修复

### 3.1 chat.js - 消息未读计数错误

**严重程度：** 高

**问题描述：**

在 [chat.js](file:///c:/Users/epsilon/Desktop/项目/分布式项目/WeLink/WeLink/frontend/src/stores/chat.js#L12-L29) 中，`addMessage()` 方法对所有消息都增加未读数，包括当前正在查看的会话的消息。这导致用户正在聊天时，自己发送或接收的消息也会增加未读数。

**原始代码：**

```javascript
function addMessage(message) {
  const msg = { ...message, timestamp: message.createdAt || dayjs().format('YYYY-MM-DD HH:mm:ss') }
  messages.value.push(msg)
  
  const conv = conversations.value.find(c => 
    (c.type === 'private' && c.userId === msg.fromUserId) ||
    (c.type === 'group' && c.groupId === msg.groupId)
  )
  
  if (conv) {
    conv.lastMessage = msg.content
    conv.lastTime = msg.timestamp
    conv.unread = (conv.unread || 0) + 1  // 所有消息都增加未读数
  }
}
```

**修复方案：**

判断消息是否属于当前活跃会话，只有非活跃会话的消息才增加未读数。

```javascript
function addMessage(message) {
  const msg = {
    ...message,
    timestamp: message.createdAt || message.timestamp || dayjs().format('YYYY-MM-DD HH:mm:ss')
  }
  messages.value.push(msg)
  
  if (currentConversation.value) {
    const isCurrentPrivate = currentConversation.value.type === 'private' && 
      ((msg.fromUserId === currentConversation.value.userId) || 
       (msg.toUserId === currentConversation.value.userId))
    const isCurrentGroup = currentConversation.value.type === 'group' && 
      msg.groupId === currentConversation.value.groupId
    
    if (!isCurrentPrivate && !isCurrentGroup) {
      // 非活跃会话消息，增加未读数
      const conv = conversations.value.find(c => 
        (c.type === 'private' && c.userId === (msg.fromUserId || msg.toUserId)) ||
        (c.type === 'group' && c.groupId === msg.groupId)
      )
      if (conv) {
        conv.lastMessage = msg.content
        conv.lastTime = msg.timestamp
        conv.unread = (conv.unread || 0) + 1
      }
    } else {
      // 活跃会话消息，只更新最后消息
      const conv = conversations.value.find(c => 
        (c.type === 'private' && (c.userId === msg.fromUserId || c.userId === msg.toUserId)) ||
        (c.type === 'group' && c.groupId === msg.groupId)
      )
      if (conv) {
        conv.lastMessage = msg.content
        conv.lastTime = msg.timestamp
      }
    }
  } else {
    // 没有当前会话，所有消息都增加未读数
    const conv = conversations.value.find(c => 
      (c.type === 'private' && (c.userId === msg.fromUserId || c.userId === msg.toUserId)) ||
      (c.type === 'group' && c.groupId === msg.groupId)
    )
    if (conv) {
      conv.lastMessage = msg.content
      conv.lastTime = msg.timestamp
      conv.unread = (conv.unread || 0) + 1
    }
  }
}
```

---

### 3.2 Chat.vue - 滚动到底部未等待 DOM 更新

**严重程度：** 中

**问题描述：**

在 [Chat.vue](file:///c:/Users/epsilon/Desktop/项目/分布式项目/WeLink/WeLink/frontend/src/views/Chat.vue#L124) 中，`selectConversation()` 调用 `scrollToBottom()` 时没有使用 `await`，导致在消息列表渲染完成前就尝试滚动，滚动位置不正确。

**原始代码：**

```javascript
const selectConversation = async (conv) => {
  chatStore.setCurrentConversation(conv)
  chatStore.clearMessages()
  
  if (conv.type === 'private') {
    await chatStore.loadPrivateHistory(conv.userId)
  } else if (conv.type === 'group') {
    await chatStore.loadGroupHistory(conv.groupId)
  }
  
  scrollToBottom()  // 没有 await
}
```

**修复方案：**

添加 `await` 确保 `scrollToBottom()` 在 DOM 更新后执行。

```javascript
const selectConversation = async (conv) => {
  // ...
  await scrollToBottom()  // 添加 await
}
```

---

### 3.3 Main.vue - WebSocket 消息到达时未自动滚动

**严重程度：** 中

**问题描述：**

在 [Main.vue](file:///c:/Users/epsilon/Desktop/项目/分布式项目/WeLink/WeLink/frontend/src/views/Main.vue#L68-L87) 中，WebSocket 接收到新消息时没有自动滚动到底部，用户需要手动滚动才能看到最新消息。

**修复方案：**

在 WebSocket 消息处理中添加自动滚动逻辑。

```javascript
const initWebSocket = () => {
  wsService.onMessage((data) => {
    if (data.type === 'message') {
      chatStore.addMessage(data)
      
      // 如果是当前会话的消息，自动滚动到底部
      if (chatStore.currentConversation) {
        const isCurrentPrivate = chatStore.currentConversation.type === 'private' && 
          ((data.fromUserId === chatStore.currentConversation.userId) || 
           (data.toUserId === chatStore.currentConversation.userId))
        const isCurrentGroup = chatStore.currentConversation.type === 'group' && 
          data.groupId === chatStore.currentConversation.groupId
        
        if (isCurrentPrivate || isCurrentGroup) {
          scrollToBottom()
        }
      }
    }
    // ...
  })
}

const scrollToBottom = () => {
  const messageList = document.querySelector('.message-list')
  if (messageList) {
    messageList.scrollTop = messageList.scrollHeight
  }
}
```

---

### 3.4 websocket.js - ACK 发送未检查 msgId

**严重程度：** 低

**问题描述：**

在 [websocket.js](file:///c:/Users/epsilon/Desktop/项目/分布式项目/WeLink/WeLink/frontend/src/utils/websocket.js#L114-L124) 中，收到消息时直接调用 `sendAck(data.msgId)`，如果消息不包含 `msgId` 字段，会发送无效的 ACK。

**修复方案：**

添加 `msgId` 存在性检查。

```javascript
handleMessage(data) {
  switch (data.type) {
    case MESSAGE_TYPES.MESSAGE:
      this.notifyHandlers(data)
      if (data.msgId) {  // 添加检查
        this.sendAck(data.msgId)
      }
      break
    // ...
  }
}
```

---

## 4. 修复总结

| 编号 | 模块 | 问题 | 严重程度 | 状态 |
|------|------|------|----------|------|
| 2.1 | IMService | Channel 查找性能 O(n) | 高 | 已修复 |
| 2.2 | IMService | 重复登录未清理旧连接 | 高 | 已修复 |
| 2.3 | MessageService | markAsRead 空指针异常 | 中 | 已修复 |
| 3.1 | chat.js | 未读计数错误 | 高 | 已修复 |
| 3.2 | Chat.vue | 滚动未等待 DOM 更新 | 中 | 已修复 |
| 3.3 | Main.vue | WebSocket 消息未自动滚动 | 中 | 已修复 |
| 3.4 | websocket.js | ACK 未检查 msgId | 低 | 已修复 |

---

## 5. 建议

### 5.1 后端建议

1. **添加分布式锁** - 好友申请、群组操作等并发场景需要分布式锁保护
2. **实现 Token 黑名单** - 用户登出时应将 Token 加入 Redis 黑名单
3. **优化群消息推送** - 成员数 > 100 的群应采用写扩散模式
4. **添加消息重试机制** - WebSocket 推送失败时应重试

### 5.2 前端建议

1. **添加消息本地缓存** - 使用 IndexedDB 缓存聊天记录
2. **实现图片预览** - 点击图片时支持放大预览
3. **添加输入状态提示** - 显示对方正在输入
4. **优化重连体验** - WebSocket 重连时显示连接状态提示
