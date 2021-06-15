package ubc.pavlab.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordReset {

    @Size(min = 6, message = "New password must have at least 6 characters.")
    private String newPassword;

    @NotEmpty(message = "Password confirmation cannot be empty.")
    private String passwordConfirm;

    public boolean isValid() {
        return this.newPassword.equals( this.passwordConfirm );
    }
}
