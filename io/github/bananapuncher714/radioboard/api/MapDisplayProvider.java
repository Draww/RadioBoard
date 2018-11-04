package io.github.bananapuncher714.radioboard.api;

import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

public interface MapDisplayProvider {
	Frame getSource();
	void interactAt( Player player, DisplayInteract action, int x, int y );
	void provideFor( MapDisplay display );
	void stopProviding();
}