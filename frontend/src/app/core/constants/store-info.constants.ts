import { environment } from '../../../environments/environment';

/**
 * Single source of truth for the physical store address across NOVA PETZLIFE.
 * Update environment.shopAddress in environment.ts / environment.prod.ts to reflect changes everywhere.
 */
export const SHOP_ADDRESS = environment.shopAddress;
export const SHOP_NAME = environment.shopName;
