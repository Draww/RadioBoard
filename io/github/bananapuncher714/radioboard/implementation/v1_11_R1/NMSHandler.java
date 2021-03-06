package io.github.bananapuncher714.radioboard.implementation.v1_11_R1;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_11_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import io.github.bananapuncher714.radioboard.RadioBoard;
import io.github.bananapuncher714.radioboard.api.PacketHandler;
import io.netty.channel.Channel;
import net.minecraft.server.v1_11_R1.MapIcon;
import net.minecraft.server.v1_11_R1.PacketPlayOutMap;
import net.minecraft.server.v1_11_R1.PlayerConnection;

public class NMSHandler implements PacketHandler {
	public static final int PACKET_THRESHOLD_MS = 0;

	private static Field[] MAP_PACKET_FIELDS = new Field[ 9 ];

	static {
		try {
			MAP_PACKET_FIELDS[ 0 ] = PacketPlayOutMap.class.getDeclaredField( "a" );
			MAP_PACKET_FIELDS[ 1 ] = PacketPlayOutMap.class.getDeclaredField( "b" );
			MAP_PACKET_FIELDS[ 2 ] = PacketPlayOutMap.class.getDeclaredField( "c" );
			MAP_PACKET_FIELDS[ 3 ] = PacketPlayOutMap.class.getDeclaredField( "d" );
			MAP_PACKET_FIELDS[ 4 ] = PacketPlayOutMap.class.getDeclaredField( "e" );
			MAP_PACKET_FIELDS[ 5 ] = PacketPlayOutMap.class.getDeclaredField( "f" );
			MAP_PACKET_FIELDS[ 6 ] = PacketPlayOutMap.class.getDeclaredField( "g" );
			MAP_PACKET_FIELDS[ 7 ] = PacketPlayOutMap.class.getDeclaredField( "h" );
			MAP_PACKET_FIELDS[ 8 ] = PacketPlayOutMap.class.getDeclaredField( "i" );

			for ( Field field : MAP_PACKET_FIELDS ) {
				field.setAccessible( true );
			}
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}
	}
	
	private class PacketPlayOutRadioMap extends PacketPlayOutMap {
		protected final PacketPlayOutMap packet;
		
		protected PacketPlayOutRadioMap( PacketPlayOutMap packet ) {
			this.packet = packet;
		}
	}

	private final Map< UUID, PlayerConnection > playerConnections = new ConcurrentHashMap< UUID, PlayerConnection >();
	private final Map< UUID, Long > lastUpdated = new ConcurrentHashMap< UUID, Long >();
	private final boolean[] registeredMaps = new boolean[ Short.MAX_VALUE ];
	
	@Override
	public Object onPacketInterceptOut( Player viewer, Object packet ) {
		if ( packet instanceof PacketPlayOutRadioMap ) {
			return ( ( PacketPlayOutRadioMap ) packet ).packet;
		} else if ( packet instanceof PacketPlayOutMap ) {
			if ( packet.getClass().getPackage().getName().startsWith( "net.minecraft.server" ) ) {
				try {
					int id = MAP_PACKET_FIELDS[ 0 ].getInt( packet );
					if ( registeredMaps[ id ] ) {
						return null;
					}
				} catch ( IllegalArgumentException | IllegalAccessException e ) {
					e.printStackTrace();
				}
			}
		}
		return packet;
	}
	
	@Override
	public Object onPacketInterceptIn( Player viewer, Object packet ) {
		return packet;
	}
	
	/**
	 * Display the map
	 */
	@Override
	public void display( UUID[] viewers, int map, int width, int height, byte[] rgb, int videoWidth ) {
		// Get the height of the frame
		int vidHeight = rgb.length / videoWidth;
		int pixH = height << 7;
		int pixW = width << 7;
		int xOff = ( pixW - videoWidth ) >> 1;
		int yOff = ( pixH - vidHeight ) >> 1;
		// Center the picture in the middle of the screen
		display( viewers, map, width, height, rgb, videoWidth, xOff, yOff );
	}

	@Override
	public void display( UUID[] viewers, int map, int width, int height, byte[] rgb, int videoWidth, int xOff, int yOff ) {
		int vidHeight = rgb.length / videoWidth;
		int pixW = width << 7;
		int negXOff = xOff + videoWidth;
		int negYOff = yOff + vidHeight;
		int xDif = pixW - videoWidth;
		int top = yOff * pixW + xOff;
		int mapWidth = ( int ) Math.min( width, Math.ceil( negXOff / 128.0 ) );
		int mapHeight = ( int ) Math.min( height, Math.ceil( negYOff / 128.0 ) );

		PacketPlayOutMap[] packetArray = new PacketPlayOutMap[ ( int ) ( ( mapWidth - Math.max( 0, xOff >> 7 ) ) * ( mapHeight - Math.max( 0, yOff >> 7 ) ) ) ];
		int arrIndex = 0;

		for ( int x = Math.max( 0, xOff >> 7 ); x < mapWidth; x++ ) {
			int relX = x << 7;
			for ( int y = Math.max( 0, yOff >> 7 ); y < mapHeight; y++ ) {
				int relY = y << 7;

				int topX = Math.max( 0, xOff - relX );
				int topY = Math.max( 0, yOff - relY );
				int xDiff = Math.min( 128 - topX, negXOff - ( relX + topX ) );
				int yDiff = Math.min( 128 - topY, negYOff - ( relY + topY ) );

				byte[] mapData = new byte[ xDiff * yDiff ];
				for ( int ix = topX; ix < xDiff + topX; ix++ ) {
					int xPos = relX + ix;
					for ( int iy = topY; iy < yDiff + topY; iy++ ) {
						int yPos = relY + iy;
						int normalizedSlot = ( yPos * pixW + xPos ) - top;
						int index = normalizedSlot - ( int ) ( Math.floor( normalizedSlot / pixW ) * xDif );
						int val = ( iy - topY ) * xDiff + ix - topX;
						mapData[ val ] = rgb[ index ];
					}
				}

				int mapId = map + width * y + x;
				PacketPlayOutMap packet = new PacketPlayOutMap();

				try {
					MAP_PACKET_FIELDS[ 0 ].set( packet, mapId );
					MAP_PACKET_FIELDS[ 1 ].set( packet, ( byte ) 0 );
					MAP_PACKET_FIELDS[ 2 ].set( packet, false );
					MAP_PACKET_FIELDS[ 3 ].set( packet, new MapIcon[ 0 ] );
					MAP_PACKET_FIELDS[ 4 ].set( packet, topX );
					MAP_PACKET_FIELDS[ 5 ].set( packet, topY );
					MAP_PACKET_FIELDS[ 6 ].set( packet, xDiff );
					MAP_PACKET_FIELDS[ 7 ].set( packet, yDiff );
					MAP_PACKET_FIELDS[ 8 ].set( packet, mapData );
				} catch ( Exception exception ) {
					exception.printStackTrace();
				}

				packetArray[ arrIndex++ ] = new PacketPlayOutRadioMap( packet );
			}
		}

		if ( viewers == null ) {
			for ( UUID uuid : playerConnections.keySet() ) {
				Object val = lastUpdated.get( uuid );
				if ( val == null || System.currentTimeMillis() - ( long ) val > PACKET_THRESHOLD_MS ) {
					lastUpdated.put( uuid, System.currentTimeMillis() );
					PlayerConnection connection = playerConnections.get( uuid );
					Channel channel = RadioBoard.getInstance().getProtocol().getChannel( uuid, connection );
					if ( channel != null ) {
						for ( PacketPlayOutMap packet : packetArray ) {
//							connection.sendPacket( packet );
							RadioBoard.getInstance().getProtocol().sendPacket( channel, packet );
						}
					}
				}
			}
		} else {
			for ( UUID uuid : viewers ) {
				Object val = lastUpdated.get( uuid );
				if ( val == null || System.currentTimeMillis() - ( long ) val > PACKET_THRESHOLD_MS ) {
					lastUpdated.put( uuid, System.currentTimeMillis() );
					PlayerConnection connection = playerConnections.get( uuid );
					Channel channel = RadioBoard.getInstance().getProtocol().getChannel( uuid, connection );
					if ( channel != null ) {
						for ( PacketPlayOutMap packet : packetArray ) {
//							connection.sendPacket( packet );
							RadioBoard.getInstance().getProtocol().sendPacket( channel, packet );
						}
					}
				}
			}
		}
	}


	@Override
	public void registerPlayer( Player player ) {
		playerConnections.put( player.getUniqueId(), ( ( CraftPlayer ) player ).getHandle().playerConnection );
	}

	@Override
	public void unregisterPlayer( UUID mapId ) {
		playerConnections.remove( mapId );
		RadioBoard.getInstance().getProtocol().removeChannel( mapId );
	}

	@Override
	public boolean isMapRegistered( int mapId ) {
		return registeredMaps[ mapId ];
	}
	
	@Override
	public void registerMap( int mapId ) {
		registeredMaps[ mapId ] = true;
		MapView view = Bukkit.getMap( ( short ) mapId );
		if ( view != null ) {
			view.getRenderers().clear();
		}
	}

	@Override
	public void unregisterMap( int mapId ) {
		registeredMaps[ mapId ] = false;
	}
	
	@Override
	public ItemStack getMapItem( int id ) {
		ItemStack item = new ItemStack( Material.MAP );
		item.setDurability( ( short ) id );
		return item;
	}
}
