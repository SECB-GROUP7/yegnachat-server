package com.yegnachat.server.chat;

import java.sql.Timestamp;

public record GroupMessage(int id, int groupId, int senderId, String content, Timestamp createdAt) {
}

