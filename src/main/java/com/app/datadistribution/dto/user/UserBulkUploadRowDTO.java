package com.app.datadistribution.dto.user;

import com.app.datadistribution.enums.HodAccessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBulkUploadRowDTO {
    private int rowNumber;
    private String name;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String username;
    private String roleName;
    private String departmentName;
    private boolean active;
    private String password;
    private HodAccessType hodAccessType;

    public String getResolvedFullName() {
        if (firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank()) {
            return (firstName.trim() + " " + lastName.trim()).trim();
        }
        if (firstName != null && !firstName.isBlank()) {
            return firstName.trim();
        }
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return "";
    }
}
