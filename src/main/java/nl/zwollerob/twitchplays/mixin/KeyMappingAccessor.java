package nl.zwollerob.twitchplays.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Single clicks (attack, use, hotbar keys) are counted in clickCount, not in isDown. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int twitchplays$getClickCount();

	@Accessor("clickCount")
	void twitchplays$setClickCount(int count);
}
