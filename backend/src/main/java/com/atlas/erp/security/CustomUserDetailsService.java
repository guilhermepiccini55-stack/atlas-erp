package com.atlas.erp.security;

import com.atlas.erp.users.CredentialState;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.User;
import java.util.regex.Pattern;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Pattern BCRYPT_HASH =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
        if (user.getCredentialState() == CredentialState.LEGACY_UNVERIFIED
                && !BCRYPT_HASH.matcher(user.getPasswordHash()).matches()) {
            throw new BadCredentialsException("Credencial indisponível para validação segura");
        }
        return new AtlasUserDetails(user);
    }
}
