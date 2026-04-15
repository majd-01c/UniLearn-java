package dto.lms;

public class UserOptionDto {
    private final Integer userId;
    private final String email;

    public UserOptionDto(Integer userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public Integer getUserId() { return userId; }
    public String getEmail() { return email; }

    @Override
    public String toString() { return email == null ? "" : email; }
}
