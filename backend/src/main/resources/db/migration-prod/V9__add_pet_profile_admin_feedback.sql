-- V9__add_pet_profile_admin_feedback.sql
-- Add optional admin feedback and timestamp to pet_profiles table

ALTER TABLE pet_profiles ADD COLUMN admin_feedback TEXT NULL;

ALTER TABLE pet_profiles ADD COLUMN admin_feedback_updated_at DATETIME NULL;
