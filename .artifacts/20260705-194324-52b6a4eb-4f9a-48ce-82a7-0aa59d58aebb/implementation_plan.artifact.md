# Priority 4 Optimization Plan: Search & UI Polish

This plan outlines the steps to optimize the search functionality and polish the UI/UX for a production-ready feel.

## User Review Required

- **Search UX**: The current search requires clicking a "Cari" button. I propose adding a debounce (e.g., 500ms) to trigger search automatically as the user types.
- **Navigation Icons**: The bottom bar currently uses letters as icons. I will add proper Vector Asset icons.
- **Empty States**: Improving the visual feedback when search returns no results.

## Proposed Changes

### UI Components Polish

#### [ProductCard.kt](file:///home/alvano/Project/Mobile/UMKMShop/app/src/main/java/com/application/umkmshop/ui/components/ProductCard.kt)
- Standardize the grid product card used in the catalog.
- Add elevation and subtle shadow for better depth.

#### [NEW] [SearchDebounce.kt](file:///home/alvano/Project/Mobile/UMKMShop/app/src/main/java/com/application/umkmshop/ui/product/SearchDebounce.kt)
- Helper to handle real-time search filtering with debounce to avoid excessive API calls.

---

### Buyer Experience Optimization

#### [BuyerCatalogViewModel.kt](file:///home/alvano/Project/Mobile/UMKMShop/app/src/main/java/com/application/umkmshop/ui/product/BuyerCatalogViewModel.kt)
- Integrate debounced search.
- Optimize state management for faster filter updates.

#### [BuyerProductScreens.kt](file:///home/alvano/Project/Mobile/UMKMShop/app/src/main/java/com/application/umkmshop/ui/product/BuyerProductScreens.kt)
- Refactor `CatalogFilters` to be more compact (expandable/collapsible).
- Add "Empty Result" illustration/friendly text for search.
- Improve `BuyerGridProductCard` layout.

---

### Navigation & Global UI

#### [UMKMShopApp.kt](file:///home/alvano/Project/Mobile/UMKMShop/app/src/main/java/com/application/umkmshop/ui/UMKMShopApp.kt)
- Replace placeholder icons in `UMKMBottomBar` with Material Design icons.
- Fix spacing in the bottom navigation.

## Verification Plan

### Automated Tests
- `BuyerCatalogViewModelTest`: Test that search triggers correctly after debounce.
- `ProductRepositoryTest`: Ensure filtering logic handles edge cases (SQL injection safety, empty patterns).

### Manual Verification
- **Search Latency**: Verify that typing "Kripik" in the search box doesn't lag the UI.
- **Visual Audit**: Walk through all screens on the Android Device to ensure consistent margins, font sizes, and colors.
- **Filter Persistence**: Ensure filters are kept when navigating back from details.
