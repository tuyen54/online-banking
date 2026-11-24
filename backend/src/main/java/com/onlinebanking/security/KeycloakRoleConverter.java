package com.onlinebanking.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts Keycloak Realm Roles embedded in the JWT {@code realm_access.roles}
 * claim into Spring Security {@link GrantedAuthority} instances.
 *
 * <p>Keycloak stores realm roles in the JWT like:
 * <pre>{
 *   "realm_access": {
 *     "roles": ["customer", "staff"]
 *   }
 * }</pre>
 *
 * <p>This converter maps each role to {@code ROLE_<UPPERCASE>}, for example:
 * <ul>
 *   <li>{@code customer} → {@code ROLE_CUSTOMER}</li>
 *   <li>{@code admin}    → {@code ROLE_ADMIN}</li>
 *   <li>{@code staff}    → {@code ROLE_STAFF}</li>
 *   <li>{@code auditor}  → {@code ROLE_AUDITOR}</li>
 * </ul>
 *
 * <p>This convention is compatible with Spring Security's
 * {@code @PreAuthorize("hasRole('ADMIN')")}.
 *
 * <p>Safety guarantees:
 * <ul>
 *   <li>Returns an empty collection when {@code realm_access} or {@code roles} is missing.</li>
 *   <li>Silently skips non-String entries in the roles collection.</li>
 *   <li>Never grants any role when the claim is absent or malformed.</li>
 *   <li>Never reads role information from HTTP request parameters or frontend data.</li>
 * </ul>
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // 1. Extract the realm_access map
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return Collections.emptyList();
        }

        // 2. Extract the roles collection
        Object rolesObject = realmAccess.get(ROLES_KEY);
        if (!(rolesObject instanceof Collection<?>)) {
            return Collections.emptyList();
        }

        Collection<?> roles = (Collection<?>) rolesObject;

        // 3. Convert each role string into a ROLE_<UPPERCASE> GrantedAuthority
        List<GrantedAuthority> authorities = roles.stream()
                .filter(role -> role instanceof String)
                .map(role -> (String) role)
                .filter(role -> !role.isBlank())
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                .collect(Collectors.toList());

        return Collections.unmodifiableList(authorities);
    }
}

