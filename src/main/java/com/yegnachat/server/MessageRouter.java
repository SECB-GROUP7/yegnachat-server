package com.yegnachat.server;

import com.google.gson.Gson;
import com.yegnachat.server.auth.SessionManager;
import com.yegnachat.server.chat.GroupMessage;
import com.yegnachat.server.protocol.JsonMessage;
import com.yegnachat.server.auth.AuthService;
import com.yegnachat.server.auth.SessionInfo;
import com.yegnachat.server.chat.ChatService;
import com.yegnachat.server.user.UserService;
import com.yegnachat.server.user.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessageRouter {

    private final Gson gson = new Gson();
    private final AuthService authService;
    private final ChatService chatService;
    private final UserService userService;

    public MessageRouter(AuthService authService, ChatService chatService, UserService userService) {
        this.authService = authService;
        this.chatService = chatService;
        this.userService = userService;
    }

    public String route(String json, ClientHandler sender) {
        JsonMessage msg = gson.fromJson(json, JsonMessage.class);

        try {
            return switch (msg.getType()) {

                case "login" -> {
                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    SessionInfo s = authService.login(
                            p.get("username").toString(),
                            p.get("password").toString()
                    );

                    if (s == null) {
                        yield gson.toJson(new JsonMessage("login_response", Map.of("status", "error")));
                    }

                    sender.setSession(s);

                    yield gson.toJson(new JsonMessage("login_response", Map.of(
                            "status", "ok",
                            "token", s.getToken(),
                            "user_id", s.getUserId(),
                            "preferred_language_code", s.getPreferredLanguageCode()
                    )));
                }


                case "send_message" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    String content = p.get("content").toString();

                    if (p.containsKey("receiver_id")) {
                        int receiverId = Integer.parseInt(p.get("receiver_id").toString());
                        chatService.savePrivateMessage(sender.getSession().getUserId(), receiverId, content);
                        ClientHandler.sendToUser(receiverId, json);
                    }

                    if (p.containsKey("group_id")) {
                        int groupId = Integer.parseInt(p.get("group_id").toString());
                        int senderId = sender.getSession().getUserId();

                        if (!chatService.isUserInGroup(groupId, senderId)) {
                            yield gson.toJson(new JsonMessage("send_message_response", Map.of(
                                    "status", "error",
                                    "message", "You are not a member of this group"
                            )));
                        }

                        chatService.saveGroupMessage(senderId, groupId, content);

                        List<Integer> members = chatService.getGroupMembers(groupId);
                        ClientHandler.sendToUsers(members, json, senderId);
                    }

                    yield null;
                }

                case "fetch_history" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    String type = p.get("chat_type").toString(); // "private" or "group"

                    if ("private".equals(type)) {
                        int otherUserId = Integer.parseInt(p.get("user_id").toString());
                        List<Map<String, Object>> history =
                                chatService.fetchPrivateHistory(
                                        sender.getSession().getUserId(),
                                        otherUserId,
                                        userService
                                );

                        yield gson.toJson(new JsonMessage("fetch_history_response", Map.of(
                                "status", "ok",
                                "chat_type", "private",
                                "messages", history
                        )));
                    } else if ("group".equals(type)) {
                        int groupId = Integer.parseInt(p.get("group_id").toString());

                        List<GroupMessage> messages = chatService.fetchGroupHistory(groupId);
                        List<Map<String, Object>> history = new ArrayList<>();

                        for (GroupMessage gm : messages) {
                            User senderUser = userService.getById(gm.senderId());
                            String senderName = senderUser != null ? senderUser.getUsername() : "Unknown";

                            Map<String, Object> msgMap = new LinkedHashMap<>();
                            msgMap.put("sender_id", gm.senderId());
                            msgMap.put("sender_username", senderName);
                            msgMap.put("avatar_url", senderUser != null ? senderUser.getAvatarUrl() : "");
                            msgMap.put("content", gm.content());

                            history.add(msgMap);
                        }


                        yield gson.toJson(new JsonMessage("fetch_history_response", Map.of(
                                "status", "ok",
                                "chat_type", "group",
                                "messages", history
                        )));
                    } else {
                        // handle unknown chat_type
                        yield gson.toJson(new JsonMessage("error", "Unknown chat type: " + type));
                    }
                }


                case "get_user" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    int userId = sender.getSession().getUserId(); // ✅ SESSION ONLY
                    User user = userService.getById(userId);

                    if (user == null) {
                        yield gson.toJson(new JsonMessage(
                                "get_user_response",
                                Map.of("status", "error", "message", "User not found")
                        ));
                    }

                    yield gson.toJson(new JsonMessage(
                            "get_user_response",
                            Map.of(
                                    "status", "ok",
                                    "user", Map.of(
                                            "id", user.getId(),
                                            "username", user.getUsername(),
                                            "avatar_url", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                                            "bio", user.getBio() != null ? user.getBio() : ""
                                    )
                            )
                    ));
                }


                case "list_users" -> {

                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    try {
                        int currentUserId = sender.getSession().getUserId();

                        // Users messages ONLY
                        List<User> users = userService.listUsersWithMessages(currentUserId);
                        List<Map<String, Object>> userList = users.stream()
                                .map(u -> Map.<String, Object>of(
                                        "id", u.getId(),
                                        "username", u.getUsername(),
                                        "avatar_url", u.getAvatarUrl() != null ? u.getAvatarUrl() : ""
                                ))
                                .toList();

                        // 2️⃣ Groups the user belongs to
                        List<Map<String, Object>> groupList = userService.listGroupsForUser(currentUserId);

                        yield gson.toJson(new JsonMessage("list_users_response", Map.of(
                                "status", "ok",
                                "users", userList,
                                "groups", groupList
                        )));
                    } catch (Exception e) {
                        yield gson.toJson(new JsonMessage("error", "Exception: " + e.getMessage()));
                    }
                }


                case "signup" -> {
                    Map<String, Object> p = (Map<String, Object>) msg.getPayload();

                    String username = p.get("username").toString().replaceAll("\\s+", "").toLowerCase();
                    String password = p.get("password").toString();
                    String avatarUrl = p.containsKey("avatar_url") ? p.get("avatar_url").toString() : "";
                    String bio = p.containsKey("bio") ? p.get("bio").toString() : "";

                    try {
                        boolean ok = userService.createUser(username, password, avatarUrl, bio);
                        if (ok) {
                            yield gson.toJson(new JsonMessage("signup_response", Map.of("status", "ok")));
                        } else {
                            yield gson.toJson(new JsonMessage("signup_response", Map.of("status", "error", "message", "Username already exists")));
                        }
                    } catch (Exception e) {
                        yield gson.toJson(new JsonMessage("signup_response", Map.of("status", "error", "message", e.getMessage())));
                    }
                }
                case "list_group_members" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());

                    List<Integer> memberIds = chatService.getGroupMembers(groupId);

                    yield gson.toJson(new JsonMessage("list_group_members_response", Map.of(
                            "status", "ok",
                            "group_id", groupId,
                            "members", memberIds
                    )));
                }
                case "create_group" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<String, Object> p = (Map<String, Object>) msg.getPayload();
                    String name = p.get("name").toString();
                    String about = p.getOrDefault("about", "").toString();
                    String avatarUrl = p.getOrDefault("avatar_url", "").toString();

                    List<?> rawIds = (List<?>) p.getOrDefault("user_ids", List.of());

                    List<Integer> userIds = new ArrayList<>();
                    for (Object o : rawIds) {
                        String s = o.toString();
                        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
                        userIds.add(Integer.parseInt(s));
                    }

                    int creatorId = sender.getSession().getUserId();

                    try {
                        int groupId = chatService.createGroup(name, about, avatarUrl, creatorId);

                        for (int userId : userIds) {
                            if (userId != creatorId && !chatService.isUserInGroup(groupId, userId)) {
                                chatService.addUserToGroup(groupId, userId, "member");
                            }
                        }

                        yield gson.toJson(new JsonMessage("create_group_response", Map.of(
                                "status", "ok",
                                "group_id", groupId
                        )));
                    } catch (Exception e) {
                        yield gson.toJson(new JsonMessage("create_group_response", Map.of(
                                "status", "error",
                                "message", e.getMessage()
                        )));
                    }
                }

                case "add_user_to_group" -> {

                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage(
                                "error",
                                "Not authenticated"
                        ));
                    }


                    Map<String, Object> p = (Map<String, Object>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());
                    int senderId = sender.getSession().getUserId();


                    if (!chatService.isUserInGroup(groupId, senderId)) {
                        yield gson.toJson(new JsonMessage(
                                "add_user_to_group_response",
                                Map.of(
                                        "status", "error",
                                        "message", "You are not a member of this group"
                                )
                        ));
                    }

                    List<?> rawIds = (List<?>) p.get("user_ids");
                    List<Integer> userIds = rawIds.stream()
                            .map(o -> Integer.parseInt(o.toString()))
                            .distinct()          // prevent duplicates in request
                            .toList();

                    try {
                        List<Integer> usersToAdd = new java.util.ArrayList<>();
                        for (int userId : userIds) {
                            if (!chatService.isUserInGroup(groupId, userId)) {
                                usersToAdd.add(userId);
                            }
                        }

                        if (usersToAdd.isEmpty()) {
                            yield gson.toJson(new JsonMessage(
                                    "add_user_to_group_response",
                                    Map.of(
                                            "status", "ok",
                                            "group_id", groupId,
                                            "message", "No new users to add"
                                    )
                            ));
                        }

                        chatService.addUsersToGroup(groupId, usersToAdd);

                        yield gson.toJson(new JsonMessage(
                                "add_user_to_group_response",
                                Map.of(
                                        "status", "ok",
                                        "group_id", groupId,
                                        "added_count", usersToAdd.size()
                                )
                        ));

                    } catch (SQLException e) {
                        yield gson.toJson(new JsonMessage(
                                "add_user_to_group_response",
                                Map.of(
                                        "status", "error",
                                        "message", "Database error: " + e.getMessage()
                                )
                        ));
                    }
                }
                case "leave_group" -> {

                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());
                    int userId = sender.getSession().getUserId();

                    if (!chatService.isUserInGroup(groupId, userId)) {
                        yield gson.toJson(new JsonMessage(
                                "leave_group_response",
                                Map.of(
                                        "status", "error",
                                        "message", "You are not a member of this group"
                                )
                        ));
                    }

                    boolean left = chatService.leaveGroup(groupId, userId);

                    yield gson.toJson(new JsonMessage(
                            "leave_group_response",
                            Map.of(
                                    "status", "ok",
                                    "group_id", groupId,
                                    "left", left
                            )
                    ));
                }
                case "list_groups_for_user" -> {

                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    int userId = sender.getSession().getUserId();

                    List<Map<String, Object>> groups =
                            chatService.listGroupsForUser(userId);

                    yield gson.toJson(new JsonMessage(
                            "list_groups_for_user_response",
                            Map.of(
                                    "status", "ok",
                                    "groups", groups
                            )
                    ));
                }
                case "set_preferred_language" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    String languageCode = p.get("language_code").toString(); // e.g., "en", "am", "fr"
                    int userId = sender.getSession().getUserId();

                    try {
                        boolean updated = userService.updatePreferredLanguage(userId, languageCode);
                        if (updated) {
                            // Update the session too
                            sender.getSession().setPreferredLanguageCode(languageCode);

                            yield gson.toJson(new JsonMessage("set_preferred_language_response", Map.of(
                                    "status", "ok",
                                    "preferred_language_code", languageCode
                            )));
                        } else {
                            yield gson.toJson(new JsonMessage("set_preferred_language_response", Map.of(
                                    "status", "error",
                                    "message", "Failed to update language"
                            )));
                        }
                    } catch (SQLException e) {
                        yield gson.toJson(new JsonMessage("set_preferred_language_response", Map.of(
                                "status", "error",
                                "message", "Database error: " + e.getMessage()
                        )));
                    }
                }
                case "get_preferred_language" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    // Get the language from the session
                    String languageCode = sender.getSession().getPreferredLanguageCode();

                    yield gson.toJson(new JsonMessage("get_preferred_language_response", Map.of(
                            "status", "ok",
                            "preferred_language_code", languageCode
                    )));
                }
                case "set_password" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    String oldPassword = p.get("old_password").toString();
                    String newPassword = p.get("new_password").toString();

                    int userId = sender.getSession().getUserId();

                    boolean ok = userService.changePassword(userId, oldPassword, newPassword);

                    if (ok) {
                        yield gson.toJson(new JsonMessage(
                                "set_password_response",
                                Map.of("status", "ok")
                        ));
                    } else {
                        yield gson.toJson(new JsonMessage(
                                "set_password_response",
                                Map.of(
                                        "status", "error",
                                        "message", "Old password incorrect"
                                )
                        ));
                    }
                }
                case "set_bio" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    String bio = p.get("bio").toString();

                    int userId = sender.getSession().getUserId();

                    boolean updated = userService.updateBio(userId, bio);

                    yield gson.toJson(new JsonMessage(
                            "set_bio_response",
                            Map.of(
                                    "status", updated ? "ok" : "error"
                            )
                    ));
                }

                case "search_users" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    String query = p.get("query").toString().trim();

                    if (query.isEmpty()) {
                        yield gson.toJson(new JsonMessage(
                                "search_users_response",
                                Map.of("status", "ok", "users", List.of())
                        ));
                    }

                    int currentUserId = sender.getSession().getUserId();

                    List<User> users = userService.searchUsers(query, currentUserId);

                    List<Map<String, Object>> result = users.stream()
                            .map(u -> Map.<String, Object>of(
                                    "id", u.getId(),
                                    "username", u.getUsername(),
                                    "avatar_url", u.getAvatarUrl() != null ? u.getAvatarUrl() : ""
                            ))
                            .toList();

                    yield gson.toJson(new JsonMessage(
                            "search_users_response",
                            Map.of(
                                    "status", "ok",
                                    "users", result
                            )
                    ));
                }
                case "logout" -> {
                    if (sender.getSession() != null) {

                        try {
                            // Invalidate the token from database
                            SessionManager.invalidate(sender.getSession().getToken());
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    // clear session from SessionInfo
                    sender.clearSession();
                    sender.close();
                    yield gson.toJson(new JsonMessage("logout_response", Map.of(
                            "status", "ok"
                    )));
                }

                case "get_session" -> {
                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    String token = p.get("token").toString();

                    SessionInfo s = SessionManager.get(token);
                    if (s == null) {
                        yield gson.toJson(new JsonMessage("get_session_response", Map.of(
                                "status", "error"
                        )));
                    }

                    User user = userService.getById(s.getUserId());
                    if (user == null) {
                        yield gson.toJson(new JsonMessage("get_session_response", Map.of(
                                "status", "error"
                        )));
                    }

                    sender.setSession(s); // set session in client handler
                    yield gson.toJson(new JsonMessage("get_session_response", Map.of(
                            "status", "ok",
                            "token", s.getToken(),
                            "user_id", s.getUserId(),
                            "preferred_language_code", s.getPreferredLanguageCode()
                    )));
                }




                default -> gson.toJson(new JsonMessage("error", "Unknown message type"));
            };

        } catch (SQLException e) {
            return gson.toJson(new JsonMessage("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            return gson.toJson(new JsonMessage("error", "Exception:" + e.getMessage()));
        }
    }
}
