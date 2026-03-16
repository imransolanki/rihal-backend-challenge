-- Add version column to slots table for optimistic locking
ALTER TABLE slots ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
