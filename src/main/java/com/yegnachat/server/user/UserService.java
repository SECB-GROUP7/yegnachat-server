package com.yegnachat.server.user;

import com.yegnachat.server.user.UserDao;
import com.yegnachat.server.user.User;
import com.yegnachat.server.DatabaseService;
import com.yegnachat.server.util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class UserService {

    private final DatabaseService db;

    public UserService(DatabaseService db) {
        this.db = db;
    }

    public User getByUsername(String username) throws SQLException {
        try (Connection conn = db.getConnection()) {
            return new UserDao(conn).getUserByUsername(username);
        }
    }

    public User getById(int id) throws SQLException {
        try (Connection conn = db.getConnection()) {
            return new UserDao(conn).getUserById(id);
        }
    }

    public List<User> listAllUsersExcept(int userId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            return new UserDao(conn).listAllExcept(userId);
        }
    }
    public boolean createUser(String username, String password, String avatarUrl, String bio) throws SQLException {
        try (Connection conn = db.getConnection()) {
            UserDao dao = new UserDao(conn);

            if (dao.getUserByUsername(username) != null) {
                return false;
            }

            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(PasswordUtil.hashPassword(password));
            user.setAvatarUrl(avatarUrl);
            user.setBio(bio);

            return dao.createUser(user);
        }
    }
}
