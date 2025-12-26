# YegnaChat Socket Protocol

This document lists **all possible JSON request/response combinations** supported by the YegnaChat socket server.

---

## 🔐 Authentication

### Signup
**Request**
```json
{ "type": "signup", "payload": { "username": "john", "password": "secret", "avatar_url": "", "bio": "" } }
```
**Response (ok)**
```json
{ "type": "signup_response", "payload": { "status": "ok" } }
```
**Response (error)**
```json
{ "type": "signup_response", "payload": { "status": "error", "message": "Username already exists" } }
```

---

### Login
**Request**
```json
{ "type": "login", "payload": { "username": "john", "password": "secret" } }
```
**Response (ok)**
```json
{ "type": "login_response", "payload": { "status": "ok", "token": "TOKEN", "user_id": 1, "preferred_language_code": "en" } }
```
**Response (error)**
```json
{ "type": "login_response", "payload": { "status": "error" } }
```

---

### Get Session
```json
{ "type": "get_session", "payload": { "token": "TOKEN" } }
```
```json
{ "type": "get_session_response", "payload": { "status": "ok", "token": "TOKEN", "user_id": 1, "preferred_language_code": "en" } }
```

---

### Logout
```json
{ "type": "logout", "payload": {} }
```
```json
{ "type": "logout_response", "payload": { "status": "ok" } }
```

---

## 👤 User Profile

### Get Current User
```json
{ "type": "get_user", "payload": {} }
```
```json
{ "type": "get_user_response", "payload": { "status": "ok", "user": { "id": 1, "username": "john", "avatar_url": "", "bio": "" } } }
```

---

### Update Bio
```json
{ "type": "set_bio", "payload": { "bio": "New bio" } }
```
```json
{ "type": "set_bio_response", "payload": { "status": "ok" } }
```

---

### Change Password
```json
{ "type": "set_password", "payload": { "old_password": "old", "new_password": "new" } }
```
```json
{ "type": "set_password_response", "payload": { "status": "ok" } }
```

---

## 💬 Messaging

### Send Private Message
```json
{ "type": "send_message", "payload": { "receiver_id": 2, "content": "Hi" } }
```
```json
{ "type": "send_message", "payload": { "chat_type": "private", "sender_id": 1, "receiver_id": 2, "content": "Hi" } }
```

---

### Send Group Message
```json
{ "type": "send_message", "payload": { "group_id": 3, "content": "Hello group" } }
```
```json
{ "type": "send_message", "payload": { "chat_type": "group", "sender_id": 1, "group_id": 3, "content": "Hello group" } }
```

---

### Fetch History
```json
{ "type": "fetch_history", "payload": { "chat_type": "private", "user_id": 2 } }
```
```json
{ "type": "fetch_history_response", "payload": { "messages": [] } }
```

---

## 👥 Groups

### Create Group
```json
{ "type": "create_group", "payload": { "name": "Study", "about": "CS", "avatar_url": "", "user_ids": [2,3] } }
```
```json
{ "type": "create_group_response", "payload": { "status": "ok", "group_id": 10 } }
```

---

### Add User to Group
```json
{ "type": "add_user_to_group", "payload": { "group_id": 10, "user_ids": [4] } }
```
```json
{ "type": "add_user_to_group_response", "payload": { "status": "ok" } }
```

---

### Remove User From Group
```json
{ "type": "remove_user_from_group", "payload": { "group_id": 10, "user_id": 4 } }
```
```json
{ "type": "remove_user_from_group_response", "payload": { "status": "ok" } }
```

---

### Promote / Demote User
```json
{ "type": "promote_demote_user", "payload": { "group_id": 10, "user_id": 4, "new_role": "admin" } }
```
```json
{ "type": "promote_demote_user_response", "payload": { "status": "ok" } }
```

---

### List Group Admins
```json
{ "type": "list_group_admins", "payload": { "group_id": 10 } }
```
```json
{ "type": "list_group_admins_response", "payload": { "admins": [] } }
```

---

## 🤝 Social

### Follow User
```json
{ "type": "follow_user", "payload": { "target_id": 2 } }
```
```json
{ "type": "follow_user_response", "payload": { "status": "ok" } }
```

---

### Unfollow User
```json
{ "type": "unfollow_user", "payload": { "target_id": 2 } }
```
```json
{ "type": "unfollow_user_response", "payload": { "status": "ok" } }
```

---

## Errors
```json
{ "type": "error", "payload": "Not authenticated" }
```
```json
{ "type": "error", "payload": "Unknown message type" }
```

---
