# YegnaChat Server

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

YegnaChat is a lightweight Java-based chat server supporting **private messaging**, **group messaging**, **group management**, and **user authentication**. This repository contains the server-side code for YegnaChat.  

---

## Table of Contents

- [Features](#features)  
- [Requirements](#requirements)  
- [Setup](#setup)  
- [Environment Variables](#environment-variables)  
- [Running the Server](#running-the-server)  
- [JSON Request Examples](#json-request-examples)  
- [Database Schema](#database-schema)  
- [Contributing](#contributing)  

---

## Features

- User signup and login with session management  
- Private messaging between users  
- Group creation, adding/removing users, leaving groups  
- Fetch chat history (private and group)  
- List users and group members  
- Prevent duplicate group members  
- Ignore self-adds in group management  

---

## Requirements

- Java 17+  
- MySQL 8+  
- Maven or any Java build tool  
- Git  

---

## Setup

1. **Clone the repository**  

```bash
git clone https://github.com/SECB-GROUP7/yegnachat-server.git
cd yegnachat-server/server
```

2. **Create `.env` file** in the project root:

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=yegnachat
DB_USER=root
DB_PASS=root
```

3. **Create the database**:
```bash
run db/schema.sql from resources
```
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

### 3. Create Group

```json
{
    "type": "create_group",
    "payload": {
        "name": "My Test Group",
        "about": "This is a test group",
        "avatar_url": "",
        "user_ids": ["2", "3"]
    }
}
```

### 4. Add User to Group

```json
{
    "type": "add_user_to_group",
    "payload": {
        "group_id": "1",
        "user_ids": ["4"]
    }
}
```

### 5. Send Message

**Private message:**

```json
{
    "type": "send_message",
    "payload": {
        "receiver_id": "2",
        "content": "Hello!"
    }
}
```

**Group message:**

```json
{
    "type": "send_message",
    "payload": {
        "group_id": "1",
        "content": "Hello group!"
    }
}
```

### 6. Leave Group

```json
{
    "type": "leave_group",
    "payload": {
        "group_id": "1"
    }
}
```

### 7. List Groups for User

```json
{
    "type": "list_groups_for_user",
    "payload": {}
}
```

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

This README gives you everything you need to **get the server running locally**, **test with JSON messages**, and **understand the database and group rules**.

