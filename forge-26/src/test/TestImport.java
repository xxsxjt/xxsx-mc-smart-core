package test;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.common.Mod;
@Mod("test")
public class TestImport {
    public TestImport() {
        ResourceLocation rl = ResourceLocation.withDefaultNamespace("test");
        System.out.println(rl);
    }
}
