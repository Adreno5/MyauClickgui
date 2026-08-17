package adreno.myauclickgui.mixin;
import adreno.myauclickgui.feature.utils.ChatUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = EntityPlayerSP.class, remap = false)
public class EntityPlayerSPMixin {
    @Inject(method = "func_145747_a", at = @At("HEAD"), cancellable = true)
    private void myauclickgui$onAddChatMessage(IChatComponent component, CallbackInfo ci) {
        if (ChatUtil.onChatMessage(component, 0)) {
            ci.cancel();
        }
    }
}
