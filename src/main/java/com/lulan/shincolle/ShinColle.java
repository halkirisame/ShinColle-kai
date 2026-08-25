package com.lulan.shincolle;

import com.lulan.shincolle.capability.CapabilityHandler;
import com.lulan.shincolle.command.CommandHandler;
import com.lulan.shincolle.config.ConfigMining;
import com.lulan.shincolle.api.equipment.ShipEquipmentProviders;
import com.lulan.shincolle.equip.ShipEquipmentOptionalIntegrations;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.equip.curios.ShipCuriosRecalcHandler;
import com.lulan.shincolle.equip.curios.ShipEquipCurioCapabilityHandler;
import com.lulan.shincolle.equip.tinkers.ShipTinkersIntegration;
import com.lulan.shincolle.equipdata.EquipDataLoader;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.*;
import com.lulan.shincolle.loot.ShinColleLootModifiers;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.worldgen.ModWorldGen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Reference.MOD_ID)
public class ShinColle {

    public static final Logger LOGGER = LoggerFactory.getLogger(Reference.MOD_ID);

    public ShinColle() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register DeferredRegisters to the mod event bus
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        ModShipAttributes.register(modEventBus);
        ShinColleLootModifiers.register(modEventBus);

        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigHandler.COMMON_SPEC,
                Reference.MOD_ID + "-common.toml");

        // Register lifecycle event listeners
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::loadComplete);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
        modEventBus.addListener(CapabilityHandler::onRegisterCapabilities);

        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        // Register command handler for Brigadier commands
        MinecraftForge.EVENT_BUS.register(new CommandHandler());

        // Third-party equipment slots (see com.lulan.shincolle.equip) are
        // backed by Curios, which is optional - only register the recalc
        // handler, and thus only touch Curios' event classes, when it's
        // actually loaded.
        if (ModList.get().isLoaded("curios")) {
            MinecraftForge.EVENT_BUS.register(new ShipCuriosRecalcHandler());
            MinecraftForge.EVENT_BUS.register(new ShipEquipCurioCapabilityHandler());
            ShipEquipmentOptionalIntegrations.registerStackSource(
                    ShipCuriosIntegration.STACK_SOURCE_ID, ShipCuriosIntegration::getEquippedStacks);
        }

        // Lets a ship-equip Curios slot read a plain Tinkers' Construct tool's
        // own material/modifier stats, same optional-dependency pattern as Curios.
        if (ModList.get().isLoaded("tconstruct")) {
            ShipEquipmentProviders.register(ShipTinkersIntegration.PROVIDER_ID, 0,
                    ShipTinkersIntegration.INSTANCE);
        }

        LOGGER.info("ShinColle: Mod loading initialized.");
    }

    /**
     * Common setup phase - register networking and other cross-side systems.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        ModNetworking.register();
        ModWorldGen.init();
        event.enqueueWork(() -> {
            ModShipAttributes.initializeLayout();
            ConfigHandler.onShipAttributeLayoutReady();
        });

        // Load mining loot table config (CSV file)
        ConfigMining.load(FMLPaths.CONFIGDIR.get()
                .resolve(Reference.MOD_ID + "-mining.cfg").toFile());
    }

    /** Freezes addon equipment-provider registration after every mod has constructed. */
    private void loadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(ShipEquipmentProviders::freeze);
    }

    /**
     * Called when config is first loaded. Syncs cached static fields.
     */
    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ConfigHandler.COMMON_SPEC) {
            ConfigHandler.syncConfig();
            LOGGER.info("ShinColle: Config loaded.");
        }
    }

    /**
     * Called when config is reloaded (e.g. via in-game config editor).
     * Re-syncs cached static fields.
     */
    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ConfigHandler.COMMON_SPEC) {
            ConfigHandler.syncConfig();
            LOGGER.info("ShinColle: Config reloaded.");
        }
    }

    /**
     * Ship equipment stats/metadata are datapack-driven (see
     * com.lulan.shincolle.equipdata) - this is how the loader gets wired into
     * the normal datapack reload cycle, same as recipes or loot tables.
     */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EquipDataLoader());
    }
}
