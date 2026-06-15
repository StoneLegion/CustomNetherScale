package ru.windcorp.piwcs.cns;

import cpw.mods.fml.common.Mod;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldServer;

public class CNSHooks {
	
	@Mod.Instance("custom_nether_scale")
	private static CNSMod modInstance;
	
	public static double getScale(WorldProvider provider) {
		if (provider instanceof WorldProviderHell) {
			return modInstance.getNetherScale(); 
		}
		
		return 1.0;
	}

	public static double getTransferRatio(WorldServer source, WorldServer target) {
		return getScale(source.provider) / getScale(target.provider);
	}

}
