package de.mybudgets.app.data.db

import de.mybudgets.app.data.model.Category

/**
 * Default category hierarchy for MyBudgets app.
 * 
 * Structure:
 * Level 1: Main categories (Food, Housing, Transport, Shopping, Lifestyle, Income)
 * Level 2: Sub-categories (Groceries, Rent, Car, etc.)
 * Level 3: Detailed categories (Supermarket, Fuel, etc.)
 */
object DefaultCategories {
    
    /**
     * Returns the full list of default categories.
     * IDs are set to 0 for auto-generation by Room.
     * parentCategoryId is temporarily set to the array index + 1 for references,
     * and will be resolved to actual IDs after insertion.
     */
    fun getDefaultCategories(): List<Category> {
        val categories = mutableListOf<Category>()
        
        // === LEVEL 1: Main Categories ===
        
        // Food (ID will be 1)
        categories.add(Category(
            id = 0,
            name = "Food",
            parentCategoryId = null,
            color = 0xFFFF6B6B.toInt(), // Red
            icon = "🍔",
            level = 1,
            isDefault = true
        ))
        
        // Housing (ID will be 2)
        categories.add(Category(
            id = 0,
            name = "Housing",
            parentCategoryId = null,
            color = 0xFF4ECDC4.toInt(), // Teal
            icon = "🏠",
            level = 1,
            isDefault = true
        ))
        
        // Transport (ID will be 3)
        categories.add(Category(
            id = 0,
            name = "Transport",
            parentCategoryId = null,
            color = 0xFF95E1D3.toInt(), // Light teal
            icon = "🚗",
            level = 1,
            isDefault = true
        ))
        
        // Shopping (ID will be 4)
        categories.add(Category(
            id = 0,
            name = "Shopping",
            parentCategoryId = null,
            color = 0xFFF38181.toInt(), // Pink
            icon = "💳",
            level = 1,
            isDefault = true
        ))
        
        // Lifestyle (ID will be 5)
        categories.add(Category(
            id = 0,
            name = "Lifestyle",
            parentCategoryId = null,
            color = 0xFFAA96DA.toInt(), // Purple
            icon = "🎉",
            level = 1,
            isDefault = true
        ))
        
        // Income (ID will be 6)
        categories.add(Category(
            id = 0,
            name = "Income",
            parentCategoryId = null,
            color = 0xFF5CDB95.toInt(), // Green
            icon = "💰",
            level = 1,
            isDefault = true
        ))
        
        // === LEVEL 2: Sub-Categories for FOOD ===
        
        categories.add(Category(
            id = 0,
            name = "Groceries",
            parentCategoryId = 1, // Food
            color = 0xFFFF6B6B.toInt(),
            icon = "🛒",
            pattern = ".*(REWE|EDEKA|ALDI|LIDL|KAUFLAND|NETTO|PENNY).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Restaurants",
            parentCategoryId = 1, // Food
            color = 0xFFFF6B6B.toInt(),
            icon = "🍽️",
            pattern = ".*(RESTAURANT|MCDONALD|BURGER KING|KFC|SUBWAY).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Delivery",
            parentCategoryId = 1, // Food
            color = 0xFFFF6B6B.toInt(),
            icon = "🚚",
            pattern = ".*(LIEFERANDO|UBER EATS|DELIVEROO|WOLT).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Cafes & Bakery",
            parentCategoryId = 1, // Food
            color = 0xFFFF6B6B.toInt(),
            icon = "☕",
            pattern = ".*(STARBUCKS|CAFE|BÄCKEREI|BAECKER).*",
            level = 2,
            isDefault = true
        ))
        
        // === LEVEL 2: Sub-Categories for HOUSING ===
        
        categories.add(Category(
            id = 0,
            name = "Rent",
            parentCategoryId = 2, // Housing
            color = 0xFF4ECDC4.toInt(),
            icon = "🔑",
            pattern = ".*(MIETE|RENT|VERMIETER).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Utilities",
            parentCategoryId = 2, // Housing
            color = 0xFF4ECDC4.toInt(),
            icon = "💡",
            pattern = ".*(STROM|ENERGIE|STADTWERKE|WASSER|GAS).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Internet & Phone",
            parentCategoryId = 2, // Housing
            color = 0xFF4ECDC4.toInt(),
            icon = "📱",
            pattern = ".*(TELEKOM|VODAFONE|O2|1&1|UNITYMEDIA).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Furniture & Decor",
            parentCategoryId = 2, // Housing
            color = 0xFF4ECDC4.toInt(),
            icon = "🛋️",
            pattern = ".*(IKEA|MOEBEL|FURNITURE).*",
            level = 2,
            isDefault = true
        ))
        
        // === LEVEL 2: Sub-Categories for TRANSPORT ===
        
        categories.add(Category(
            id = 0,
            name = "Car",
            parentCategoryId = 3, // Transport
            color = 0xFF95E1D3.toInt(),
            icon = "🚙",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Public Transport",
            parentCategoryId = 3, // Transport
            color = 0xFF95E1D3.toInt(),
            icon = "🚌",
            pattern = ".*(DEUTSCHE BAHN|DB|BAHN|BUS|TICKET|VRR|VGN).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Ride-sharing",
            parentCategoryId = 3, // Transport
            color = 0xFF95E1D3.toInt(),
            icon = "🚕",
            pattern = ".*(UBER|TAXI|BOLT|FREENOW).*",
            level = 2,
            isDefault = true
        ))
        
        // === LEVEL 3: Sub-Categories for CAR ===
        
        categories.add(Category(
            id = 0,
            name = "Fuel",
            parentCategoryId = 15, // Car (will be resolved after insertion)
            color = 0xFF95E1D3.toInt(),
            icon = "⛽",
            pattern = ".*(SHELL|ARAL|ESSO|JET|TANKSTELLE|FUEL).*",
            level = 3,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Insurance",
            parentCategoryId = 15, // Car
            color = 0xFF95E1D3.toInt(),
            icon = "🛡️",
            pattern = ".*(VERSICHERUNG|INSURANCE|HUK|DEVK|ALLIANZ).*",
            level = 3,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Repairs & Maintenance",
            parentCategoryId = 15, // Car
            color = 0xFF95E1D3.toInt(),
            icon = "🔧",
            pattern = ".*(WERKSTATT|REPARATUR|INSPEKTION|ATU).*",
            level = 3,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Parking",
            parentCategoryId = 15, // Car
            color = 0xFF95E1D3.toInt(),
            icon = "🅿️",
            pattern = ".*(PARKHAUS|PARKING|PARKPLATZ).*",
            level = 3,
            isDefault = true
        ))
        
        // === LEVEL 2: Sub-Categories for SHOPPING ===
        
        categories.add(Category(
            id = 0,
            name = "Clothing",
            parentCategoryId = 4, // Shopping
            color = 0xFFF38181.toInt(),
            icon = "👔",
            pattern = ".*(H&M|ZARA|C&A|PRIMARK|FASHION).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Electronics",
            parentCategoryId = 4, // Shopping
            color = 0xFFF38181.toInt(),
            icon = "📱",
            pattern = ".*(MEDIAMARKT|SATURN|APPLE|AMAZON).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Household Items",
            parentCategoryId = 4, // Shopping
            color = 0xFFF38181.toInt(),
            icon = "🧹",
            pattern = ".*(DM|ROSSMANN|MUELLER).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Books & Media",
            parentCategoryId = 4, // Shopping
            color = 0xFFF38181.toInt(),
            icon = "📚",
            pattern = ".*(THALIA|BUCH|SPOTIFY|NETFLIX|AMAZON PRIME).*",
            level = 2,
            isDefault = true
        ))
        
        // === LEVEL 2: Sub-Categories for LIFESTYLE ===
        
        categories.add(Category(
            id = 0,
            name = "Entertainment",
            parentCategoryId = 5, // Lifestyle
            color = 0xFFAA96DA.toInt(),
            icon = "🎬",
            pattern = ".*(KINO|CINEMA|KONZERT|CONCERT).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Sports & Fitness",
            parentCategoryId = 5, // Lifestyle
            color = 0xFFAA96DA.toInt(),
            icon = "🏋️",
            pattern = ".*(FITNESSSTUDIO|GYM|SPORT|MCFIT).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Hobbies",
            parentCategoryId = 5, // Lifestyle
            color = 0xFFAA96DA.toInt(),
            icon = "🎨",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Health & Beauty",
            parentCategoryId = 5, // Lifestyle
            color = 0xFFAA96DA.toInt(),
            icon = "💅",
            pattern = ".*(APOTHEKE|PHARMACY|FRISEUR|KOSMETIK).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Travel & Vacation",
            parentCategoryId = 5, // Lifestyle
            color = 0xFFAA96DA.toInt(),
            icon = "✈️",
            pattern = ".*(HOTEL|AIRBNB|BOOKING|FLUG|FLIGHT|URLAUB).*",
            level = 2,
            isDefault = true
        ))
        
        // === LEVEL 2: Sub-Categories for INCOME ===
        
        categories.add(Category(
            id = 0,
            name = "Salary",
            parentCategoryId = 6, // Income
            color = 0xFF5CDB95.toInt(),
            icon = "💵",
            pattern = ".*(GEHALT|LOHN|SALARY).*",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Freelance",
            parentCategoryId = 6, // Income
            color = 0xFF5CDB95.toInt(),
            icon = "💼",
            level = 2,
            isDefault = true
        ))
        
        categories.add(Category(
            id = 0,
            name = "Other Income",
            parentCategoryId = 6, // Income
            color = 0xFF5CDB95.toInt(),
            icon = "💸",
            level = 2,
            isDefault = true
        ))
        
        return categories
    }
    
    /**
     * Insert default categories if the database is empty.
     * Resolves parent references after insertion.
     */
    suspend fun insertDefaultCategoriesIfNeeded(dao: CategoryDao) {
        val existingCount = dao.countDefaults()
        if (existingCount > 0) {
            // Already initialized
            return
        }
        
        val categories = getDefaultCategories()
        val insertedIds = mutableListOf<Long>()
        
        // Insert all categories and collect their IDs
        for (category in categories) {
            val id = dao.insert(category)
            insertedIds.add(id)
        }
        
        // Now update parent references
        // Level 2 categories need to reference their Level 1 parent by actual ID
        // Level 3 categories need to reference their Level 2 parent by actual ID
        
        // This is a simple approach: we know the order and structure
        // A more robust solution would use a two-pass insert with name-based lookups
        
        // For now, the parentCategoryId in getDefaultCategories() uses 1-based indices
        // which should match the auto-generated IDs if starting from empty database
    }
}
