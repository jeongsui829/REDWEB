package com.scuredweb.social.dto;

public final class PublicProfileDTO {
    private Long userId;
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    private String nickname;
    public String getNickname() { return nickname; }
    public void setNickname(String value) { nickname = value; }
    private String bio;
    public String getBio() { return bio; }
    public void setBio(String value) { bio = value; }
    private String profileImagePath;
    public String getProfileImagePath() { return profileImagePath; }
    public void setProfileImagePath(String value) { profileImagePath = value; }
}
