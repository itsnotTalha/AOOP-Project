PRAGMA foreign_keys = ON;

-- ============================================================
-- Users
-- ============================================================
CREATE TABLE users (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid            TEXT NOT NULL UNIQUE,
    full_name       TEXT NOT NULL,
    username        TEXT NOT NULL COLLATE NOCASE UNIQUE,
    email           TEXT NOT NULL COLLATE NOCASE UNIQUE,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL DEFAULT 'USER'
                    CHECK (role IN ('ADMIN', 'USER')),
    profile_image   TEXT,
    phone           TEXT,
    is_verified     INTEGER NOT NULL DEFAULT 0
                    CHECK (is_verified IN (0, 1)),
    status          TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_username ON users (username);

-- ============================================================
-- Digital Assets
-- ============================================================
CREATE TABLE digital_assets (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid                TEXT NOT NULL UNIQUE,
    owner_id            INTEGER NOT NULL,
    title               TEXT NOT NULL
                        CHECK (length(title) BETWEEN 1 AND 150),
    description         TEXT
                        CHECK (description IS NULL OR length(description) <= 2000),
    asset_type          TEXT NOT NULL
                        CHECK (asset_type IN ('IMAGE', 'DOCUMENT')),
    original_filename   TEXT NOT NULL,
    stored_filename     TEXT NOT NULL UNIQUE,
    mime_type           TEXT NOT NULL,
    file_size           INTEGER NOT NULL CHECK (file_size >= 0),
    storage_path        TEXT NOT NULL,
    sha256_hash         TEXT NOT NULL UNIQUE
                        CHECK (length(sha256_hash) = 64 AND
                               sha256_hash NOT GLOB '*[^0-9a-f]*'),
    perceptual_hash     TEXT,
    metadata_json       TEXT,
    upload_date         TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verification_status TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    current_owner_id    INTEGER NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users (id),
    FOREIGN KEY (current_owner_id) REFERENCES users (id)
);

CREATE INDEX idx_digital_assets_owner_id ON digital_assets (owner_id);
CREATE INDEX idx_digital_assets_current_owner_id ON digital_assets (current_owner_id);
CREATE INDEX idx_digital_assets_sha256_hash ON digital_assets (sha256_hash);
CREATE INDEX idx_digital_assets_perceptual_hash ON digital_assets (perceptual_hash);

-- ============================================================
-- Documents
-- ============================================================
CREATE TABLE documents (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    asset_id        INTEGER NOT NULL UNIQUE,
    extracted_text  TEXT,
    semantic_hash   TEXT,
    page_count      INTEGER CHECK (page_count IS NULL OR page_count > 0),
    language        TEXT,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asset_id) REFERENCES digital_assets (id) ON DELETE CASCADE
);

CREATE INDEX idx_documents_asset_id ON documents (asset_id);
CREATE INDEX idx_documents_semantic_hash ON documents (semantic_hash);

-- ============================================================
-- Verification History
-- ============================================================
CREATE TABLE verification_history (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    asset_id            INTEGER NOT NULL,
    verified_by         INTEGER NOT NULL,
    verification_method TEXT NOT NULL
                        CHECK (verification_method IN ('SHA256_UPLOAD', 'SHA256_INTEGRITY')),
    result              TEXT NOT NULL
                        CHECK (result IN ('PENDING', 'VERIFIED', 'REJECTED', 'INCONCLUSIVE')),
    similarity_score    NUMERIC
                        CHECK (similarity_score IS NULL OR
                               (similarity_score >= 0 AND similarity_score <= 1)),
    notes               TEXT,
    verified_at         TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asset_id) REFERENCES digital_assets (id) ON DELETE CASCADE,
    FOREIGN KEY (verified_by) REFERENCES users (id)
);

CREATE INDEX idx_verification_history_asset_id ON verification_history (asset_id);
CREATE INDEX idx_verification_history_verified_by ON verification_history (verified_by);

-- ============================================================
-- Blockchain Ledger (VeriChain)
-- ============================================================
CREATE TABLE blockchain_ledger (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    block_index     INTEGER NOT NULL UNIQUE CHECK (block_index >= 0),
    asset_id        INTEGER NOT NULL,
    previous_hash   TEXT,
    current_hash    TEXT NOT NULL UNIQUE,
    owner_id        INTEGER NOT NULL,
    action          TEXT NOT NULL
                    CHECK (action IN ('MINT', 'TRANSFER', 'LIST', 'SALE', 'UPDATE')),
    timestamp       TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asset_id) REFERENCES digital_assets (id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_blockchain_ledger_asset_id ON blockchain_ledger (asset_id);
CREATE INDEX idx_blockchain_ledger_owner_id ON blockchain_ledger (owner_id);
CREATE INDEX idx_blockchain_ledger_current_hash ON blockchain_ledger (current_hash);

-- ============================================================
-- Marketplace Listings
-- ============================================================
CREATE TABLE marketplace_listings (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    asset_id    INTEGER NOT NULL,
    seller_id   INTEGER NOT NULL,
    price       NUMERIC NOT NULL CHECK (price >= 0),
    currency    TEXT NOT NULL DEFAULT 'USD',
    status      TEXT NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'SOLD', 'CANCELLED', 'EXPIRED')),
    listed_at   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TEXT,
    FOREIGN KEY (asset_id) REFERENCES digital_assets (id) ON DELETE CASCADE,
    FOREIGN KEY (seller_id) REFERENCES users (id),
    CHECK (expires_at IS NULL OR expires_at >= listed_at)
);

CREATE INDEX idx_marketplace_listings_asset_id ON marketplace_listings (asset_id);
CREATE INDEX idx_marketplace_listings_seller_id ON marketplace_listings (seller_id);
CREATE INDEX idx_marketplace_listings_status ON marketplace_listings (status);

-- ============================================================
-- Ownership History
-- ============================================================
CREATE TABLE ownership_history (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    asset_id            INTEGER NOT NULL,
    previous_owner      INTEGER,
    new_owner           INTEGER NOT NULL,
    transfer_type       TEXT NOT NULL
                        CHECK (transfer_type IN ('INITIAL', 'TRANSFER', 'PURCHASE', 'GIFT')),
    transfer_date       TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    blockchain_block_id INTEGER NOT NULL UNIQUE,
    FOREIGN KEY (asset_id) REFERENCES digital_assets (id) ON DELETE CASCADE,
    FOREIGN KEY (previous_owner) REFERENCES users (id),
    FOREIGN KEY (new_owner) REFERENCES users (id),
    FOREIGN KEY (blockchain_block_id) REFERENCES blockchain_ledger (id)
);

CREATE INDEX idx_ownership_history_asset_id ON ownership_history (asset_id);
CREATE INDEX idx_ownership_history_previous_owner ON ownership_history (previous_owner);
CREATE INDEX idx_ownership_history_new_owner ON ownership_history (new_owner);

-- ============================================================
-- Fractional Ownership
-- ============================================================
CREATE TABLE fractional_ownership (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    asset_id        INTEGER NOT NULL,
    owner_id        INTEGER NOT NULL,
    percentage      NUMERIC NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    shares          INTEGER NOT NULL CHECK (shares > 0),
    purchased_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asset_id) REFERENCES digital_assets (id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES users (id),
    UNIQUE (asset_id, owner_id)
);

CREATE INDEX idx_fractional_ownership_asset_id ON fractional_ownership (asset_id);
CREATE INDEX idx_fractional_ownership_owner_id ON fractional_ownership (owner_id);

-- ============================================================
-- VeriWallet
-- ============================================================
CREATE TABLE veri_wallets (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL UNIQUE,
    balance      NUMERIC NOT NULL DEFAULT 0 CHECK (balance >= 0),
    total_earned NUMERIC NOT NULL DEFAULT 0 CHECK (total_earned >= 0),
    total_spent  NUMERIC NOT NULL DEFAULT 0 CHECK (total_spent >= 0),
    updated_at   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_veri_wallets_user_id ON veri_wallets (user_id);

-- ============================================================
-- Wallet Transactions
-- ============================================================
CREATE TABLE wallet_transactions (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    wallet_id        INTEGER NOT NULL,
    transaction_type TEXT NOT NULL
                     CHECK (transaction_type IN ('CREDIT', 'DEBIT', 'REFUND', 'WITHDRAWAL')),
    amount           NUMERIC NOT NULL CHECK (amount > 0),
    description      TEXT,
    reference_id     TEXT,
    created_at       TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (wallet_id) REFERENCES veri_wallets (id) ON DELETE CASCADE
);

CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions (wallet_id);
CREATE INDEX idx_wallet_transactions_reference_id ON wallet_transactions (reference_id);
CREATE INDEX idx_wallet_transactions_created_at ON wallet_transactions (created_at);

-- ============================================================
-- Secure Vault (VeriSafe)
-- ============================================================
CREATE TABLE secure_vault (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER NOT NULL,
    vault_type      TEXT NOT NULL,
    title           TEXT NOT NULL,
    encrypted_data  TEXT NOT NULL,
    encryption_iv   TEXT NOT NULL,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_secure_vault_user_id ON secure_vault (user_id);
CREATE INDEX idx_secure_vault_vault_type ON secure_vault (vault_type);

-- ============================================================
-- Notifications
-- ============================================================
CREATE TABLE notifications (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL,
    title       TEXT NOT NULL,
    message     TEXT NOT NULL,
    type        TEXT NOT NULL,
    is_read     INTEGER NOT NULL DEFAULT 0
                CHECK (is_read IN (0, 1)),
    created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_is_read ON notifications (is_read);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);
