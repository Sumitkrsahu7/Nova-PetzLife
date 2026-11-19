package com.novapetzlife.security;

import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String trimmed = identifier != null ? identifier.trim() : "";
        String normalizedPhone = trimmed.startsWith("+91") ? trimmed.substring(3) : trimmed;

        User user = userRepository.findByEmail(trimmed.toLowerCase())
                .or(() -> userRepository.findByPhone(trimmed))
                .or(() -> userRepository.findByPhone(normalizedPhone))
                .or(() -> userRepository.findByPhone("+91" + normalizedPhone))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + trimmed));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UsernameNotFoundException("User account is deactivated: " + trimmed);
        }

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet());

        if (user.getPermissions() != null) {
            user.getPermissions().forEach(p ->
                    authorities.add(new SimpleGrantedAuthority(p.getName()))
            );
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                authorities
        );
    }
}
