package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public class AppUserPrincipal implements UserDetails, Serializable {

    /** Fixed so that compatible changes do not invalidate sessions already stored in the database. */
    private static final long serialVersionUID = 1L;

    private final User user;

    public AppUserPrincipal(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    /** Deactivated accounts are rejected by Spring Security at sign-in. */
    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
