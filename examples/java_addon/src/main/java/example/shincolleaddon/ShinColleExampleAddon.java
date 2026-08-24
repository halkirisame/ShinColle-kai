package example.shincolleaddon;

import com.lulan.shincolle.api.attribute.ShipAttributeRegistries;
import com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.equipment.IShipEquipment;
import com.lulan.shincolle.api.equipment.ResolvedShipEquipment;
import com.lulan.shincolle.api.equipment.ShipEquipmentContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Compile-checked minimal Java addon using only ShinColle-kai's Public API. */
@Mod(ShinColleExampleAddon.MOD_ID)
public final class ShinColleExampleAddon {

    public static final String MOD_ID = "shincolle_example";
    public static final ResourceLocation SONAR_PRECISION_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "sonar_precision");

    private static final DeferredRegister<ShipAttributeType> ATTRIBUTES =
            DeferredRegister.create(ShipAttributeRegistries.REGISTRY_KEY, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<ShipAttributeType> SONAR_PRECISION = ATTRIBUTES.register(
            "sonar_precision", () -> ShipAttributeType.builder()
                    .minimum(0F)
                    .maximum(1F)
                    .displayFormat(ShipAttributeDisplayFormat.PERCENT)
                    .translationKey("ship_attribute.shincolle_example.sonar_precision")
                    .build());
    public static final RegistryObject<Item> SONAR_MODULE = ITEMS.register(
            "sonar_module", () -> new SonarModuleItem(new Item.Properties().stacksTo(1)));

    public ShinColleExampleAddon() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ATTRIBUTES.register(modBus);
        ITEMS.register(modBus);
    }

    private static final class SonarModuleItem extends Item implements IShipEquipment {

        private SonarModuleItem(Properties properties) {
            super(properties);
        }

        @Override
        public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
            ItemStack stack = context.stack();
            CompoundTag tag = stack.getTag();
            float calibration = tag == null ? 0F : Math.max(0F, Math.min(1F,
                    tag.getFloat("SonarCalibration")));
            return new ResolvedShipEquipment(
                    com.lulan.shincolle.api.attribute.ShipAttributeValues.builder(context.layout())
                            .set(SONAR_PRECISION_ID, calibration)
                            .build(),
                    ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
        }
    }
}
