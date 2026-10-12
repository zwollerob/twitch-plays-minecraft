package nl.zwollerob.twitchplays.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import nl.zwollerob.twitchplays.Secrets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The arrow-up history of the chat box: a line with a token or key in it is not kept there either. */
@Mixin(ChatComponent.class)
public class ChatHistoryMixin {
	@Inject(method = "addRecentChat", at = @At("HEAD"), cancellable = true)
	private void twitchplays$skipSecrets(String message, CallbackInfo ci) {
		if (Secrets.isSensitive(message)) {
			ci.cancel();
		}
	}
}
