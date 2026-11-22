export interface ProductVariant {
  id: number;
  name: string;
  price: number;
  discountedPrice?: number;
  stock: number;
}

export interface ProductCardData {
  id: number;
  name: string;
  brand: string;
  category: string;
  subcategory?: string;
  petType: 'Cat' | 'Dog' | 'Bird' | 'Fish' | 'All Pets';
  price: number;
  discountedPrice?: number;
  imageUrl: string;
  backImageUrl?: string;
  inStock: boolean;
  stockQuantity: number;
  rating: number;
  reviewCount: number;
  isFeatured?: boolean;
  returnAllowed: boolean;
  variants?: ProductVariant[];
}

export interface CategoryItem {
  id: number;
  name: string;
  slug: string;
  icon: string;
  description: string;
  itemCount: number;
  popularBrands: string[];
}
