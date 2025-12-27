# YegnaChat Server

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

YegnaChat is a lightweight Java-based chat server supporting **private messaging**, **group messaging**, **group management**, and **user authentication**. This repository contains the server-side code for YegnaChat.

---

## Table of Contents

* [Features](#features)
* [Requirements](#requirements)
* [Setup](#setup)
* [Environment Variables](#environment-variables)
* [Running the Server](#running-the-server)
* [JSON Request Examples](#json-request-examples)
* [Database Schema](#database-schema)
* [Contributing](#contributing)

---

## Features

* User signup and login with session management
* Private messaging between users
* Group creation, adding/removing users, leaving groups
* Fetch chat history (private and group)
* List users and group members
* Prevent duplicate group members
* Ignore self-adds in group management
* Feed support: posts, likes, comments, follow/unfollow
* Language preference per user

---

## Requirements

* Java 17+
* MySQL 8+
* Maven or any Java build tool
* Git

---

## Environment Variables

Create a `.env` file in the project root with the following:

```env
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASS=root
DB_NAME=yegnachat
```

These variables will configure the server to connect to your MySQL database.

---

## Setup

1. **Clone the repository**

```bash
git clone https://github.com/SECB-GROUP7/yegnachat-server.git
cd yegnachat-server/server
```

2. **Create the database**

```sql
-- run schema.sql from resources folder
```

3. **Configure `.env`** as shown in [Environment Variables](#environment-variables).

---

## Running the Server

1. **Compile and run**:

```bash
# Using IntelliJ or your IDE: Run Main.java
# Or using command line with javac
javac -d out/production/server src/main/java/com/yegnachat/server/Main.java
java -cp out/production/server com.yegnachat.server.Main
```

2. Input the port to **run server**.

---

## JSON Request Examples

### 1. Signup
```json
{
    "type": "signup",
    "payload": {
        "username": "john",
        "password": "john123",
        "avatar_url": "",
        "bio": "Hello world"
    }
}
```

### 2. Login
```json
{
    "type": "login",
    "payload": {
        "username": "john",
        "password": "john123"
    }
}
```

### 3. Logout
```json
{
    "type": "logout",
    "payload": {}
}
```

### 4. Get Session
```json
{
    "type": "get_session",
    "payload": {
        "token": "SESSION_TOKEN"
    }
}
```

### 5. Send Message
**Private:**
```json
{
    "type": "send_message",
    "payload": {
        "receiver_id": "2",
        "content": "Hello!"
    }
}
```
**Group:**
```json
{
    "type": "send_message",
    "payload": {
        "group_id": "1",
        "content": "Hello group!"
    }
}
```

### 6. Fetch Chat History
```json
{
    "type": "fetch_history",
    "payload": {
        "chat_type": "private",
        "user_id": 2
    }
}
```
```json
{
    "type": "fetch_history",
    "payload": {
        "chat_type": "group",
        "group_id": 1
    }
}
```

### 7. User Operations
**Get yourself:**
```json
{
    "type": "get_user",
    "payload": {}
}
```
**Get another user:**
```json
{
    "type": "get_user_profile",
    "payload": {
        "user_id": 2
    }
}
```
**Search users:**
```json
{
    "type": "search_users",
    "payload": {
        "query": "john"
    }
}
```
**List users with messages/groups:**
```json
{
    "type": "list_users",
    "payload": {}
}
```
**Set bio:**
```json
{
    "type": "set_bio",
    "payload": {
        "bio": "Updated bio"
    }
}
```
**Set preferred language:**
```json
{
    "type": "set_preferred_language",
    "payload": {
        "language_code": "en"
    }
}
```
**Get preferred language:**
```json
{
    "type": "get_preferred_language",
    "payload": {}
}
```
**Change password:**
```json
{
    "type": "set_password",
    "payload": {
        "old_password": "old123",
        "new_password": "new123"
    }
}
```
**Follow user:**
```json
{
    "type": "follow_user",
    "payload": {
        "user_id": 3
    }
}
```
**Unfollow user:**
```json
{
    "type": "unfollow_user",
    "payload": {
        "user_id": 3
    }
}
```

### 8. Group Operations
**Create group:**
```json
{
    "type": "create_group",
    "payload": {
        "name": "Test Group",
        "about": "A test group",
        "avatar_url": "",
        "user_ids": [2,3]
    }
}
```
**Add user to group:**
```json
{
    "type": "add_user_to_group",
    "payload": {
        "group_id": 1,
        "user_ids": [4]
    }
}
```
**Add group member by username:**
```json
{
    "type": "add_group_member",
    "payload": {
        "group_id": 1,
        "username": "alice"
    }
}
```
**Leave group:**
```json
{
    "type": "leave_group",
    "payload": {
        "group_id": 1
    }
}
```
**List groups for user:**
```json
{
    "type": "list_groups_for_user",
    "payload": {}
}
```
**List group members:**
```json
{
    "type": "list_group_members",
    "payload": {
        "group_id": 1
    }
}
```
**Remove user from group:**
```json
{
    "type": "remove_user_from_group",
    "payload": {
        "group_id": 1,
        "user_id": 4
    }
}
```
**Promote/Demote user:**
```json
{
    "type": "promote_demote_user",
    "payload": {
        "group_id": 1,
        "user_id": 4,
        "new_role": "admin"
    }
}
```
**Update group info:**
```json
{
    "type": "update_group_info",
    "payload": {
        "group_id": 1,
        "name": "New Group Name",
        "about": "Updated about",
        "avatar_url": "new_avatar.png"
    }
}
```
**List group admins:**
```json
{
    "type": "list_group_admins",
    "payload": {
        "group_id": 1
    }
}
```
**Get group info:**
```json
{
    "type": "get_group_info",
    "payload": {
        "group_id": 1
    }
}
```

### 9. Feed Operations
**Create post:**
```json
{
    "type": "create_post",
    "payload": {
        "content": "My first post!",
        "image_url": ""
    }
}
```
**List feed posts:**
```json
{
    "type": "list_feed_posts",
    "payload": {
        "limit": 10,
        "offset": 0
    }
}
```
**Like post:**
```json
{
    "type": "like_post",
    "payload": {
        "post_id": 123
    }
}
```
**Unlike post:**
```json
{
    "type": "unlike_post",
    "payload": {
        "post_id": 123
    }
}
```
**Add comment:**
```json
{
    "type": "add_comment",
    "payload": {
        "post_id": 123,
        "content": "Nice post!"
    }
}
```
**List comments:**
```json
{
    "type": "list_comments",
    "payload": {
        "post_id": 123
    }
}
```

---

## Database Schema

Ensure your MySQL database matches the tables expected by the server. Use `resources/schema.sql`.

---

## Contributing

1. Fork the repository
2. Create a branch for your feature/fix (`git checkout -b feature/xyz`)
3. Make your changes
4. Commit (`git commit -m "feat: your message"`)
5. Push (`git push origin feature/xyz`)
6. Open a Pull Request

---

## License

MIT License © SECB-GROUP7

This README provides all you need to **run the server locally**, **test JSON messages**, and **understand group/chat rules and feed operations**.

