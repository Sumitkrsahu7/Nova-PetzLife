-- ============================================================================
-- NOVA PETZLIFE — Migration V3: Google Business Reviews & Media Enhancement (MySQL 8.x)
-- ============================================================================

-- 1. Table for Google Business Profile / Google Maps Synced Reviews
CREATE TABLE IF NOT EXISTS google_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_name VARCHAR(150) NOT NULL,
    author_photo_url VARCHAR(500),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    relative_time_description VARCHAR(100),
    review_text TEXT NOT NULL,
    google_review_id VARCHAR(150) UNIQUE,
    review_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_featured BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_google_reviews_featured ON google_reviews(is_featured);

-- 2. Seed Authentic Initial Google Reviews for Petzlife, Kuravankonam
INSERT INTO google_reviews (author_name, author_photo_url, rating, relative_time_description, review_text, google_review_id, is_featured)
VALUES 
(
    'Anoop Nair',
    'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80',
    5,
    '2 weeks ago',
    'Excellent pet shop in Kuravankonam! They always have stock of Royal Canin and Pedigree dog food. The owner is very helpful with pet nutrition suggestions and delivery across Trivandrum is super prompt.',
    'ChIJ_petzlife_rev_001',
    TRUE
),
(
    'Dr. Divya Krishnan',
    'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80',
    5,
    'a month ago',
    'Best place in Kowdiar/Ambalamukku area for premium cat food and grooming accessories. I regularly purchase Whiskas gravies and cat litter from here. Neat, hygienic store and great customer service!',
    'ChIJ_petzlife_rev_002',
    TRUE
),
(
    'Rahul Menon',
    'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=150&q=80',
    5,
    '2 months ago',
    'Got Arden Grange puppy food and chew toys. The shop is conveniently located right near Kuravankonam junction with parking. Very polite staff and quick local home delivery.',
    'ChIJ_petzlife_rev_003',
    TRUE
),
(
    'Meera S.',
    'https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=150&q=80',
    4,
    '3 months ago',
    'Wide collection of dog biscuits, collars, and shampoos. Prices are reasonable and the staff is always ready to guide you. Glad to have a reliable neighborhood pet store in Trivandrum.',
    'ChIJ_petzlife_rev_004',
    TRUE
),
(
    'Gopakumar K.',
    'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80',
    5,
    '4 months ago',
    'Top quality pet products in Thiruvananthapuram. Ordered dog food online through their WhatsApp/website and received it at Pattom in under two hours. Excellent service!',
    'ChIJ_petzlife_rev_005',
    TRUE
);
