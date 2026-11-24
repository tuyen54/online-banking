-- V2: UserProfile, Branch, IdentityGroup, and UserGroupMembership schema

CREATE TABLE IF NOT EXISTS branches (
    id BINARY(16) NOT NULL PRIMARY KEY,
    branch_code VARCHAR(20) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT uk_branches_branch_code UNIQUE (branch_code),
    INDEX idx_branch_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_profiles (
    id BINARY(16) NOT NULL PRIMARY KEY,
    keycloak_user_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NULL,
    email VARCHAR(255) NULL,
    first_name VARCHAR(255) NULL,
    last_name VARCHAR(255) NULL,
    display_name VARCHAR(500) NULL,
    email_verified BIT(1) NULL,
    profile_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    branch_id BINARY(16) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    last_synced_at DATETIME NULL,
    version BIGINT NULL,
    CONSTRAINT uk_user_profiles_keycloak_user_id UNIQUE (keycloak_user_id),
    CONSTRAINT fk_user_profiles_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    INDEX idx_up_username (username),
    INDEX idx_up_email (email),
    INDEX idx_up_branch_id (branch_id),
    INDEX idx_up_profile_status (profile_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS identity_groups (
    id BINARY(16) NOT NULL PRIMARY KEY,
    keycloak_group_path VARCHAR(500) NOT NULL,
    group_name VARCHAR(255) NOT NULL,
    keycloak_group_id VARCHAR(255) NULL,
    last_synced_at DATETIME NULL,
    CONSTRAINT uk_identity_groups_path UNIQUE (keycloak_group_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_group_memberships (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_profile_id BINARY(16) NOT NULL,
    identity_group_id BINARY(16) NOT NULL,
    synced_at DATETIME NULL,
    CONSTRAINT uk_user_group UNIQUE (user_profile_id, identity_group_id),
    CONSTRAINT fk_ugm_user_profile FOREIGN KEY (user_profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_ugm_identity_group FOREIGN KEY (identity_group_id) REFERENCES identity_groups (id) ON DELETE CASCADE,
    INDEX idx_ugm_user_profile (user_profile_id),
    INDEX idx_ugm_identity_group (identity_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

