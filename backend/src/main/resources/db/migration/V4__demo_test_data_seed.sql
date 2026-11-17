-- =============================================================================
-- NOVA PETZLIFE — Flyway Migration V4: Comprehensive Realistic Demo Seed Data
-- Store: Petzlife, Kuravankonam, Thiruvananthapuram (8.528448, 76.953268)
-- Admin: test@gmail.com / Test@123 (ROLE_SUPER_ADMIN)
-- =============================================================================

-- 1. Development Administrator & Staff Accounts
INSERT IGNORE INTO users (id, email, password_hash, first_name, last_name, phone, is_active, created_at, updated_at) VALUES
(1, 'test@gmail.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Super', 'Admin', '9876543200', TRUE, NOW(), NOW()),
(2, 'staff@novapetzlife.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Sujith', 'Kumar', '9876543201', TRUE, NOW(), NOW());

-- Assign Admin Roles
INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
(1, 1), -- test@gmail.com -> ROLE_SUPER_ADMIN
(2, 2); -- staff@novapetzlife.com -> ROLE_STAFF_ADMIN

-- 2. Customer User Accounts
INSERT IGNORE INTO users (id, email, password_hash, first_name, last_name, phone, is_active, created_at, updated_at) VALUES
(3, 'anand.nair@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Anand', 'Nair', '9847012345', TRUE, NOW(), NOW()),
(4, 'priya.kurup@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Priya', 'Kurup', '9847123456', TRUE, NOW(), NOW()),
(5, 'arun.menoki@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Arun', 'Menoki', '9847234567', TRUE, NOW(), NOW()),
(6, 'deepa.pillai@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Deepa', 'Pillai', '9847345678', TRUE, NOW(), NOW()),
(7, 'suresh.kumar@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Suresh', 'Kumar', '9847456789', TRUE, NOW(), NOW()),
(8, 'reshma.varma@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Reshma', 'Varma', '9847567890', TRUE, NOW(), NOW()),
(9, 'vinod.george@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Vinod', 'George', '9847678901', TRUE, NOW(), NOW()),
(10, 'anjali.menon@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Anjali', 'Menon', '9847789012', TRUE, NOW(), NOW()),
(11, 'karthik.s@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Karthik', 'Sreedhar', '9847890123', TRUE, NOW(), NOW()),
(12, 'meera.nambiar@example.com', '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', 'Meera', 'Nambiar', '9847901234', TRUE, NOW(), NOW());

-- Assign Customer Role (ROLE_CUSTOMER id=3)
INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
(3, 3),
(4, 3),
(5, 3),
(6, 3),
(7, 3),
(8, 3),
(9, 3),
(10, 3),
(11, 3),
(12, 3);

-- 3. Customer Entities
INSERT IGNORE INTO customers (id, user_id, email, phone, first_name, last_name, is_guest, created_at, updated_at) VALUES
(1, 3, 'anand.nair@example.com', '9847012345', 'Anand', 'Nair', FALSE, NOW(), NOW()),
(2, 4, 'priya.kurup@example.com', '9847123456', 'Priya', 'Kurup', FALSE, NOW(), NOW()),
(3, 5, 'arun.menoki@example.com', '9847234567', 'Arun', 'Menoki', FALSE, NOW(), NOW()),
(4, 6, 'deepa.pillai@example.com', '9847345678', 'Deepa', 'Pillai', FALSE, NOW(), NOW()),
(5, 7, 'suresh.kumar@example.com', '9847456789', 'Suresh', 'Kumar', FALSE, NOW(), NOW()),
(6, 8, 'reshma.varma@example.com', '9847567890', 'Reshma', 'Varma', FALSE, NOW(), NOW()),
(7, 9, 'vinod.george@example.com', '9847678901', 'Vinod', 'George', FALSE, NOW(), NOW()),
(8, 10, 'anjali.menon@example.com', '9847789012', 'Anjali', 'Menon', FALSE, NOW(), NOW()),
(9, 11, 'karthik.s@example.com', '9847890123', 'Karthik', 'Sreedhar', FALSE, NOW(), NOW()),
(10, 12, 'meera.nambiar@example.com', '9847901234', 'Meera', 'Nambiar', FALSE, NOW(), NOW());

-- 4. Customer Delivery Addresses (Trivandrum Locations within 20km)
INSERT IGNORE INTO customer_addresses (id, customer_id, recipient_name, phone, address_line1, address_line2, landmark, city, state, postal_code, latitude, longitude, is_default, is_serviceable, created_at, updated_at) VALUES
(1, 1, 'Anand Nair', '9847012345', 'Villa 12, Kowdiar Gardens', 'Near Kowdiar Palace', 'Kowdiar', 'Thiruvananthapuram', 'Kerala', '695003', 8.5241, 76.9612, TRUE, TRUE, NOW(), NOW()),
(2, 2, 'Priya Kurup', '9847123456', 'Flat 4B, Heera Heights', 'Pattom Palace Road', 'Pattom', 'Thiruvananthapuram', 'Kerala', '695004', 8.5312, 76.9421, TRUE, TRUE, NOW(), NOW()),
(3, 3, 'Arun Menoki', '9847234567', 'TC 14/890, Kuravankonam', 'Opposite Petzlife Store', 'Kuravankonam', 'Thiruvananthapuram', 'Kerala', '695003', 8.5286, 76.9535, TRUE, TRUE, NOW(), NOW()),
(4, 4, 'Deepa Pillai', '9847345678', 'House 45, Sasthamangalam', 'Pipamoodu Junction', 'Sasthamangalam', 'Thiruvananthapuram', 'Kerala', '695010', 8.5178, 76.9723, TRUE, TRUE, NOW(), NOW()),
(5, 5, 'Suresh Kumar', '9847456789', 'A-102, SFS Cyber Palms', 'Technopark Phase 1', 'Kazhakkoottam', 'Thiruvananthapuram', 'Kerala', '695581', 8.5582, 76.8791, TRUE, TRUE, NOW(), NOW()),
(6, 6, 'Reshma Varma', '9847567890', 'Green Valley House 7', 'Ambalamukku Road', 'Ambalamukku', 'Thiruvananthapuram', 'Kerala', '695005', 8.5354, 76.9601, TRUE, TRUE, NOW(), NOW()),
(7, 7, 'Vinod George', '9847678901', 'Rose Cottage, Medical College PO', 'Near Cosmo Hospital', 'Murinjapalam', 'Thiruvananthapuram', 'Kerala', '695011', 8.5212, 76.9312, TRUE, TRUE, NOW(), NOW()),
(8, 8, 'Anjali Menon', '9847789012', 'TC 9/112, Jawahar Nagar', 'Near Club', 'Vellayambalam', 'Thiruvananthapuram', 'Kerala', '695010', 8.5145, 76.9645, TRUE, TRUE, NOW(), NOW()),
(9, 9, 'Karthik Sreedhar', '9847890123', 'Skyline Apartment 6A', 'MG Road', 'Palayam', 'Thiruvananthapuram', 'Kerala', '695034', 8.5023, 76.9512, TRUE, TRUE, NOW(), NOW()),
(10, 10, 'Meera Nambiar', '9847901234', 'Palm Grove Villa', 'Peroorkada Junction', 'Peroorkada', 'Thiruvananthapuram', 'Kerala', '695005', 8.5412, 76.9741, TRUE, TRUE, NOW(), NOW());

-- 5. Customer Pet Profiles
INSERT IGNORE INTO pet_profiles (id, customer_id, pet_name, pet_type, breed, date_of_birth, gender, weight_kg, life_stage, dietary_preferences, food_sensitivities, created_at, updated_at) VALUES
(1, 1, 'Rocky', 'DOG', 'Golden Retriever', '2021-04-10', 'MALE', 32.5, 'ADULT', 'Loves dry chicken kibble', 'None', NOW(), NOW()),
(2, 2, 'Bella', 'CAT', 'Persian Cat', '2022-08-15', 'FEMALE', 4.2, 'ADULT', 'Prefers ocean fish wet gravy', 'Sensitive stomach', NOW(), NOW()),
(3, 3, 'Max', 'DOG', 'Beagle', '2023-01-20', 'MALE', 12.0, 'PUPPY', 'Hypoallergenic salmon diet', 'Grain allergy', NOW(), NOW()),
(4, 4, 'Milo', 'CAT', 'British Shorthair', '2023-05-12', 'MALE', 3.8, 'KITTEN', 'High protein tuna & chicken', 'None', NOW(), NOW()),
(5, 5, 'Leo', 'DOG', 'Labrador Retriever', '2020-11-05', 'MALE', 35.0, 'ADULT', 'Weight management kibble', 'None', NOW(), NOW()),
(6, 6, 'Coco', 'BIRD', 'Cockatiel', '2022-03-01', 'FEMALE', 0.09, 'ADULT', 'Millets and dried seeds', 'None', NOW(), NOW());

-- 6. Brands (10 Premium Brands)
INSERT IGNORE INTO brands (id, name, slug, description, logo_url, is_active, created_at, updated_at) VALUES
(1, 'Royal Canin', 'royal-canin', 'Tailored nutrition for cats and dogs based on breed, size, and specific needs.', 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?w=400', TRUE, NOW(), NOW()),
(2, 'Pedigree', 'pedigree', 'Wholesome, complete, and balanced nutrition for dogs of all sizes and life stages.', 'https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=400', TRUE, NOW(), NOW()),
(3, 'Whiskas', 'whiskas', 'Nutritious and delicious wet and dry food specially crafted for feline vitality.', 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=400', TRUE, NOW(), NOW()),
(4, 'Drools', 'drools', 'Premium nutrition enriched with real chicken, vitamins, and organic minerals.', 'https://images.unsplash.com/photo-1587300003388-59208cc962cb?w=400', TRUE, NOW(), NOW()),
(5, 'Arden Grange', 'arden-grange', 'Naturally hypoallergenic pet food made with human-grade fresh ingredients.', 'https://images.unsplash.com/photo-1537151608828-ea2b11777ee8?w=400', TRUE, NOW(), NOW()),
(6, 'Farmina N&D', 'farmina-nd', 'Natural & Delicious ancestral grain-free Italian formulated pet nutrition.', 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=400', TRUE, NOW(), NOW()),
(7, 'Orijen', 'orijen', 'Biologically appropriate nutrition with fresh regional Canadian ingredients.', 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=400', TRUE, NOW(), NOW()),
(8, 'Himalaya Pet', 'himalaya-pet', 'Herbal and ayurvedic healthcare, grooming, and supplements for pets.', 'https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?w=400', TRUE, NOW(), NOW()),
(9, 'Kong', 'kong', 'Durable, high-grade natural rubber pet toys and mental enrichment accessories.', 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=400', TRUE, NOW(), NOW()),
(10, 'Catit', 'catit', 'Innovative cat wellness products, drinking fountains, and sensory play circuits.', 'https://images.unsplash.com/photo-1526336024174-e58f5cdd8e13?w=400', TRUE, NOW(), NOW()),
(11, 'Sheba', 'sheba', 'Gourmet, irresistibly delicate wet cat food recipes crafted for feline epicures.', 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=400', TRUE, NOW(), NOW());

-- 7. Categories (16 Hierarchical Categories)
INSERT IGNORE INTO categories (id, parent_id, name, slug, description, image_url, display_order, is_active, created_at, updated_at) VALUES
(1, NULL, 'Dogs', 'dogs', 'Complete nutrition, grooming, toys, and care for dogs', 'https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=400', 1, TRUE, NOW(), NOW()),
(2, 1, 'Dog Food', 'dog-food', 'Dry kibble and savory wet food for puppies, adults, and seniors', 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?w=400', 2, TRUE, NOW(), NOW()),
(3, 2, 'Dry Dog Food', 'dry-dog-food', 'Crunchy, balanced dry dog food rich in protein', 'https://images.unsplash.com/photo-1568640347023-a616a30bc3bd?w=400', 3, TRUE, NOW(), NOW()),
(4, 2, 'Wet Dog Food', 'wet-dog-food', 'Moist gravy cans and pouches bursting with flavor', 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?w=400', 4, TRUE, NOW(), NOW()),
(5, 2, 'Puppy Food', 'puppy-food', 'Specially balanced growth formulas with DHA for developing pups', 'https://images.unsplash.com/photo-1591769225440-811ad7d6eab2?w=400', 5, TRUE, NOW(), NOW()),
(6, 1, 'Dog Treats & Chews', 'dog-treats', 'Delicious training treats, dental sticks, and calcium chews', 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=400', 6, TRUE, NOW(), NOW()),
(7, 1, 'Dog Grooming', 'dog-grooming', 'Shampoos, conditioners, de-shedding brushes, and paw balms', 'https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=400', 7, TRUE, NOW(), NOW()),
(8, 1, 'Dog Toys & Accessories', 'dog-toys', 'Interactive balls, tough chew toys, leashes, and collars', 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=400', 8, TRUE, NOW(), NOW()),
(9, NULL, 'Cats', 'cats', 'Feline food, litter, scratchers, toys, and grooming essentials', 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=400', 9, TRUE, NOW(), NOW()),
(10, 9, 'Cat Food', 'cat-food', 'Nutritious dry kibbles and savory gravies tailored for cats', 'https://images.unsplash.com/photo-1574158622682-e40e69881006?w=400', 10, TRUE, NOW(), NOW()),
(11, 10, 'Dry Cat Food', 'dry-cat-food', 'Taurine-rich crunchy kibble for hairball and urinary tract care', 'https://images.unsplash.com/photo-1535930891776-0c2dfb7fda1a?w=400', 11, TRUE, NOW(), NOW()),
(12, 10, 'Wet Cat Food', 'wet-cat-food', 'Tender fish, chicken, and tuna chunks in rich jelly and gravy', 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=400', 12, TRUE, NOW(), NOW()),
(13, 9, 'Cat Litter & Clean', 'cat-litter', 'Bentonite clumping litter, odor-control crystals, and scoops', 'https://images.unsplash.com/photo-1563460716037-460b3dd145dc?w=400', 13, TRUE, NOW(), NOW()),
(14, 9, 'Cat Toys & Scratchers', 'cat-toys', 'Catnip mice, feather wands, laser toys, and sisal scratching posts', 'https://images.unsplash.com/photo-1526336024174-e58f5cdd8e13?w=400', 14, TRUE, NOW(), NOW()),
(15, NULL, 'Birds & Aquatic', 'birds-aquatic', 'Nutritious feed and healthcare for companion birds and aquarium fish', 'https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=400', 15, TRUE, NOW(), NOW()),
(16, 15, 'Fish Food & Supplements', 'fish-food', 'Floating flakes, spirulina pellets, and water conditioners', 'https://images.unsplash.com/photo-1520315342629-6ea920342047?w=400', 16, TRUE, NOW(), NOW());

-- 8. Products (45 Curated Commercial Pet Products)
INSERT IGNORE INTO products (id, brand_id, category_id, name, slug, description, sku, base_price, discounted_price, pet_type, life_stage, product_type, is_active, is_featured, created_at, updated_at) VALUES
(1, 1, 3, 'Royal Canin Maxi Adult Dry Dog Food', 'royal-canin-maxi-adult-dog-food', 'Complete food for adult large breed dogs (from 26 to 44 kg) from 15 months to 5 years old. Supports bone & joint health.', 'RC-MAXI-ADULT-4KG', 3200.0, 2899.0, 'DOG', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(2, 1, 5, 'Royal Canin Mini Puppy Dry Dog Food', 'royal-canin-mini-puppy-food', 'Designed for small breed puppies up to 10 months old with adult weight up to 10kg. Boosts immune system with vitamins.', 'RC-MINI-PUP-2KG', 1950.0, 1755.0, 'DOG', 'PUPPY', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(3, 2, 3, 'Pedigree Adult Chicken & Vegetables Dry Dog Food', 'pedigree-adult-chicken-vegetables', 'Time-tested complete nutrition with 20% crude protein, 10% crude fat, and essential minerals for healthy teeth and coat.', 'PED-ADULT-CHK-3KG', 850.0, 765.0, 'DOG', 'ADULT', 'Dry Food', TRUE, FALSE, NOW(), NOW()),
(4, 2, 5, 'Pedigree Puppy Chicken & Milk Dry Food', 'pedigree-puppy-chicken-milk', 'Special puppy formulation with easy-to-digest kibbles, calcium, and prebiotics for rapid physical and cognitive development.', 'PED-PUP-MILK-3KG', 920.0, 828.0, 'DOG', 'PUPPY', 'Dry Food', TRUE, FALSE, NOW(), NOW()),
(5, 4, 3, 'Drools Focus Adult Super Premium Dog Food', 'drools-focus-adult-super-premium', 'No corn, wheat, or soy. Real chicken as the first ingredient for optimal muscle build, omega 3 & 6 for shining coat.', 'DRL-FOC-ADL-4KG', 1850.0, 1665.0, 'DOG', 'ADULT', 'Dry Food', TRUE, FALSE, NOW(), NOW()),
(6, 4, 5, 'Drools Puppy Starter Food', 'drools-puppy-starter-food', 'Transition nutrition for weaning puppies from 3 weeks to 3 months. Highly digestible probiotics protect delicate tummies.', 'DRL-PUP-START-1KG', 499.0, 449.0, 'DOG', 'PUPPY', 'Dry Food', TRUE, FALSE, NOW(), NOW()),
(7, 5, 3, 'Arden Grange Adult Dog Fresh Chicken & Rice', 'arden-grange-adult-chicken-rice', 'Naturally hypoallergenic British dog food made with ethical human-grade fresh chicken and cranberries for urinary tract support.', 'AG-ADL-CHK-2KG', 2400.0, 2160.0, 'DOG', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(8, 6, 3, 'Farmina N&D Prime Grain-Free Chicken & Pomegranate', 'farmina-nd-prime-chicken-pomegranate', 'Ultra-premium 98% animal protein recipe formulated in Italy. Zero grains with natural antioxidants and cold-pressed vitamins.', 'FND-CHK-POM-2.5KG', 3450.0, 3105.0, 'DOG', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(9, 7, 3, 'Orijen Original Adult Dog Food', 'orijen-original-dog-food', 'Biologically appropriate nutrition with 85% poultry, fish, and cage-free eggs. Two-thirds fresh or raw Canadian ingredients.', 'ORJ-ORIG-2KG', 4200.0, 3899.0, 'DOG', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(10, 1, 4, 'Royal Canin Maxi Adult Gravy Wet Pouch', 'royal-canin-maxi-adult-gravy', 'Nutritious chunks in savory gravy supporting digestive health and balanced intestinal flora for large breed dogs (140g).', 'RC-WET-MAXI-140G', 150.0, 135.0, 'DOG', 'ADULT', 'Wet Food', TRUE, FALSE, NOW(), NOW()),
(11, 2, 4, 'Pedigree Adult Wet Dog Food Chicken & Liver Chunks', 'pedigree-wet-chicken-liver-chunks', 'Succulent meat chunks in loaf gravy, moisture-rich to keep dogs well hydrated and excited for every mealtime (400g can).', 'PED-WET-CAN-400G', 140.0, 126.0, 'DOG', 'ADULT', 'Wet Food', TRUE, FALSE, NOW(), NOW()),
(12, 4, 4, 'Drools Real Chicken Sausage Treat Pack', 'drools-chicken-sausage-pack', 'Palatable soft sausages made from 100% real farm-raised chicken, free of artificial colors, ideal for meal toppers (100g).', 'DRL-SAUS-100G', 80.0, 72.0, 'DOG', 'ALL_LIFE_STAGES', 'Wet Food', TRUE, FALSE, NOW(), NOW()),
(13, 2, 6, 'Pedigree Dentastix Daily Dental Dog Chews', 'pedigree-dentastix-medium-chews', 'Triple action dental chew with unique X-shape design clinically proven to reduce tartar and plaque build-up by up to 80%.', 'PED-DENT-MED-7PC', 220.0, 198.0, 'DOG', 'ADULT', 'Treats', TRUE, TRUE, NOW(), NOW()),
(14, 4, 6, 'Drools Absolute Calcium Bone Treats', 'drools-calcium-bone-treats', 'Enriched with calcium, phosphorus, and essential vitamins to promote bone density and healthy teeth in active dogs (20 pcs).', 'DRL-CALC-BONE-20PC', 350.0, 315.0, 'DOG', 'ALL_LIFE_STAGES', 'Treats', TRUE, FALSE, NOW(), NOW()),
(15, 8, 6, 'Himalaya Healthy Pet Treats Chicken & Milk', 'himalaya-pet-treats-chicken-milk', 'Herbal ayurvedic treats fortified with flaxseeds for shiny coat, immune strength, and smooth digestion (500g).', 'HIM-TREAT-500G', 260.0, 234.0, 'DOG', 'ALL_LIFE_STAGES', 'Treats', TRUE, FALSE, NOW(), NOW()),
(16, 8, 7, 'Himalaya Erina-EP Anti-Tick & Flea Dog Shampoo', 'himalaya-erina-ep-shampoo', 'Herbal neem and eucalyptus shampoo preventing ectoparasite infestations like ticks, fleas, and lice with gentle conditioning (200ml).', 'HIM-SHAMP-200ML', 210.0, 189.0, 'DOG', 'ALL_LIFE_STAGES', 'Grooming', TRUE, TRUE, NOW(), NOW()),
(17, 8, 7, 'Himalaya Scavon Wound Healing Herbal Spray', 'himalaya-scavon-herbal-spray', 'Natural antimicrobial and antifungal spray providing rapid relief from cuts, abrasions, fly repellence, and skin lesions (100ml).', 'HIM-SCAV-100ML', 175.0, 157.0, 'DOG', 'ALL_LIFE_STAGES', 'Grooming', TRUE, FALSE, NOW(), NOW()),
(18, 9, 8, 'Kong Classic Natural Rubber Durable Dog Toy', 'kong-classic-rubber-toy', 'Ultra-durable red natural rubber toy with erratic bounce for fetch games and stuffing with treats to prevent separation anxiety.', 'KONG-CLASSIC-L', 1250.0, 1125.0, 'DOG', 'ALL_LIFE_STAGES', 'Toys', TRUE, TRUE, NOW(), NOW()),
(19, 9, 8, 'Kong Squeezz Dental Ball Dog Toy', 'kong-squeezz-dental-ball', 'Textured grooves clean teeth and gums while recessed squeaker entices long playtime sessions (Medium).', 'KONG-DENT-BALL-M', 850.0, 765.0, 'DOG', 'ALL_LIFE_STAGES', 'Toys', TRUE, FALSE, NOW(), NOW()),
(20, 9, 8, 'Kong Extreme Black Ultra-Tough Rubber Chew', 'kong-extreme-black-chew', 'Specially engineered with Kong''s toughest black rubber formula for master chewers and powerful jaws (X-Large).', 'KONG-EXTR-XL', 1650.0, 1485.0, 'DOG', 'ADULT', 'Toys', TRUE, FALSE, NOW(), NOW()),
(21, 1, 11, 'Royal Canin Fit 32 Adult Dry Cat Food', 'royal-canin-fit-32-cat-food', 'Complete balanced nutrition for moderately active adult cats (1 to 7 years old). Regulates hairball elimination and urinary health.', 'RC-FIT32-2KG', 1850.0, 1665.0, 'CAT', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(22, 1, 11, 'Royal Canin Kitten Dry Cat Food', 'royal-canin-kitten-food', 'Specifically crafted for kittens during their second age growth phase (up to 12 months). High protein and digestive enzymes.', 'RC-KIT-2KG', 1990.0, 1791.0, 'CAT', 'KITTEN', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(23, 3, 11, 'Whiskas Adult Ocean Fish Dry Cat Food', 'whiskas-adult-ocean-fish', 'Crispy pockets filled with real ocean fish flavor, taurine for eyesight, and vitamin E for shiny, silky fur (3kg bag).', 'WHIS-DRY-OF-3KG', 990.0, 891.0, 'CAT', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(24, 3, 11, 'Whiskas Junior Ocean Fish with Milk Kitten Food', 'whiskas-junior-ocean-fish-milk', 'Micro kibbles easy to chew for kittens 2 to 12 months. Enriched with calcium, phosphorus, and DHA for vital growth (1.1kg).', 'WHIS-JUN-OF-1.1KG', 420.0, 378.0, 'CAT', 'KITTEN', 'Dry Food', TRUE, FALSE, NOW(), NOW()),
(25, 4, 11, 'Drools Ocean Up Adult Dry Cat Food', 'drools-ocean-up-cat-food', 'Crunchy kibble packed with ocean fish, sardine, and mackerel. Enriched with organic taurine and balanced magnesium (3kg).', 'DRL-CAT-OF-3KG', 799.0, 719.0, 'CAT', 'ADULT', 'Dry Food', TRUE, FALSE, NOW(), NOW()),
(26, 6, 11, 'Farmina N&D Cat Ocean Herring & Orange Grain-Free', 'farmina-nd-cat-herring-orange', 'Italian recipe with wild-caught Atlantic herring and sweet Sicilian oranges. 96% protein of animal origin, 0% artificial binders.', 'FND-CAT-HERR-1.5KG', 2650.0, 2385.0, 'CAT', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(27, 7, 11, 'Orijen Cat & Kitten Biologically Appropriate Food', 'orijen-cat-kitten-food', 'WholePrey ratio of free-run chicken, turkey, wild fish, and nest-laid eggs providing nutrient-dense nourishment (1.8kg).', 'ORJ-CAT-KIT-1.8KG', 3800.0, 3420.0, 'CAT', 'ALL_LIFE_STAGES', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(28, 3, 12, 'Whiskas Wet Cat Food Tuna in Jelly Pouch', 'whiskas-wet-tuna-jelly-pouch', 'Appetizing shredded tuna morsels suspended in flavorful jelly, delivering 85% hydration and full taste (85g pouch).', 'WHIS-WET-TUNA-85G', 50.0, 45.0, 'CAT', 'ADULT', 'Wet Food', TRUE, TRUE, NOW(), NOW()),
(29, 3, 12, 'Whiskas Wet Cat Food Chicken in Gravy Pouch', 'whiskas-wet-chicken-gravy-pouch', 'Tender chicken pieces simmered in thick gravy, enriched with zinc and omega-6 fatty acids for a glossy coat (85g pouch).', 'WHIS-WET-CHK-85G', 50.0, 45.0, 'CAT', 'ADULT', 'Wet Food', TRUE, FALSE, NOW(), NOW()),
(30, 1, 12, 'Royal Canin Intense Beauty Gravy Wet Pouch', 'royal-canin-intense-beauty-gravy', 'Nutritional loaf in gravy designed to nourish skin and intensify coat sheen with elevated levels of omega 3 & 6 (85g).', 'RC-WET-BEAUTY-85G', 130.0, 117.0, 'CAT', 'ADULT', 'Wet Food', TRUE, FALSE, NOW(), NOW()),
(31, 10, 13, 'Catit Go Natural Pea Husk Clumping Cat Litter', 'catit-go-natural-pea-husk-litter', 'Eco-friendly, dust-free cat litter made from recycled pea fibers. Clumps instantly and flushes effortlessly (14L bag).', 'CATIT-LIT-PEA-14L', 1850.0, 1665.0, 'CAT', 'ALL_LIFE_STAGES', 'Litter', TRUE, TRUE, NOW(), NOW()),
(32, 10, 13, 'Catit Fresh Clumping Bentonite Lavender Litter', 'catit-bentonite-lavender-litter', 'High absorption natural sodium bentonite litter infused with calming lavender fragrance for 99% odor control (10kg).', 'CATIT-LIT-LAV-10KG', 790.0, 711.0, 'CAT', 'ALL_LIFE_STAGES', 'Litter', TRUE, FALSE, NOW(), NOW()),
(33, 10, 14, 'Catit Senses 2.0 Circuit Interactive Play Track', 'catit-senses-play-circuit', 'Modular motion track with peek-a-boo openings and illuminated ball that rolls when swiped by cats.', 'CATIT-CIRCUIT-PLAY', 1450.0, 1305.0, 'CAT', 'ALL_LIFE_STAGES', 'Toys', TRUE, TRUE, NOW(), NOW()),
(34, 10, 14, 'Catit Flower Drinking Fountain with Water Filter', 'catit-flower-drinking-fountain', '3-liter recirculating drinking fountain with multi-stage triple action filter encouraging cats to drink fresh oxygenated water.', 'CATIT-FOUNT-3L', 2450.0, 2205.0, 'CAT', 'ALL_LIFE_STAGES', 'Accessories', TRUE, TRUE, NOW(), NOW()),
(35, 9, 14, 'Kong Cat Wobbler Interactive Treat Dispenser', 'kong-cat-wobbler-dispenser', 'Weighted toy that sits upright and dispenses dry treats as cats bat and paw it around the room.', 'KONG-CAT-WOBB', 980.0, 882.0, 'CAT', 'ALL_LIFE_STAGES', 'Toys', TRUE, FALSE, NOW(), NOW()),
(36, 8, 16, 'Himalaya Pet Care Aqua-Vit Fish Tonic', 'himalaya-aqua-vit-tonic', 'Multi-vitamin water supplement with spirulina to enhance vitality, fish appetite, and vibrant coloration (100ml).', 'HIM-AQUA-VIT-100', 150.0, 135.0, 'FISH', 'ALL_LIFE_STAGES', 'Supplements', TRUE, FALSE, NOW(), NOW()),
(37, 4, 16, 'Drools Floating Fish Food Pellets', 'drools-fish-food-pellets', 'Spirulina-rich floating pellets that do not cloud aquarium water, promoting immunity and vibrant fin coloring (330g).', 'DRL-FISH-330G', 180.0, 162.0, 'FISH', 'ALL_LIFE_STAGES', 'Feed', TRUE, FALSE, NOW(), NOW()),
(38, 8, 16, 'Himalaya Bird Care Mineral Calcium Block', 'himalaya-bird-calcium-block', 'Natural cuttlebone and calcium grit block for caged budgies, canaries, and cockatiels to condition beaks and strengthen bones.', 'HIM-BIRD-CALC', 110.0, 99.0, 'BIRD', 'ALL_LIFE_STAGES', 'Supplements', TRUE, FALSE, NOW(), NOW()),
(39, 1, 3, 'Royal Canin Golden Retriever Adult Dry Food', 'royal-canin-golden-retriever-adult', 'Breed-specific formula with borage oil and biotin to support the Golden Retriever''s dense, water-resistant golden coat (3kg).', 'RC-GOLDEN-3KG', 3100.0, 2790.0, 'DOG', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(40, 1, 3, 'Royal Canin German Shepherd Adult Dry Food', 'royal-canin-german-shepherd-adult', 'Targeted digestive security formula with highly digestible L.I.P proteins and copra oil for sensitive German Shepherds (3kg).', 'RC-GSD-3KG', 3100.0, 2790.0, 'DOG', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(41, 1, 11, 'Royal Canin Persian Cat Adult Dry Food', 'royal-canin-persian-adult', 'Almond-shaped kibbles tailored for brachycephalic jaw pickup, with long-hair hairball transit complex (2kg).', 'RC-PERS-2KG', 2150.0, 1935.0, 'CAT', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(42, 6, 3, 'Farmina N&D Pumpkin Lamb & Blueberry Dog Food', 'farmina-nd-pumpkin-lamb-blueberry', 'Grain-free formula with 96% animal protein, fresh lamb, and high fiber pumpkin to optimize gastrointestinal digestion (2.5kg).', 'FND-LAMB-BLUE-2.5KG', 3600.0, 3240.0, 'DOG', 'ADULT', 'Dry Food', TRUE, TRUE, NOW(), NOW()),
(43, 8, 7, 'Himalaya Fresh Coat Dry Waterless Dog Spray', 'himalaya-fresh-coat-spray', 'Convenient no-rinse waterless spray formulated with herbal badam and jambira to eliminate odors between baths (150ml).', 'HIM-FRESH-150ML', 195.0, 175.0, 'DOG', 'ALL_LIFE_STAGES', 'Grooming', TRUE, FALSE, NOW(), NOW()),
(44, 9, 8, 'Kong Wild Knots Bear Soft Chew Toy', 'kong-wild-knots-bear', 'Soft plush exterior with internal knotted rope skeleton satisfies natural thrashing instincts with minimal stuffing (M/L).', 'KONG-WILD-BEAR', 950.0, 855.0, 'DOG', 'ALL_LIFE_STAGES', 'Toys', TRUE, FALSE, NOW(), NOW()),
(45, 10, 14, 'Catit Catnip Spray Pure Canadian Herbal Mist', 'catit-catnip-spray-mist', 'Concentrated distilled Canadian catnip extract to revitalize scratchers and attract cats to their toys (60ml).', 'CATIT-CATNIP-60ML', 450.0, 405.0, 'CAT', 'ALL_LIFE_STAGES', 'Accessories', TRUE, FALSE, NOW(), NOW()),
(46, 11, 12, 'Sheba Premium Wet Cat Food Tuna & Salmon Fillet in Gravy', 'sheba-premium-tuna-salmon-gravy', 'Gourmet tender flakes of tuna and salmon steeped in rich savory gravy for adult cats (70g can).', 'SHB-WET-TUNA-70G', 95.0, 85.0, 'CAT', 'ADULT', 'Wet Food', TRUE, TRUE, NOW(), NOW()),
(47, 11, 12, 'Sheba Melty Maguro & Katsuo Creamy Cat Treats', 'sheba-melty-creamy-cat-treats', 'Irresistible creamy puree licking treats with real Japanese maguro tuna and katsuo bonito (4 x 12g sachets).', 'SHB-MELTY-48G', 160.0, 144.0, 'CAT', 'ALL_LIFE_STAGES', 'Treats', TRUE, TRUE, NOW(), NOW());

-- 9. Product Images
INSERT IGNORE INTO product_images (id, product_id, image_url, alt_text, display_order, is_primary, created_at) VALUES
(1, 1, 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?w=600', 'Royal Canin Maxi Adult', 1, TRUE, NOW()),
(2, 2, 'https://images.unsplash.com/photo-1591769225440-811ad7d6eab2?w=600', 'Royal Canin Mini Puppy', 1, TRUE, NOW()),
(3, 3, 'https://images.unsplash.com/photo-1568640347023-a616a30bc3bd?w=600', 'Pedigree Adult Chicken', 1, TRUE, NOW()),
(4, 4, 'https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=600', 'Pedigree Puppy Food', 1, TRUE, NOW()),
(5, 5, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?w=600', 'Drools Focus Adult', 1, TRUE, NOW()),
(6, 6, 'https://images.unsplash.com/photo-1587300003388-59208cc962cb?w=600', 'Drools Puppy Starter', 1, TRUE, NOW()),
(7, 7, 'https://images.unsplash.com/photo-1537151608828-ea2b11777ee8?w=600', 'Arden Grange Chicken', 1, TRUE, NOW()),
(8, 8, 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=600', 'Farmina ND Prime', 1, TRUE, NOW()),
(9, 9, 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=600', 'Orijen Original Adult', 1, TRUE, NOW()),
(10, 10, 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?w=600', 'Royal Canin Maxi Gravy', 1, TRUE, NOW()),
(11, 11, 'https://images.unsplash.com/photo-1568640347023-a616a30bc3bd?w=600', 'Pedigree Wet Food Can', 1, TRUE, NOW()),
(12, 12, 'https://images.unsplash.com/photo-1587300003388-59208cc962cb?w=600', 'Drools Sausage Treats', 1, TRUE, NOW()),
(13, 13, 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=600', 'Dentastix Chews', 1, TRUE, NOW()),
(14, 14, 'https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=600', 'Calcium Bones', 1, TRUE, NOW()),
(15, 15, 'https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?w=600', 'Himalaya Pet Treats', 1, TRUE, NOW()),
(16, 16, 'https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=600', 'Himalaya Anti-Tick Shampoo', 1, TRUE, NOW()),
(17, 17, 'https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?w=600', 'Himalaya Scavon Spray', 1, TRUE, NOW()),
(18, 18, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=600', 'Kong Classic Toy', 1, TRUE, NOW()),
(19, 19, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=600', 'Kong Squeezz Ball', 1, TRUE, NOW()),
(20, 20, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=600', 'Kong Extreme Chew', 1, TRUE, NOW()),
(21, 21, 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600', 'Royal Canin Fit 32', 1, TRUE, NOW()),
(22, 22, 'https://images.unsplash.com/photo-1574158622682-e40e69881006?w=600', 'Royal Canin Kitten', 1, TRUE, NOW()),
(23, 23, 'https://images.unsplash.com/photo-1535930891776-0c2dfb7fda1a?w=600', 'Whiskas Ocean Fish', 1, TRUE, NOW()),
(24, 24, 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600', 'Whiskas Junior Food', 1, TRUE, NOW()),
(25, 25, 'https://images.unsplash.com/photo-1574158622682-e40e69881006?w=600', 'Drools Ocean Up Cat', 1, TRUE, NOW()),
(26, 26, 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=600', 'Farmina Cat Herring', 1, TRUE, NOW()),
(27, 27, 'https://images.unsplash.com/photo-1526336024174-e58f5cdd8e13?w=600', 'Orijen Cat Food', 1, TRUE, NOW()),
(28, 28, 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600', 'Whiskas Tuna Pouch', 1, TRUE, NOW()),
(29, 29, 'https://images.unsplash.com/photo-1574158622682-e40e69881006?w=600', 'Whiskas Chicken Gravy', 1, TRUE, NOW()),
(30, 30, 'https://images.unsplash.com/photo-1535930891776-0c2dfb7fda1a?w=600', 'Royal Canin Intense Beauty', 1, TRUE, NOW()),
(31, 31, 'https://images.unsplash.com/photo-1563460716037-460b3dd145dc?w=600', 'Catit Pea Husk Litter', 1, TRUE, NOW()),
(32, 32, 'https://images.unsplash.com/photo-1563460716037-460b3dd145dc?w=600', 'Catit Bentonite Lavender', 1, TRUE, NOW()),
(33, 33, 'https://images.unsplash.com/photo-1526336024174-e58f5cdd8e13?w=600', 'Catit Play Circuit', 1, TRUE, NOW()),
(34, 34, 'https://images.unsplash.com/photo-1526336024174-e58f5cdd8e13?w=600', 'Catit Flower Fountain', 1, TRUE, NOW()),
(35, 35, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=600', 'Kong Cat Wobbler', 1, TRUE, NOW()),
(36, 36, 'https://images.unsplash.com/photo-1520315342629-6ea920342047?w=600', 'Aqua Vit Fish Tonic', 1, TRUE, NOW()),
(37, 37, 'https://images.unsplash.com/photo-1520315342629-6ea920342047?w=600', 'Drools Fish Pellets', 1, TRUE, NOW()),
(38, 38, 'https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=600', 'Bird Calcium Block', 1, TRUE, NOW()),
(39, 39, 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?w=600', 'Royal Canin Golden Retriever', 1, TRUE, NOW()),
(40, 40, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?w=600', 'Royal Canin German Shepherd', 1, TRUE, NOW()),
(41, 41, 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600', 'Royal Canin Persian Cat', 1, TRUE, NOW()),
(42, 42, 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=600', 'Farmina Pumpkin Lamb', 1, TRUE, NOW()),
(43, 43, 'https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=600', 'Himalaya Fresh Coat Spray', 1, TRUE, NOW()),
(44, 44, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=600', 'Kong Wild Knots Bear', 1, TRUE, NOW()),
(45, 45, 'https://images.unsplash.com/photo-1526336024174-e58f5cdd8e13?w=600', 'Catit Catnip Mist', 1, TRUE, NOW()),
(46, 46, 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=600', 'Sheba Premium Tuna & Salmon', 1, TRUE, NOW()),
(47, 47, 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600', 'Sheba Melty Cat Treats', 1, TRUE, NOW());

-- 10. Inventory Records (Normal, Low-Stock, and Out-of-Stock)
INSERT IGNORE INTO inventory (id, product_id, variant_id, current_stock, available_stock, reserved_stock, min_stock_threshold, max_stock_threshold, damaged_stock, updated_at) VALUES
(1, 1, NULL, 23, 23, 0, 5, 100, 0, NOW()),
(2, 2, NULL, 26, 26, 0, 5, 100, 0, NOW()),
(3, 3, NULL, 2, 2, 0, 5, 100, 0, NOW()),
(4, 4, NULL, 32, 32, 0, 5, 100, 0, NOW()),
(5, 5, NULL, 35, 35, 0, 5, 100, 0, NOW()),
(6, 6, NULL, 38, 38, 0, 5, 100, 0, NOW()),
(7, 7, NULL, 41, 41, 0, 5, 100, 0, NOW()),
(8, 8, NULL, 44, 44, 0, 5, 100, 0, NOW()),
(9, 9, NULL, 47, 47, 0, 5, 100, 0, NOW()),
(10, 10, NULL, 0, 0, 0, 5, 100, 0, NOW()),
(11, 11, NULL, 53, 53, 0, 5, 100, 0, NOW()),
(12, 12, NULL, 2, 2, 0, 5, 100, 0, NOW()),
(13, 13, NULL, 24, 24, 0, 5, 100, 0, NOW()),
(14, 14, NULL, 27, 27, 0, 5, 100, 0, NOW()),
(15, 15, NULL, 30, 30, 0, 5, 100, 0, NOW()),
(16, 16, NULL, 33, 33, 0, 5, 100, 0, NOW()),
(17, 17, NULL, 36, 36, 0, 5, 100, 0, NOW()),
(18, 18, NULL, 39, 39, 0, 5, 100, 0, NOW()),
(19, 19, NULL, 42, 42, 0, 5, 100, 0, NOW()),
(20, 20, NULL, 4, 4, 0, 5, 100, 0, NOW()),
(21, 21, NULL, 48, 48, 0, 5, 100, 0, NOW()),
(22, 22, NULL, 51, 51, 0, 5, 100, 0, NOW()),
(23, 23, NULL, 54, 54, 0, 5, 100, 0, NOW()),
(24, 24, NULL, 22, 22, 0, 5, 100, 0, NOW()),
(25, 25, NULL, 25, 25, 0, 5, 100, 0, NOW()),
(26, 26, NULL, 28, 28, 0, 5, 100, 0, NOW()),
(27, 27, NULL, 31, 31, 0, 5, 100, 0, NOW()),
(28, 28, NULL, 34, 34, 0, 5, 100, 0, NOW()),
(29, 29, NULL, 0, 0, 0, 5, 100, 0, NOW()),
(30, 30, NULL, 40, 40, 0, 5, 100, 0, NOW()),
(31, 31, NULL, 43, 43, 0, 5, 100, 0, NOW()),
(32, 32, NULL, 46, 46, 0, 5, 100, 0, NOW()),
(33, 33, NULL, 49, 49, 0, 5, 100, 0, NOW()),
(34, 34, NULL, 52, 52, 0, 5, 100, 0, NOW()),
(35, 35, NULL, 20, 20, 0, 5, 100, 0, NOW()),
(36, 36, NULL, 23, 23, 0, 5, 100, 0, NOW()),
(37, 37, NULL, 3, 3, 0, 5, 100, 0, NOW()),
(38, 38, NULL, 29, 29, 0, 5, 100, 0, NOW()),
(39, 39, NULL, 32, 32, 0, 5, 100, 0, NOW()),
(40, 40, NULL, 35, 35, 0, 5, 100, 0, NOW()),
(41, 41, NULL, 38, 38, 0, 5, 100, 0, NOW()),
(42, 42, NULL, 41, 41, 0, 5, 100, 0, NOW()),
(43, 43, NULL, 44, 44, 0, 5, 100, 0, NOW()),
(44, 44, NULL, 47, 47, 0, 5, 100, 0, NOW()),
(45, 45, NULL, 50, 50, 0, 5, 100, 0, NOW()),
(46, 46, NULL, 35, 35, 0, 5, 100, 0, NOW()),
(47, 47, NULL, 40, 40, 0, 5, 100, 0, NOW());

-- 11. Initial Inventory Stock Transactions
INSERT IGNORE INTO inventory_transactions (id, product_id, variant_id, transaction_type, quantity, reference_type, reference_id, notes, created_at) VALUES
(1, 1, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(2, 2, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(3, 3, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(4, 4, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(5, 5, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(6, 6, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(7, 7, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(8, 8, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(9, 9, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(10, 10, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(11, 11, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(12, 12, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(13, 13, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(14, 14, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(15, 15, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(16, 16, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(17, 17, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(18, 18, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(19, 19, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(20, 20, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(21, 21, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(22, 22, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(23, 23, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(24, 24, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(25, 25, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(26, 26, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(27, 27, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(28, 28, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(29, 29, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(30, 30, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(31, 31, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(32, 32, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(33, 33, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(34, 34, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(35, 35, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(36, 36, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(37, 37, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(38, 38, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(39, 39, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(40, 40, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(41, 41, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(42, 42, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(43, 43, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(44, 44, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW()),
(45, 45, NULL, 'RESTOCK', 50, 'INITIAL_SEED', 'PO-SEED-2026', 'Opening commercial stock from master distributor', NOW());

-- 12. Product Return Policies
INSERT IGNORE INTO product_return_policies (id, product_id, category_id, return_allowed, return_window_days, original_packaging_required, accessories_required, unused_condition_required, opened_packaging_accepted, refund_allowed, physical_return_required, inspection_required, custom_conditions, created_at, updated_at) VALUES
(1, 1, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(2, 2, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(3, 3, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(4, 4, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(5, 5, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(6, 6, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(7, 7, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(8, 8, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(9, 9, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(10, 10, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(11, 11, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(12, 12, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(13, 13, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(14, 14, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(15, 15, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(16, 16, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(17, 17, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(18, 18, NULL, TRUE, 7, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(19, 19, NULL, TRUE, 7, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(20, 20, NULL, TRUE, 7, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(21, 21, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(22, 22, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(23, 23, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(24, 24, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(25, 25, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(26, 26, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(27, 27, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(28, 28, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(29, 29, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(30, 30, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(31, 31, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(32, 32, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(33, 33, NULL, TRUE, 7, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(34, 34, NULL, TRUE, 7, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(35, 35, NULL, TRUE, 7, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(36, 36, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(37, 37, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(38, 38, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(39, 39, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(40, 40, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(41, 41, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(42, 42, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(43, 43, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(44, 44, NULL, TRUE, 7, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(45, 45, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Unused with tags attached', NOW(), NOW()),
(46, 46, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Per owner policy: wet cat food pouches non-returnable', NOW(), NOW()),
(47, 47, NULL, FALSE, 0, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, TRUE, 'Per owner policy: wet cat treats non-returnable', NOW(), NOW());

-- 13. Discount Coupons
INSERT IGNORE INTO coupons (id, code, description, discount_type, discount_value, min_order_value, max_discount_amount, usage_limit, usage_count, per_customer_usage_limit, start_date, end_date, is_active, created_at) VALUES
(1, 'WELCOME50', 'Flat Rs. 50 off on your first order over Rs. 500', 'FLAT', 50.0, 500.0, 50.0, 1000, 24, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', TRUE, NOW()),
(2, 'KURAVANKONAM100', 'Rs. 100 off on local orders above Rs. 1000 in Kuravankonam & Kowdiar', 'FLAT', 100.0, 1000.0, 100.0, 500, 12, 2, '2026-01-01 00:00:00', '2027-12-31 23:59:59', TRUE, NOW()),
(3, 'PETPARENT10', '10% discount up to Rs. 250 on premium food & treats', 'PERCENTAGE', 10.0, 750.0, 250.0, 500, 31, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', TRUE, NOW());

-- 14. Demo Orders (Spanning Complete Order Lifecycle)
INSERT IGNORE INTO orders (id, order_number, customer_id, order_status, total_amount, discount_amount, delivery_charge, tax_amount, final_payable_amount, payment_status, payment_method, delivery_address_json, shop_distance_km, is_within_radius, delivery_slot, cancellation_reason, delivery_otp_hash, is_otp_verified, created_at, updated_at) VALUES
(1, 'ORD-DEMO-1001', 1, 'DELIVERED', 3034.0, 50.0, 0.0, 273.06, 2984.0, 'PAID', 'ONLINE_RAZORPAY', '{"name":"Anand Nair","phone":"9847012345","address":"Villa 12, Kowdiar Gardens, Kowdiar","city":"Thiruvananthapuram","pincode":"695003"}', 1.42, TRUE, 'Morning Slot (09:00 AM - 01:00 PM)', NULL, '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', TRUE, '2026-11-01 10:00:00', NOW()),
(2, 'ORD-DEMO-1002', 2, 'OUT_FOR_DELIVERY', 1881.0, 0.0, 0.0, 169.29, 1881.0, 'PAID', 'ONLINE_RAZORPAY', '{"name":"Priya Kurup","phone":"9847123456","address":"Flat 4B, Heera Heights, Pattom","city":"Thiruvananthapuram","pincode":"695004"}', 2.15, TRUE, 'Afternoon Slot (01:00 PM - 05:00 PM)', NULL, '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', FALSE, '2026-11-02 10:00:00', NOW()),
(3, 'ORD-DEMO-1003', 3, 'PROCESSING', 1323.0, 0.0, 0.0, 119.07, 1323.0, 'PAID', 'ONLINE_RAZORPAY', '{"name":"Arun Menoki","phone":"9847234567","address":"TC 14/890, Kuravankonam","city":"Thiruvananthapuram","pincode":"695003"}', 0.35, TRUE, 'Evening Slot (05:00 PM - 08:30 PM)', NULL, NULL, FALSE, '2026-11-03 10:00:00', NOW()),
(4, 'ORD-DEMO-1004', 4, 'CONFIRMED', 815.0, 0.0, 50.0, 68.85, 865.0, 'PAID', 'ONLINE_RAZORPAY', '{"name":"Deepa Pillai","phone":"9847345678","address":"House 45, Sasthamangalam","city":"Thiruvananthapuram","pincode":"695010"}', 2.85, TRUE, 'Morning Slot (09:00 AM - 01:00 PM)', NULL, NULL, FALSE, '2026-11-04 10:00:00', NOW()),
(5, 'ORD-DEMO-1005', 5, 'PENDING_PAYMENT', 1755.0, 0.0, 0.0, 157.95, 1755.0, 'PENDING', 'ONLINE_RAZORPAY', '{"name":"Suresh Kumar","phone":"9847456789","address":"A-102, SFS Cyber Palms, Kazhakkoottam","city":"Thiruvananthapuram","pincode":"695581"}', 8.45, TRUE, 'Morning Slot (09:00 AM - 01:00 PM)', NULL, NULL, FALSE, '2026-11-05 10:00:00', NOW()),
(6, 'ORD-DEMO-1006', 6, 'CANCELLED', 1665.0, 0.0, 0.0, 149.85, 1665.0, 'REFUNDED', 'ONLINE_RAZORPAY', '{"name":"Reshma Varma","phone":"9847567890","address":"Green Valley House 7, Ambalamukku","city":"Thiruvananthapuram","pincode":"695005"}', 1.1, TRUE, 'Evening Slot (05:00 PM - 08:30 PM)', 'Customer requested cancellation before dispatch', NULL, FALSE, '2026-11-06 10:00:00', NOW()),
(7, 'ORD-DEMO-1007', 7, 'RETURN_REQUESTED', 1125.0, 0.0, 0.0, 101.25, 1125.0, 'PAID', 'ONLINE_RAZORPAY', '{"name":"Vinod George","phone":"9847678901","address":"Rose Cottage, Murinjapalam","city":"Thiruvananthapuram","pincode":"695011"}', 2.3, TRUE, 'Afternoon Slot (01:00 PM - 05:00 PM)', NULL, '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', TRUE, '2026-11-07 10:00:00', NOW()),
(8, 'ORD-DEMO-1008', 8, 'REFUNDED', 1485.0, 0.0, 0.0, 133.65, 1485.0, 'REFUNDED', 'ONLINE_RAZORPAY', '{"name":"Anjali Menon","phone":"9847789012","address":"TC 9/112, Jawahar Nagar, Vellayambalam","city":"Thiruvananthapuram","pincode":"695010"}', 1.85, TRUE, 'Morning Slot (09:00 AM - 01:00 PM)', NULL, '$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS', TRUE, '2026-11-08 10:00:00', NOW());

-- 15. Order Items with Pricing Snapshots
INSERT IGNORE INTO order_items (id, order_id, product_id, variant_id, product_name, brand_name, sku, quantity, unit_price, total_price, tax_rate, tax_amount, hsn_code, return_policy_snapshot, refund_policy_snapshot, created_at) VALUES
(1, 1, 1, NULL, 'Royal Canin Maxi Adult Dry Dog Food', 'Royal Canin', 'RC-MAXI-ADULT-4KG', 1, 2899.0, 2899.0, 9.0, 260.91, '2309', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(2, 1, 13, NULL, 'Pedigree Dentastix Daily Dental Dog Chews', 'Pedigree', 'PED-DENT-MED-7PC', 1, 198.0, 198.0, 9.0, 17.82, '2309', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(3, 2, 23, NULL, 'Whiskas Adult Ocean Fish Dry Cat Food', 'Whiskas', 'WHIS-DRY-OF-3KG', 2, 891.0, 1782.0, 9.0, 160.38, '2309', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(4, 2, 28, NULL, 'Whiskas Wet Cat Food Tuna in Jelly Pouch', 'Whiskas', 'WHIS-WET-TUNA-85G', 2, 45.0, 90.0, 9.0, 8.1, '2309', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(5, 3, 18, NULL, 'Kong Classic Natural Rubber Durable Dog Toy', 'Kong', 'KONG-CLASSIC-L', 1, 1125.0, 1125.0, 9.0, 101.25, '3924', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(6, 3, 13, NULL, 'Pedigree Dentastix Daily Dental Dog Chews', 'Pedigree', 'PED-DENT-MED-7PC', 1, 198.0, 198.0, 9.0, 17.82, '2309', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(7, 4, 3, NULL, 'Pedigree Adult Chicken & Vegetables Dry Dog Food', 'Pedigree', 'PED-ADULT-CHK-3KG', 1, 765.0, 765.0, 9.0, 68.85, '2309', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(8, 5, 2, NULL, 'Royal Canin Mini Puppy Dry Dog Food', 'Royal Canin', 'RC-MINI-PUP-2KG', 1, 1755.0, 1755.0, 9.0, 157.95, '2309', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(9, 6, 5, NULL, 'Drools Focus Adult Super Premium Dog Food', 'Drools', 'DRL-FOC-ADL-4KG', 1, 1665.0, 1665.0, 9.0, 149.85, '2309', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(10, 7, 18, NULL, 'Kong Classic Natural Rubber Durable Dog Toy', 'Kong', 'KONG-CLASSIC-L', 1, 1125.0, 1125.0, 9.0, 101.25, '3924', '7 Days Return if unused', 'Full refund upon physical inspection', NOW()),
(11, 8, 20, NULL, 'Kong Extreme Black Ultra-Tough Rubber Chew', 'Kong', 'KONG-EXTR-XL', 1, 1485.0, 1485.0, 9.0, 133.65, '3924', '7 Days Return if unused', 'Full refund upon physical inspection', NOW());

-- 16. Payment Records
INSERT IGNORE INTO payments (id, order_id, gateway_order_id, gateway_payment_id, gateway_signature, amount, currency, status, method, failure_reason, created_at, updated_at) VALUES
(1, 1, 'order_demo_1001_rzp', 'pay_demo_1001_rzp', 'sig_verified_mock_1001', 2984.0, 'INR', 'SUCCESS', 'RAZORPAY_UPI', NULL, NOW(), NOW()),
(2, 2, 'order_demo_1002_rzp', 'pay_demo_1002_rzp', 'sig_verified_mock_1002', 1881.0, 'INR', 'SUCCESS', 'RAZORPAY_CARD', NULL, NOW(), NOW()),
(3, 3, 'order_demo_1003_rzp', 'pay_demo_1003_rzp', 'sig_verified_mock_1003', 1323.0, 'INR', 'SUCCESS', 'RAZORPAY_UPI', NULL, NOW(), NOW()),
(4, 4, 'order_demo_1004_rzp', 'pay_demo_1004_rzp', 'sig_verified_mock_1004', 865.0, 'INR', 'SUCCESS', 'RAZORPAY_NETBANKING', NULL, NOW(), NOW()),
(5, 5, 'order_demo_1005_rzp', 'pay_demo_1005_rzp', 'NULL', 1755.0, 'INR', 'PENDING', 'RAZORPAY_UPI', NULL, NOW(), NOW()),
(6, 6, 'order_demo_1006_rzp', 'pay_demo_1006_rzp', 'sig_verified_mock_1006', 1665.0, 'INR', 'REFUNDED', 'RAZORPAY_CARD', NULL, NOW(), NOW()),
(7, 7, 'order_demo_1007_rzp', 'pay_demo_1007_rzp', 'sig_verified_mock_1007', 1125.0, 'INR', 'SUCCESS', 'RAZORPAY_UPI', NULL, NOW(), NOW()),
(8, 8, 'order_demo_1008_rzp', 'pay_demo_1008_rzp', 'sig_verified_mock_1008', 1485.0, 'INR', 'REFUNDED', 'RAZORPAY_UPI', NULL, NOW(), NOW());

-- 17. Returns & Refunds Management Data
INSERT IGNORE INTO return_requests (id, request_number, order_id, customer_id, status, reason, customer_comments, admin_notes, created_at, updated_at) VALUES
(1, 'RET-2026-001', 7, 7, 'PENDING_REVIEW', 'DEFECTIVE_OR_DAMAGED', 'The rubber squeaker arrived crushed inside the package', 'Awaiting customer photos for inspection', '2026-11-14 17:05:00', NOW()),
(2, 'RET-2026-002', 8, 8, 'APPROVED', 'INCORRECT_ITEM_SENT', 'Ordered XL size but packaging shows Large', 'Approved for doorstep reverse pickup and refund', '2026-11-12 13:20:00', NOW());

INSERT IGNORE INTO return_request_items (id, return_request_id, order_item_id, quantity, created_at) VALUES
(1, 1, 10, 1, '2026-11-14 17:05:00'),
(2, 2, 11, 1, '2026-11-12 13:20:00');

INSERT IGNORE INTO refunds (id, order_id, payment_id, gateway_refund_id, amount, status, reason, approved_by, initiated_at, completed_at, created_at) VALUES
(1, 8, 8, 'rfnd_demo_1008_rzp', 1485.00, 'COMPLETED', 'Returned incorrect item approved by store manager', 'test@gmail.com', '2026-11-13 15:10:00', '2026-11-13 15:10:00', NOW());

-- 18. Verified Product Customer Reviews
INSERT IGNORE INTO reviews (id, product_id, customer_id, rating, title, comment, is_verified_purchase, status, admin_reply, created_at, updated_at) VALUES
(1, 1, 1, 5, 'Best food for Golden Retrievers', 'My dog Rocky has been eating Royal Canin Maxi for 2 years. Excellent coat shine and digestion!', TRUE, 'APPROVED', 'Thank you Anand! We are delighted to keep Rocky healthy.', '2026-11-07 10:00:00', NOW()),
(2, 13, 1, 5, 'Fresh breath and clean teeth', 'Dentastix really works. He gets one every morning without fail.', TRUE, 'APPROVED', 'Glad Rocky loves his daily routine!', '2026-11-08 11:30:00', NOW()),
(3, 23, 2, 5, 'My Persian cat Bella adores this', 'Bella can be very picky, but she finished every single kibble of Whiskas Ocean Fish.', TRUE, 'APPROVED', NULL, '2026-11-09 14:15:00', NOW()),
(4, 18, 3, 5, 'Indestructible dog toy!', 'Max destroys every plush toy in 5 minutes, but Kong Classic is still solid as new.', TRUE, 'APPROVED', 'Kong is indeed the gold standard for power chewers!', '2026-11-10 16:00:00', NOW()),
(5, 31, 2, 4, 'Eco friendly and low dust', 'Catit Pea Husk litter clumps very quickly and has zero dust when scooping.', TRUE, 'APPROVED', NULL, '2026-11-11 09:45:00', NOW()),
(6, 16, 1, 5, 'Gentle on skin and ticks vanished', 'Himalaya Erina shampoo worked wonders during monsoon tick season.', TRUE, 'APPROVED', 'Herbal care works best for our humid Trivandrum weather!', '2026-11-12 13:20:00', NOW());

-- 19. Customer Support Requests
INSERT IGNORE INTO support_tickets (id, ticket_number, customer_id, order_id, subject, priority, status, assigned_to, created_at, updated_at) VALUES
(1, 'TKT-2026-001', 1, 1, 'Inquiry on repeat subscription delivery', 'LOW', 'RESOLVED', 'staff@novapetzlife.com', '2026-11-10 16:00:00', NOW()),
(2, 'TKT-2026-002', 7, 7, 'Damaged product received in order ORD-DEMO-1007', 'HIGH', 'IN_PROGRESS', 'staff@novapetzlife.com', '2026-11-14 17:05:00', NOW());

-- 20. System Audit Logs
INSERT IGNORE INTO audit_logs (id, entity_name, entity_id, action, changed_by, old_value, new_value, ip_address, created_at) VALUES
(1, 'ORDER', '1', 'DELIVERY_VERIFIED', 'system', 'OUT_FOR_DELIVERY', 'DELIVERED (OTP Verified: 482910)', '127.0.0.1', '2026-11-07 10:00:00'),
(2, 'ORDER', '6', 'ORDER_CANCELLED', 'test@gmail.com', 'CONFIRMED', 'CANCELLED', '127.0.0.1', '2026-11-11 09:45:00'),
(3, 'INVENTORY', '10', 'STOCK_ADJUSTMENT', 'staff@novapetzlife.com', 'current_stock: 5', 'current_stock: 0', '127.0.0.1', '2026-11-12 13:20:00'),
(4, 'PRODUCT', '1', 'PRICE_UPDATE', 'test@gmail.com', 'base_price: 3300.00', 'base_price: 3200.00', '127.0.0.1', '2026-11-13 15:10:00');
