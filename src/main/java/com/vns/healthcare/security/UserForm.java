package com.vns.healthcare.security;

import java.util.ArrayList;
import java.util.List;

public class UserForm {

    private String username;
    private String password;
    private boolean enabled = true;
    private List<Long> roleIds = new ArrayList<Long>();
    private List<String> readPermissions = new ArrayList<String>();
    private List<String> writePermissions = new ArrayList<String>();

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds == null ? new ArrayList<Long>() : roleIds;
    }

    public List<String> getReadPermissions() {
        return readPermissions;
    }

    public void setReadPermissions(List<String> readPermissions) {
        this.readPermissions = readPermissions == null ? new ArrayList<String>() : readPermissions;
    }

    public List<String> getWritePermissions() {
        return writePermissions;
    }

    public void setWritePermissions(List<String> writePermissions) {
        this.writePermissions = writePermissions == null ? new ArrayList<String>() : writePermissions;
    }
}
