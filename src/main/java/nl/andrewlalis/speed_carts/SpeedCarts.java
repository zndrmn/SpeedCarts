package nl.andrewlalis.speed_carts;

import net.fabricmc.api.ModInitializer;

public class SpeedCarts implements ModInitializer {
	public static Config config;

	@Override
	public void onInitialize() {
		config = new Config();
		System.out.println("Speed Carts initialized.");
	}
}
