package com.minelittlepony.unicopia.item;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

public class JarInsertRecipe extends ItemCombinationRecipe {

    public JarInsertRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public final ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
        Pair<ItemStack, ItemStack> pair = runMatch(inventory);

        return UItems.FILLED_JAR.setAppearance(UItems.FILLED_JAR.getDefaultStack(), pair.getRight());
    }

    @Override
    protected boolean isContainerItem(ItemStack stack) {
        return stack.getItem() == UItems.EMPTY_JAR;
    }

    @Override
    protected boolean isInsertItem(ItemStack stack) {
        return !(stack.getItem() instanceof JarItem);
    }

    @Override
    protected boolean isCombinationInvalid(ItemStack bangle, ItemStack dust) {
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return URecipes.JAR_INSERT_SERIALIZER;
    }
}
