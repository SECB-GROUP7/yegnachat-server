package com.yegnachat.server;

import com.google.gson.Gson;
import com.yegnachat.server.auth.SessionManager;
import com.yegnachat.server.chat.GroupMessage;
import com.yegnachat.server.feed.FeedService;
import com.yegnachat.server.image.ImageUploadService;
import com.yegnachat.server.protocol.JsonMessage;
import com.yegnachat.server.auth.AuthService;
import com.yegnachat.server.auth.SessionInfo;
import com.yegnachat.server.chat.ChatService;
import com.yegnachat.server.user.UserService;
import com.yegnachat.server.user.User;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;

public class MessageRouter {

    private final Gson gson = new Gson();
    private final AuthService authService;
    private final ChatService chatService;
    private final UserService userService;
    private final FeedService feedService;


    public MessageRouter(AuthService authService, ChatService chatService, UserService userService,FeedService feedService) {
        this.authService = authService;
        this.chatService = chatService;
        this.userService = userService;
        this.feedService = feedService;
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
                    } else {
                        sender.setSession(s);
                        yield gson.toJson(new JsonMessage("login_response", Map.of(
                                "status", "ok",
                                "token", s.getToken(),
                                "user_id", s.getUserId(),
                                "preferred_language_code", s.getPreferredLanguageCode()
                        )));
                    }

                }


                case "send_message" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    String content = p.get("content").toString();

                    if (p.containsKey("receiver_id")) {
                        int senderId = sender.getSession().getUserId();
                        int receiverId = Integer.parseInt(p.get("receiver_id").toString());

                        chatService.savePrivateMessage(senderId, receiverId, content);

                        User senderUser = userService.getById(senderId);

                        Map<String, Object> enrichedPayload = Map.of(
                                "chat_type", "private",
                                "sender_id", senderId,
                                "sender_username", senderUser.getUsername(),
                                "avatar_url", senderUser.getAvatarUrl() != null ? senderUser.getAvatarUrl() : "",
                                "receiver_id", receiverId,
                                "content", content
                        );

                        String outgoing = gson.toJson(new JsonMessage("send_message", enrichedPayload));

                        // Send to receiver
                        ClientHandler.sendToUser(receiverId, outgoing);

                        // Optional: echo back to sender (recommended for UI consistency)
                        ClientHandler.sendToUser(senderId, outgoing);
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
                        User senderUser = userService.getById(senderId);

                        Map<String, Object> enrichedPayload = Map.of(
                                "chat_type", "group",
                                "sender_id", senderId,
                                "sender_username", senderUser.getUsername(),
                                "avatar_url", senderUser.getAvatarUrl() != null ? senderUser.getAvatarUrl() : "",
                                "group_id", groupId,
                                "content", content
                        );

                        String outgoing = gson.toJson(new JsonMessage("send_message", enrichedPayload));

                        for (int memberId : members) {
                            ClientHandler.sendToUser(memberId, outgoing);
                        }
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
                        // Convert user_id to integer safely
                        int otherUserId = ((Number) p.get("user_id")).intValue();

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
                        // Convert group_id to integer safely
                        int groupId = ((Number) p.get("group_id")).intValue();

                        List<GroupMessage> messages = chatService.fetchGroupHistory(groupId);
                        List<Map<String, Object>> history = new ArrayList<>();

                        for (GroupMessage gm : messages) {
                            User senderUser = userService.getById(gm.senderId());
                            String senderName = senderUser != null ? senderUser.getUsername() : "Unknown";

                            Map<String, Object> msgMap = new LinkedHashMap<>();
                            msgMap.put("sender_id", gm.senderId());              // integer
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
                        yield gson.toJson(new JsonMessage("error", "Unknown chat type: " + type));
                    }
                }

                // to get yourself
                case "get_user" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    int userId = sender.getSession().getUserId();
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
                // to get other people using ID
                case "get_user_profile" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int targetUserId = Integer.parseInt(p.get("user_id").toString());

                    User user = userService.getById(targetUserId);
                    if (user == null) {
                        yield gson.toJson(new JsonMessage(
                                "get_user_profile_response",
                                Map.of("status", "error", "message", "User not found")
                        ));
                    }

                    yield gson.toJson(new JsonMessage(
                            "get_user_profile_response",
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

                        // Groups the user belongs to
                        List<Map<String, Object>> groupList = userService.listGroupsForUser(currentUserId);

                        yield gson.toJson(new JsonMessage("list_users_response", Map.of(
                                "status", "ok",
                                "users", userList,
                                "groups", groupList
                        )));
                    } catch (Exception e) {
                        e.printStackTrace();
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
                        e.printStackTrace();
                        yield gson.toJson(new JsonMessage("signup_response", Map.of("status", "error", "message", e.getMessage())));
                    }
                }
                case "list_group_members" -> {

                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());

                    List<Map<String, Object>> members = chatService.getGroupMembersDetailed(groupId);

                    yield gson.toJson(new JsonMessage(
                            "list_group_members_response",
                            Map.of(
                                    "status", "ok",
                                    "group_id", groupId,
                                    "members", members
                            )
                    ));
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
                        e.printStackTrace();
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
                        e.printStackTrace();
                        yield gson.toJson(new JsonMessage(
                                "add_user_to_group_response",
                                Map.of(
                                        "status", "error",
                                        "message", "Database error: " + e.getMessage()
                                )
                        ));
                    }
                }
                case "add_group_member" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage(
                                "error",
                                "Not authenticated"
                        ));
                    }

                    Map<String, Object> p = (Map<String, Object>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());
                    String username = p.get("username").toString();
                    int senderId = sender.getSession().getUserId();

                    try {
                        // Check if sender is in the group
                        if (!chatService.isUserInGroup(groupId, senderId)) {
                            yield gson.toJson(new JsonMessage(
                                    "add_group_member_response",
                                    Map.of(
                                            "status", "error",
                                            "message", "You are not a member of this group"
                                    )
                            ));
                        }

                        // Lookup user by username
                        User user = userService.getByUsername(username);
                        if (user == null) {
                            yield gson.toJson(new JsonMessage(
                                    "add_group_member_response",
                                    Map.of(
                                            "status", "error",
                                            "message", "User not found"
                                    )
                            ));
                        }

                        int userId = user.getId();

                        // Check if user is already in group
                        if (chatService.isUserInGroup(groupId, userId)) {
                            yield gson.toJson(new JsonMessage(
                                    "add_group_member_response",
                                    Map.of(
                                            "status", "error",
                                            "message", "User is already in the group"
                                    )
                            ));
                        }

                        // Add user to group as "member"
                        chatService.addUserToGroup(groupId, userId, "member");

                        yield gson.toJson(new JsonMessage(
                                "add_group_member_response",
                                Map.of(
                                        "status", "ok",
                                        "group_id", groupId,
                                        "added_username", username
                                )
                        ));

                    } catch (SQLException e) {
                        e.printStackTrace();
                        yield gson.toJson(new JsonMessage(
                                "add_group_member_response",
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
                        e.printStackTrace();
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
                case "remove_user_from_group" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());
                    int targetUserId = Integer.parseInt(p.get("user_id").toString());
                    int senderId = sender.getSession().getUserId();

                    if (!chatService.isAdminInGroup(groupId, senderId)) {
                        yield gson.toJson(new JsonMessage("remove_user_from_group_response", Map.of(
                                "status", "error",
                                "message", "You are not an admin of this group"
                        )));
                    }

                    boolean removed = chatService.removeUserFromGroup(groupId, targetUserId);

                    yield gson.toJson(new JsonMessage("remove_user_from_group_response", Map.of(
                            "status", removed ? "ok" : "error",
                            "removed_user_id", targetUserId
                    )));
                }
                case "promote_demote_user" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());
                    int targetUserId = Integer.parseInt(p.get("user_id").toString());
                    String newRole = p.get("new_role").toString().toLowerCase();
                    int senderId = sender.getSession().getUserId();

                    if (!chatService.isOwnerInGroup(groupId, senderId)) {
                        yield gson.toJson(new JsonMessage("promote_demote_user_response", Map.of(
                                "status", "error",
                                "message", "Only the owner can change roles"
                        )));
                    }

                    boolean updated = chatService.updateUserRole(groupId, targetUserId, newRole);

                    yield gson.toJson(new JsonMessage("promote_demote_user_response", Map.of(
                            "status", updated ? "ok" : "error",
                            "user_id", targetUserId,
                            "new_role", newRole
                    )));
                }
                case "update_group_info" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());
                    String name = p.get("name").toString();
                    String about = p.get("about").toString();
                    String avatarUrl = p.get("avatar_url").toString();
                    int senderId = sender.getSession().getUserId();

                    if (!chatService.isAdminInGroup(groupId, senderId)) {
                        yield gson.toJson(new JsonMessage("update_group_info_response", Map.of(
                                "status", "error",
                                "message", "Only admins can update group info"
                        )));
                    }

                    boolean updated = chatService.updateGroupInfo(groupId, name, about, avatarUrl);

                    yield gson.toJson(new JsonMessage("update_group_info_response", Map.of(
                            "status", updated ? "ok" : "error",
                            "group_id", groupId
                    )));
                }
                case "list_group_admins" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    int groupId = ((Number)((Map<?, ?>) msg.getPayload()).get("group_id")).intValue();

                    List<Map<String, Object>> admins = chatService.listGroupAdmins(groupId);

                    yield gson.toJson(new JsonMessage("list_group_admins_response", Map.of(
                            "status", "ok",
                            "group_id", groupId,
                            "admins", admins
                    )));
                }
                // FEED BASED
                case "follow_user" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }
                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int targetId = ((Number) p.get("user_id")).intValue();
                    boolean ok = userService.followUser(sender.getSession().getUserId(), targetId);

                    yield gson.toJson(new JsonMessage("follow_user_response", Map.of(
                            "status", ok ? "ok" : "error",
                            "user_id", targetId
                    )));
                }

                case "unfollow_user" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }
                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int targetId = ((Number) p.get("user_id")).intValue();
                    boolean ok = userService.unfollowUser(sender.getSession().getUserId(), targetId);

                    yield gson.toJson(new JsonMessage("unfollow_user_response", Map.of(
                            "status", ok ? "ok" : "error",
                            "user_id", targetId
                    )));
                }
                case "create_post" -> {
                    int userId = sender.getSession().getUserId();
                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();

                    String content = Objects.toString(p.get("content"), "").trim();
                    boolean hasImage = Boolean.TRUE.equals(p.get("has_image"));

                    if (content.isEmpty() && !hasImage) {
                        yield gson.toJson(new JsonMessage("create_post_response", Map.of(
                                "status", "error",
                                "message", "Post cannot be empty"
                        )));
                    }

                    // Create post WITHOUT image
                    long postId = feedService.createPost(userId, content, null);

                    // Receive binary image (if present)
                    if (hasImage) {
                        long imageSize = ((Number) p.get("image_size")).longValue();
                        String mime = Objects.toString(p.get("mime"), "image/png");

                        if (!mime.startsWith("image/")) {
                            yield gson.toJson(new JsonMessage("create_post_response", Map.of(
                                    "status", "error",
                                    "message", "Invalid image type"
                            )));
                        }

                        if (imageSize <= 0 || imageSize > 10_000_000) { // 10MB cap
                            yield gson.toJson(new JsonMessage("create_post_response", Map.of(
                                    "status", "error",
                                    "message", "Invalid image size"
                            )));
                        }

                        try(InputStream imageStream = sender.readBinary(imageSize)){

                        String imageUrl = ImageUploadService.uploadPostImage(
                                postId,
                                imageStream,
                                mime
                        );
                            feedService.attachPostImage(postId, imageUrl);
                        }


                    }

                    yield gson.toJson(new JsonMessage("create_post_response", Map.of(
                            "status", "ok",
                            "post_id", postId
                    )));
                }



                case "list_feed_posts" -> {
                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();

                    int limit = parseIntSafe(p.get("limit"), 20);  // default 20
                    int offset = parseIntSafe(p.get("offset"), 0); // default 0

                    var posts = feedService.listFeedPosts(limit, offset);

                    yield gson.toJson(new JsonMessage("list_feed_posts_response", Map.of(
                            "status", "ok",
                            "posts", posts
                    )));
                }
                case "like_post" -> {
                    int userId = sender.getSession().getUserId();
                    long postId = ((Number)((Map<?, ?>) msg.getPayload()).get("post_id")).longValue();

                    feedService.likePost(userId, postId);

                    yield gson.toJson(new JsonMessage("like_post_response", Map.of(
                            "status", "ok",
                            "post_id", postId
                    )));
                }

                case "unlike_post" -> {
                    int userId = sender.getSession().getUserId();
                    long postId = ((Number)((Map<?, ?>) msg.getPayload()).get("post_id")).longValue();

                    feedService.unlikePost(userId, postId);

                    yield gson.toJson(new JsonMessage("unlike_post_response", Map.of(
                            "status", "ok",
                            "post_id", postId
                    )));
                }
                case "add_comment" -> {
                    int userId = sender.getSession().getUserId();
                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();

                    long postId = ((Number)p.get("post_id")).longValue();
                    String content = p.get("content").toString();

                    long commentId = feedService.addComment(userId, postId, content);

                    yield gson.toJson(new JsonMessage("add_comment_response", Map.of(
                            "status", "ok",
                            "comment_id", commentId,
                            "post_id", postId
                    )));
                }

                case "list_comments" -> {
                    long postId = ((Number)((Map<?, ?>) msg.getPayload()).get("post_id")).longValue();

                    var comments = feedService.listComments(postId);

                    yield gson.toJson(new JsonMessage("list_comments_response", Map.of(
                            "status", "ok",
                            "post_id", postId,
                            "comments", comments
                    )));
                }
                case "get_group_info" -> {
                    if (sender.getSession() == null) {
                        yield gson.toJson(new JsonMessage("error", "Not authenticated"));
                    }

                    Map<?, ?> p = (Map<?, ?>) msg.getPayload();
                    int groupId = Integer.parseInt(p.get("group_id").toString());

                    try {
                        Map<String, Object> groupData = chatService.getGroupInfo(groupId);

                        if (groupData == null) {
                            yield gson.toJson(new JsonMessage("get_group_info_response", Map.of(
                                    "status", "error",
                                    "message", "Group not found"
                            )));
                        } else {
                            yield gson.toJson(new JsonMessage("get_group_info_response", Map.of(
                                    "status", "ok",
                                    "group", groupData
                            )));
                        }
                    } catch (SQLException e) {
                        yield gson.toJson(new JsonMessage("get_group_info_response", Map.of(
                                "status", "error",
                                "message", "Database error: " + e.getMessage()
                        )));
                    }
                }



                default -> gson.toJson(new JsonMessage("error", "Unknown message type"));
            };


        } catch (SQLException e) {
            return gson.toJson(new JsonMessage("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            return gson.toJson(new JsonMessage("error", "Exception:" + e.getMessage()));
        }
    }
    public void onImageUploadComplete(
            ClientHandler sender,
            String imagePath,
            String purpose
    ){

       try{ if (sender.getSession() == null) return;

        int userId = sender.getSession().getUserId();

        if ("avatar".equals(purpose)) {
            userService.updateAvatar(userId, imagePath);
        }

        sender.send(gson.toJson(new JsonMessage(
                "upload_complete",
                Map.of("image_url", imagePath)
        )));}catch (SQLException e){
           e.printStackTrace();
       }
    }
    private static int parseIntSafe(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return (int) Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
