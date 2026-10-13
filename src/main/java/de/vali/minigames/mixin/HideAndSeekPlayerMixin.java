package de.vali.minigames.mixin;

import de.vali.minigames.hideandseek.HideAndSeekVisibility;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class HideAndSeekPlayerMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void serverMinigames$hideDuringCountdown(ServerPlayer viewer,
            CallbackInfoReturnable<Boolean> callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (HideAndSeekVisibility.isHiddenFrom(player.getUUID(), viewer.getUUID())) {
            callback.setReturnValue(false);
        }
    }
}
