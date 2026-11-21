# 青程点菜 1.0 接口速查

适用于 `feature/android-ui-redesign` 分支的双人模式。原版使用 Android 客户端。本文的字段名按后端 Controller 和 DTO 核对。

## 请求与返回

- 本机预览用 `http://127.0.0.1:18081`，开发者测试用 `http://127.0.0.1:18082`。请求里的路径都相对服务地址。
- 登录和版本检查无需 token。其余业务请求在请求头带 `Authorization: Bearer <token>`。有 JSON 请求体时带 `Content-Type: application/json`。上传使用 `multipart/form-data`。
- 两个模式的 token 各自使用。双人模式关闭注册和旧版 `/api/admin/**` 接口。当前后端源码未配置 CORS，另起网页时使用同源代理接 API。
- 表中的返回字段均位于 `data` 内。标为可选的字段可能省略。日期和时间是字符串，金额是 JSON 数字，展示订单编号是字符串。

```json
{"success":true,"message":"ok","data":{}}
```

失败使用 HTTP 状态码，JSON 形状仍是 `{success,message,data}`。前端可显示 `message`。常见状态包括 `401` 会话失效、`403` 权限不足、`409` 状态冲突。

## 登录与账号

| 请求 | 参数 | `data` |
| --- | --- | --- |
| `POST /api/auth/login` | `{username,password}`，登录时无需 token | `{token,user}` |
| `GET /api/auth/me` | 无 | `user` |
| `POST /api/auth/logout` | 无 | `null` |
| `POST /api/auth/nickname` | `{nickname}` | 更新后的 `user` |
| `POST /api/auth/password` | `{oldPassword,newPassword}` | `null`，修改成功后重新登录 |

`user` 包含 `id,username,nickname,phone,role,enabled,qingchengCoins,lastCheckInDate,createdAt,lastLoginAt`。双人模式还返回 `couple:{mode,identity,canBuyMenu,canBuyShop}`。`mode` 为 `FORMAL` 或 `TEST`，`identity` 为 `JIUGE`、`BAOBAO` 或测试开发者 `DEVELOPER`。

## 菜单和商店商品

| 请求 | 参数 | `data` |
| --- | --- | --- |
| `GET /api/menu-items` | 无 | 上架菜单商品数组 |
| `GET /api/menu-items/manage` | 无 | 菜单商品数组，含下架商品 |
| `GET /api/shop-items` | 无 | 宝宝商店商品数组 |
| `POST /api/menu-items` 或 `POST /api/shop-items` | 商品请求体 | 新商品 |
| `PUT /api/menu-items/{id}` 或 `PUT /api/shop-items/{id}` | 商品请求体 | 更新后的商品 |
| `DELETE /api/menu-items/{id}` 或 `DELETE /api/shop-items/{id}` | 路径商品 `id` | `null` |
| `POST /api/uploads` | 表单文件字段 `file` | `{url}` |

商品请求体字段为 `name,description,imageUrl,category,price`，其中 `name`、`price` 必填。菜单商品另接受 `available`。返回在这些字段之外还带 `id,createdAt`。向猪猪商店下单使用菜单接口，商品分类为「猪猪商店」。

## 下单与处理

以下创建请求都需要 `Idempotency-Key`。同一次提交重试用同一个 key。状态操作使用原订单 `id`。

| 请求 | 参数 | `data` |
| --- | --- | --- |
| `POST /api/orders` | `{items:[{menuItemId,quantity}],remark?}` | 菜单订单 |
| `GET /api/orders/me`、`GET /api/orders/received` | 无 | 我下的、我处理的菜单订单数组 |
| `PUT /api/orders/{id}/status` | `{action}` | 更新后的菜单订单 |
| `POST /api/shop-orders` | `{items:[{shopItemId,quantity}],remark?}` | 宝宝商店订单 |
| `GET /api/shop-orders/me`、`GET /api/shop-orders/received` | 无 | 我下的、我处理的宝宝商店订单数组 |
| `PUT /api/shop-orders/{id}/status` | `{action}` | 更新后的宝宝商店订单 |

菜单订单 `data` 示例

```json
{
  "id": 42,
  "userId": 3,
  "username": "sample",
  "nickname": "样例",
  "items": [{"menuItemId": 102,"name": "样例商品","price": 10.00,"quantity": 1,"subtotal": 10.00}],
  "totalAmount": 10.00,
  "remark": null,
  "status": "PENDING",
  "createdAt": "2026-11-21T09:12:00",
  "updatedAt": "2026-11-21T09:12:00",
  "handlerUserId": 1,
  "orderKind": "PIGGY_SHOP",
  "allowedActions": ["CANCEL"],
  "displayNumber": "001"
}
```

宝宝商店订单还返回 `totalQuantity`。它的明细使用 `shopItemId,unitPrice`，其余明细字段相同。`orderKind` 可为 `MENU`、`PIGGY_SHOP`、`BABY_SHOP`、`MIXED_MENU` 或 `LEGACY_MENU`。`handlerUserId`、`orderKind`、`allowedActions`、`displayNumber` 都可能省略。界面按 `allowedActions` 显示操作，操作值为 `ACCEPT`、`COMPLETE`、`CANCEL`。状态为 `PENDING`、`PREPARING`、`COMPLETED`、`CANCELLED`。

## 钱包与电子猪猪

| 请求 | 参数 | `data` |
| --- | --- | --- |
| `GET /api/wallet` | 无 | 钱包 |
| `POST /api/wallet/check-in` | 无 | 签到后的钱包 |
| `GET /api/piggy` | 查询参数 `deviceId`，UUID | 猪德 |
| `POST /api/piggy/sync` | `{deviceId,revision,taps,legacyCount?}` | 同步后的猪德 |
| `POST /api/piggy/reset` | `{deviceId,revision}` | 归零结果 |

钱包字段为 `userId,qingchengCoins,lastCheckInDate,checkedInToday,dailyReward,nextReward,currentStreak,currentDate,currentDateLabel,currentMonthLabel,weekCheckInDates,checkInWeek,monthCalendar,transactions`。流水项使用 `{id,type,amount,balanceAfter,title,detail,referenceId,createdAt}`。签到周项使用 `{date,weekday,checked}`，月项还带 `dayOfMonth,currentMonth,today`。

猪德返回结构为 `{"count":120,"revision":0,"deviceTaps":2,"legacyImportClosed":true}`。`taps` 是该设备在当前 `revision` 下的累计新增次数。旧次数可通过 `legacyCount` 首次导入。客户端使用响应中的 `revision` 判断归零后是否进入新一轮。

## 双人聊天

| 请求 | 参数 | `data` |
| --- | --- | --- |
| `GET /api/friends` | 无 | `{friends,incomingRequests,outgoingRequests,unreadCount}` |
| `GET /api/friends/{userId}/messages` | 对方 `userId`，可选 `limit`（1–200，默认 80） | 消息数组 |
| `POST /api/friends/{userId}/messages` | `{content,replyToMessageId?}` | 已发送的消息 |
| `POST /api/friends/{userId}/messages/image` | 表单文件字段 `file` | 已发送的图片消息 |
| `POST /api/friends/{userId}/messages/sticker/{stickerId}` | 路径 ID，无请求体 | 已发送的表情消息 |
| `GET /api/friends/media/stickers` | 无 | `[{id,imageUrl,createdAt}]` |
| `POST /api/friends/media/stickers` | 表单文件字段 `file` | `{id,imageUrl,createdAt}` |
| `GET /api/friends/media/messages/{messageId}/content` 或 `GET /api/friends/media/stickers/{stickerId}/content` | 媒体所属 ID，带 token | 图片字节 |
| `POST /api/friends/{userId}/read` 或 `DELETE /api/friends/{userId}/messages` | 对方 `userId` | `null` |

`friends` 的每一项包含 `friendshipId,user,unreadCount,lastMessage,lastMessageAt,friendsSince`，其中 `user:{id,username,nickname,role}`。好友请求项包含 `friendshipId,user,direction,status,createdAt,updatedAt`。消息包含 `id,senderId,receiverId,content,type,mediaUrl,replyToMessageId,replyToSenderId,replyToType,replyToPreview,createdAt,readAt,mine`。`type` 为 `TEXT`、`IMAGE` 或 `STICKER`。聊天媒体使用返回的 `mediaUrl` 或 `imageUrl`。

## 纪念日与情侣守则

| 请求 | 参数 | `data` |
| --- | --- | --- |
| `GET /api/anniversaries` 或 `GET /api/anniversaries/{id}` | 列表无参数，详情用 `id` | 纪念日数组或单项 |
| `POST /api/anniversaries` 或 `PUT /api/anniversaries/{id}` | `{title,date,note?,repeatYearly?,type?}` | 纪念日 |
| `DELETE /api/anniversaries/{id}` | 路径 `id` | `null` |
| `GET /api/love-rules` | 无 | 守则数组 |
| `POST /api/love-rules` 或 `PUT /api/love-rules/{id}` | `{content,sortOrder?}` | 守则 |
| `DELETE /api/love-rules/{id}` | 路径 `id` | `null` |

纪念日字段为 `id,title,date,note,repeatYearly,type,nextOccurrence,daysUntil,createdAt,updatedAt,canManage`。`date` 用 `YYYY-MM-DD`，`type` 为 `meeting`、`relationship`、`wedding`、`birthday` 或 `other`。守则字段为 `id,content,sortOrder,createdAt,updatedAt`。

## AI 对话和点单

| 请求 | 参数 | 返回 |
| --- | --- | --- |
| `GET /api/ai/conversations/page` | 可选查询 `limit`（默认 20）、`cursor`（传上一页的 `nextCursor`） | `data:{items,nextCursor,hasMore}` |
| `GET /api/ai/conversations` | 无 | 对话摘要数组 |
| `GET /api/ai/conversations/{id}/messages` | 对话 `id` | AI 消息数组 |
| `DELETE /api/ai/conversations/{id}` | 对话 `id` | `data:null` |
| `GET /api/ai/models` | 可选查询 `refresh` | `data:{defaultModel,models:[{id}]}` |
| `POST /api/ai/responses` | 比如 `{"input":"你好","conversationId":12}`。新对话省略 `conversationId`，可选 `model` | `data` 中的 `conversationId,savedMessages`，及生成结果 `output_text` 或 `output` |
| `POST /api/ai/responses/stream` | 同上 | `text/event-stream`。字块事件 `{type:"response.output_text.delta",delta}`，完成事件 `{type:"response.completed",response}`，错误事件 `{type:"error",message}` |
| `POST /api/ai/ordering/messages` | `{message,conversationId?}` | `data:{status,output_text,model,conversationId,savedMessages,ordering}` |
| `POST /api/ai/ordering/confirm` | `{quoteToken}`，用户确认草稿后提交 | `data:{order,balance}` |

对话摘要字段为 `id,title,model,messageCount,createdAt,updatedAt`。AI 消息字段为 `id,conversationId,role,content,model,imageCount,createdAt`。`ordering.kind` 为 `menu` 时，`items` 是 `[{id,name,price}]`。为 `draft` 时，草稿带 `items,totalAmount,balance,remark,expiresAt,quoteToken`，明细为 `{menuItemId,name,price,quantity}`。为 `orders` 时，`items` 是菜单订单数组。为 `answer` 时展示 `output_text`。

## 更新、天气和推送

| 请求 | 参数 | 返回 |
| --- | --- | --- |
| `GET /app-version.json` | 无 | 裸 JSON `{versionCode,versionName,apkUrl,forceUpdate,notes}` |
| `GET /api/weather` | 可选查询 `query` | `data:{query,locationId,locationName,weather,temperature,feelsLike,humidity,windDirection,windScale,windSpeed,precipitation,pressure,visibility,observationTime,updatedAt}` |
| `GET /api/push/status` | 无 | `data:{provider,configured}` |
| `POST /api/push/devices` | `{registrationId,provider?,platform?}` | `data:{provider,configured}` |
| `POST /api/push/devices/unregister` | 同上 | `data:null` |
| `GET /api/capabilities` | 无 | `data:[{id,name,description,category,route,executable,inputSchema}]` |

开发者测试接口使用开发者 token。`POST /api/test/actor` 传 `{member}`（`JIUGE` 或 `BAOBAO`），返回 `{token,user}`。`POST /api/test/reset` 重置测试订单和钱包，无请求体。`POST /api/test/wallet` 传 `{member,balance}`，两者都返回 `data:null`。离线联调会屏蔽 AI 和天气。
