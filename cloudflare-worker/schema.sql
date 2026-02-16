-- Database Schema for HotFix License System
-- Run these commands separately using: npx wrangler d1 execute

-- Licenses table
CREATE TABLE IF NOT EXISTS licenses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  license_key TEXT UNIQUE NOT NULL,
  max_devices INTEGER DEFAULT 2,
  is_active INTEGER DEFAULT 1,
  expires_at INTEGER,
  created_at INTEGER NOT NULL
);

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  license_id INTEGER NOT NULL,
  device_id TEXT NOT NULL,
  device_info TEXT,
  activated_at INTEGER NOT NULL,
  last_check INTEGER,
  FOREIGN KEY (license_id) REFERENCES licenses(id),
  UNIQUE(license_id, device_id)
);

-- Access logs table (optional - for monitoring)
CREATE TABLE IF NOT EXISTS access_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  license_id INTEGER,
  device_id TEXT,
  action TEXT,
  ip_address TEXT,
  timestamp INTEGER NOT NULL,
  FOREIGN KEY (license_id) REFERENCES licenses(id)
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_license_key ON licenses(license_key);
CREATE INDEX IF NOT EXISTS idx_device_license ON devices(license_id);
CREATE INDEX IF NOT EXISTS idx_device_id ON devices(device_id);
