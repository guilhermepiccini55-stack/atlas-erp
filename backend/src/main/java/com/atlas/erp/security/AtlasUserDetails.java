package com.atlas.erp.security;

import com.atlas.erp.users.User;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;

public final class AtlasUserDetails implements UserDetails, CredentialsContainer {

    private final UserDetails delegate;
    private final Long userId;
    private final long credentialVersion;

    public AtlasUserDetails(User user) {
        this.delegate = org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .disabled(user.getStatus() != com.atlas.erp.users.UserStatus.ACTIVE)
                .build();
        this.userId = user.getId();
        this.credentialVersion = user.getCredentialVersion();
    }

    public Long getUserId() {
        return userId;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getPassword() {
        return delegate.getPassword();
    }

    @Override
    public String getUsername() {
        return delegate.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return delegate.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return delegate.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return delegate.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public void eraseCredentials() {
        if (delegate instanceof CredentialsContainer credentialsContainer) {
            credentialsContainer.eraseCredentials();
        }
    }
}
