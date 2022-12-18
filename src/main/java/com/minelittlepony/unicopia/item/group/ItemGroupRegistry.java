package com.minelittlepony.unicopia.item.group;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.cache.*;
import com.minelittlepony.unicopia.UTags;
import com.minelittlepony.unicopia.Unicopia;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;

public interface ItemGroupRegistry {
    LoadingCache<Item, List<ItemStack>> STACK_VARIANCES_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.SECONDS)
            .build(CacheLoader.from(i -> {
                if (i instanceof MultiItem m) {
                    return m.getDefaultStacks();
                }
                return ItemGroups.SEARCH.getSearchTabStacks().stream().filter(s -> s.getItem() == i).toList();
            }));

    static List<ItemStack> getVariations(Item item) {
        return STACK_VARIANCES_CACHE.getUnchecked(item);
    }

    Map<ItemGroup, Set<Item>> REGISTRY = new HashMap<>();

    static <T extends Item> T register(T item, ItemGroup group) {
        REGISTRY.computeIfAbsent(group, g -> new HashSet<>()).add(item);
        return item;
    }

    static ItemGroup createDynamic(String name, Supplier<ItemStack> icon, Supplier<Stream<Item>> items) {
        boolean[] reloading = new boolean[1];
        return FabricItemGroup.builder(Unicopia.id(name)).entries((features, list, k) -> {
            if (reloading[0]) {
                return;
            }
            reloading[0] = true;
            items.get().forEach(item -> {
                list.addAll(ItemGroupRegistry.getVariations(item));
            });
            reloading[0] = false;
        }).icon(icon).build();
    }

    static ItemGroup createGroupFromTag(String name, Supplier<ItemStack> icon) {
        TagKey<Item> key = UTags.item("groups/" + name);
        return createDynamic(name, icon, () -> {
            return Registries.ITEM.getEntryList(key)
                    .stream()
                    .flatMap(named -> named.stream())
                    .map(entry -> entry.value());
        });
    }

    static void bootstrap() {
        REGISTRY.forEach((group, items) -> {
            ItemGroupEvents.modifyEntriesEvent(group).register(event -> {
                event.addAll(items.stream().map(Item::getDefaultStack).toList());
            });
        });
    }
}
