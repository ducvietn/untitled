package com.teamup.teamup.security;

import com.teamup.teamup.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security {@link UserDetails} implementation backed by the application
 * {@link User} entity.
 *
 * <h3>Subject scoping</h3>
 * The {@code subjectCode} field is extracted from the User entity and stored
 * in the principal so it is accessible inside SpEL expressions:
 * <pre>
 * {@code @PreAuthorize("@subjectAuth.canAccessGroup(#groupId, authentication)")}
 * </pre>
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password;
    private final String subjectCode;
    private final boolean accountActive;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.userId        = user.getId();
        this.email         = user.getEmail();
        this.password      = user.getPasswordHash();
        this.subjectCode   = user.getSubjectCode();   // null for students; non-null for teachers
        this.accountActive = user.isActive();
        this.authorities   = List.of(new SimpleGrantedAuthority(user.getRole().name()));
    }

    /** Constructor for programmatic creation (e.g. JWT token building). */
    public CustomUserDetails(Long userId, String email, String password,
                            String subjectCode, boolean accountActive, List<String> roles) {
        this.userId        = userId;
        this.email         = email;
        this.password      = password;
        this.subjectCode   = subjectCode;
        this.accountActive = accountActive;
        this.authorities   = roles.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override public String getUsername()       { return email; }
    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return accountActive; }
    @Override public boolean isCredentialsNonExpired()  { return true; }
    @Override public boolean isEnabled()                { return accountActive; }
}
