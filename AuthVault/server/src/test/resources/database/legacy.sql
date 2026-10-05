-- Synthetic legacy DB. No production file or account data is used by the tests.
CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL, email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL, role TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE assets (
  id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL, title TEXT NOT NULL,
  description TEXT, category TEXT, file_name TEXT NOT NULL, file_path TEXT NOT NULL,
  file_size INTEGER, mime_type TEXT, status TEXT DEFAULT 'active',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE verification_reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT, asset_id INTEGER NOT NULL, verification_type TEXT,
  sha256_match INTEGER, similarity_score REAL, status TEXT, report_json TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE vaults (
  id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL,
  public_reference TEXT NOT NULL UNIQUE, name TEXT NOT NULL, description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE marketplace_listings (
  id INTEGER PRIMARY KEY AUTOINCREMENT, asset_id INTEGER NOT NULL, seller_id INTEGER NOT NULL,
  listing_type TEXT, price REAL, status TEXT DEFAULT 'active', created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE,
  FOREIGN KEY(seller_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE ownership_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT, asset_id INTEGER NOT NULL, previous_owner INTEGER,
  new_owner INTEGER, transfer_type TEXT, blockchain_block_id INTEGER,
  transferred_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE CASCADE,
  FOREIGN KEY(previous_owner) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY(new_owner) REFERENCES users(id) ON DELETE SET NULL
);
CREATE TABLE documents (
  id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL, asset_id INTEGER,
  page_count INTEGER, language TEXT DEFAULT 'eng', created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY(asset_id) REFERENCES assets(id) ON DELETE SET NULL
);
INSERT INTO users (id, full_name, email, password_hash, role) VALUES
  (1, 'Synthetic Owner', 'owner@example.test', 'preserve-this-hash', 'moderator'),
  (2, 'Synthetic Buyer', 'buyer@example.test', 'another-hash', 'unknown'),
  (3, 'Legacy User', 'legacy@example.test', 'third-hash', NULL);
INSERT INTO assets (id, owner_id, title, file_name, file_path) VALUES (10, 1, 'Original title', 'old.png', '/unchanged/old.png');
INSERT INTO verification_reports (id, asset_id, verification_type, sha256_match, similarity_score, status, report_json, created_at)
  VALUES (42, 10, 'image_comparison', 1, 98.5, 'exact', '{"keep":"unchanged"}', '2020-01-02 03:04:05');
INSERT INTO vaults (id, user_id, public_reference, name) VALUES (7, 1, 'VT-ABCDEF', 'Legacy collection');
INSERT INTO marketplace_listings (id, asset_id, seller_id, listing_type, price, status) VALUES
  (11, 10, 1, 'sale', 50, 'active'), (12, 10, 1, 'sale', 60, 'active'), (13, 10, 1, 'sale', 70, 'removed');
INSERT INTO ownership_history (id, asset_id, previous_owner, new_owner, transfer_type, transferred_at)
  VALUES (15, 10, 1, 2, 'marketplace_sale', '2020-01-02 03:04:05');
INSERT INTO documents (id, owner_id, asset_id, page_count) VALUES (20, 1, 10, 2);
