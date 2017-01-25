package vd_server.helper.objects;

import vd_server.helper.Crypto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by pouya on 1/20/17.
 */
public class User {

    private Integer ID;
    private String Username;
    private boolean IsLogined = false;
    private Connection db;
    private float Quota;
    private float Total_quota;

    public User(Connection db) {
        this.db = db;
    }

    public Integer getID() {
        return ID;
    }

    public String getUsername() {
        return Username;
    }

    public float getQuota() {
        return Quota;
    }

    public float getTotalQuota() {
        return Total_quota;
    }

    public boolean isLogined() {
        return IsLogined;
    }

    public void setLogined(boolean logined) {
        IsLogined = logined;
    }


    public boolean isExist(String username) {

        final String CheckSql = "SELECT COUNT(*) AS count from users where username = ?";

        try {

            PreparedStatement pstmt = db.prepareStatement(CheckSql);

            pstmt.setString(1, username);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int numberOfRows = rs.getInt(1);
                if (numberOfRows > 0)
                    return true;
            } else {
                System.out.println("error: could not get the record counts");
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;

    }

    public boolean Register(String username, String password) {


        String InsertSql = "INSERT INTO users(username,password) VALUES(?,?)";
        password = Crypto.sha256(password);
        try {

            if (isExist(username)) {
                return false;
            }

            PreparedStatement pstmt = db.prepareStatement(InsertSql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            int affectedRows = pstmt.executeUpdate();


            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return true;

    }

    public boolean Login(String username, String password) {

        password = Crypto.sha256(password);
        final String LoginSql = "SELECT id,remain_quota,total_quota from users where username = ? and password=? and status=1";

        try {

            PreparedStatement pstmt = db.prepareStatement(LoginSql);

            pstmt.setString(1, username);
            pstmt.setString(2, password);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt(1);

                if (id > 0) {
                    float quota = rs.getFloat(2);
                    float tquota = rs.getFloat(3);
                    this.ID = id;
                    this.IsLogined = true;
                    this.Username = username;
                    this.Quota = quota;
                    this.Total_quota = tquota;

                    return true;

                } else {
                    return false;
                }

            } else {
                System.out.println("error: could not get the record counts");
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;

    }

    public boolean PublicLogin() {

        final String LoginSql = "SELECT id,remain_quota,total_quota from users where username = \"public\" and status=1";

        try {

            PreparedStatement pstmt = db.prepareStatement(LoginSql);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt(1);

                if (id > 0) {
                    float quota = rs.getFloat(2);
                    float tquota = rs.getFloat(3);
                    this.ID = id;
                    this.IsLogined = true;
                    this.Username = "public";
                    this.Quota = quota;
                    this.Total_quota = tquota;

                    return true;

                } else {
                    return false;
                }

            } else {
                System.out.println("error: could not get the record counts");
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;

    }

    public boolean decQuota(float used) {


        String UpdateSql = "UPDATE users set remain_quota=remain_quota-? where id=?";
        try {


            PreparedStatement pstmt = db.prepareStatement(UpdateSql);
            pstmt.setFloat(1, used);
            pstmt.setInt(2, this.ID);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        getNewInfo();

        return true;

    }

    public boolean incQuota(float used) {


        String UpdateSql = "UPDATE users set remain_quota=remain_quota+? where id=?";
        try {


            PreparedStatement pstmt = db.prepareStatement(UpdateSql);
            pstmt.setFloat(1, used);
            pstmt.setInt(2, this.ID);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        getNewInfo();

        return true;

    }

    public void getNewInfo() {


        final String selectSql = "SELECT remain_quota,total_quota from users where id = ?";

        try {

            PreparedStatement pstmt = db.prepareStatement(selectSql);

            pstmt.setInt(1, this.ID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                float quota = rs.getFloat(1);
                float tquota = rs.getFloat(2);


                this.Quota = quota;
                this.Total_quota = tquota;


            } else {
                System.out.println("error: could not get the record counts");
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

    }
}
