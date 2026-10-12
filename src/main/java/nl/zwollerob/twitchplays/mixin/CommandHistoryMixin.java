package nl.zwollerob.twitchplays.mixin;

import net.minecraft.client.CommandHistory;
import nl.zwollerob.twitchplays.Secrets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft saves typed commands in command_history.txt; a command with a token or key in it is never saved. */
@Mixin(CommandHistory.class)
public class CommandHistoryMixin {
	@Inject(method = "addCommand", at = @At("HEAD"), cancellable = true)
	private void twitchplays$skipSecrets(String command, CallbackInfo ci) {
		if (Secrets.isSensitive(command)) {
			ci.cancel();
		}
	}
}
