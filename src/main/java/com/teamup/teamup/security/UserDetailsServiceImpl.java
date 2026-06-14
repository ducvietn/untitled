package com.teamup.teamup.security;

import com.teamup.teamup.entity.User;
import com.teamup.teamup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a {@link CustomUserDetails} from the database.
 * Called by {@link JwtAuthenticationFilter} after JWT validation,
 * and by Spring Security's form-login / basic-auth mechanisms.
 *
 * <h3>Account status enforcement</h3>
 * Only users with {@code accountStatus == ACTIVE} can authenticate.
 * Users with {@code PENDING_APPROVAL} or {@code SUSPENDED} will cause
 * a {@code DisabledException} at the Spring Security layer, returning HTTP 403.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        log.debug("Loaded user: {} (role={}, status={})",
            email, user.getRole(), user.getAccountStatus());

        return new CustomUserDetails(user);
    }

    /**
     * Loads a user by their primary key (used after JWT extraction).
     * This ensures the most current {@code accountStatus} is always checked
     * — even if a token was issued before the user's account was suspended.
     *
     * @param userId the user's primary key
     * @return CustomUserDetails wrapping the current user state
     * @throws UsernameNotFoundException if the user does not exist
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        return new CustomUserDetails(user);
    }
}
